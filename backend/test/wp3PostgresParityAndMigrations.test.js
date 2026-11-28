import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  MemoryLedgerStore,
  PostgresLedgerStore,
  PLATFORM_TREASURY_UID
} from '../src/ledgerStore.js';
import { PostgresCallStore } from '../src/callStore.js';
import {
  loadMigrations,
  runMigrations,
  getMigrationStatus,
  MigrationError
} from '../src/migrator.js';
import { reconcileLedgerSnapshot } from '../src/reconcileLedger.js';
import { createCallServer } from '../src/server.js';

/**
 * Stateful PostgreSQL harness enforcing:
 * - schema_migrations checksums & DDL state
 * - users.agency_name
 * - ON DELETE RESTRICT across all 5 financial tables (SQLSTATE 23503)
 * - trg_ledger_entries_immutable append-only trigger (SQLSTATE 55000 on UPDATE/DELETE)
 * - Transaction BEGIN / COMMIT / ROLLBACK rollback snapshots
 * - All LedgerStore & CallStore SQL queries
 */
function createStatefulPgHarness() {
  const freshState = () => ({
    schemaMigrations: new Map(),
    triggers: new Set(),
    constraints: {
      wallet_accounts: 'RESTRICT',
      ledger_holds: 'RESTRICT',
      ledger_entries: 'RESTRICT',
      iap_transactions: 'RESTRICT',
      payout_requests: 'RESTRICT'
    },
    users: new Map(),
    wallets: new Map(),
    ledger: [],
    holds: new Map(),
    iap: new Map(),
    economyConfig: null,
    dailyDeviceClaims: new Map(),
    dailyActivities: new Map(),
    agencies: new Map(),
    agencyMemberships: new Map(),
    payouts: [],
    ageVerifications: new Map(),
    underageDeviceLocks: new Map(),
    moderationReports: new Map(),
    moderationSamples: new Map(),
    blocks: [],
    audits: [],
    callGovernanceConfig: null,
    callSessions: new Map(),
    callAlerts: new Map(),
    callCompletions: new Map()
  });

  let state = freshState();

  function cloneState(src) {
    return {
      schemaMigrations: new Map(src.schemaMigrations),
      triggers: new Set(src.triggers),
      constraints: { ...src.constraints },
      users: new Map(Array.from(src.users.entries(), ([k, v]) => [k, { ...v }])),
      wallets: new Map(Array.from(src.wallets.entries(), ([k, v]) => [k, { ...v }])),
      ledger: src.ledger.map((e) => ({ ...e, metadata: { ...(e.metadata || {}) } })),
      holds: new Map(Array.from(src.holds.entries(), ([k, v]) => [k, { ...v }])),
      iap: new Map(Array.from(src.iap.entries(), ([k, v]) => [k, { ...v }])),
      economyConfig: src.economyConfig ? { ...src.economyConfig } : null,
      dailyDeviceClaims: new Map(Array.from(src.dailyDeviceClaims.entries(), ([k, v]) => [k, { ...v }])),
      dailyActivities: new Map(src.dailyActivities),
      agencies: new Map(Array.from(src.agencies.entries(), ([k, v]) => [k, { ...v }])),
      agencyMemberships: new Map(Array.from(src.agencyMemberships.entries(), ([k, v]) => [k, { ...v }])),
      payouts: src.payouts.map((p) => ({ ...p })),
      ageVerifications: new Map(Array.from(src.ageVerifications.entries(), ([k, v]) => [k, { ...v }])),
      underageDeviceLocks: new Map(Array.from(src.underageDeviceLocks.entries(), ([k, v]) => [k, { ...v }])),
      moderationReports: new Map(Array.from(src.moderationReports.entries(), ([k, v]) => [k, { ...v }])),
      moderationSamples: new Map(Array.from(src.moderationSamples.entries(), ([k, v]) => [k, { ...v }])),
      blocks: src.blocks.map((b) => ({ ...b })),
      audits: src.audits.map((a) => ({ ...a, details: { ...(a.details || {}) } })),
      callGovernanceConfig: src.callGovernanceConfig ? JSON.parse(JSON.stringify(src.callGovernanceConfig)) : null,
      callSessions: new Map(Array.from(src.callSessions.entries(), ([k, v]) => [k, JSON.parse(JSON.stringify(v))])),
      callAlerts: new Map(Array.from(src.callAlerts.entries(), ([k, v]) => [k, JSON.parse(JSON.stringify(v))])),
      callCompletions: new Map(Array.from(src.callCompletions.entries(), ([k, v]) => [k, JSON.parse(JSON.stringify(v))]))
    };
  }

  let txSnapshot = null;

  async function executeQuery(sql, params = []) {
    const q = sql.replace(/\s+/g, ' ').trim();

    if (q === 'BEGIN') {
      txSnapshot = cloneState(state);
      return { rowCount: 0, rows: [] };
    }
    if (q === 'COMMIT') {
      txSnapshot = null;
      return { rowCount: 0, rows: [] };
    }
    if (q === 'ROLLBACK') {
      if (txSnapshot) {
        state = txSnapshot;
        txSnapshot = null;
      }
      return { rowCount: 0, rows: [] };
    }

    // DDL & Migration runner queries
    if (q.startsWith('CREATE TABLE IF NOT EXISTS schema_migrations')) {
      return { rowCount: 0, rows: [] };
    }
    if (q.includes('SELECT version, filename, checksum') && q.includes('FROM schema_migrations')) {
      const rows = Array.from(state.schemaMigrations.values()).sort((a, b) => a.version.localeCompare(b.version));
      return { rowCount: rows.length, rows };
    }
    if (q.startsWith('INSERT INTO schema_migrations')) {
      const [version, filename, checksum] = params;
      state.schemaMigrations.set(version, {
        version,
        filename,
        checksum,
        applied_at: new Date().toISOString()
      });
      return { rowCount: 1, rows: [] };
    }
    if (
      q.includes('CREATE TABLE IF NOT EXISTS users') ||
      q.includes('CREATE TABLE IF NOT EXISTS ledger_holds') ||
      q.includes('ALTER TABLE users ADD COLUMN IF NOT EXISTS agency_name') ||
      q.includes('ALTER TABLE users ADD COLUMN IF NOT EXISTS status') ||
      q.includes('CREATE TABLE IF NOT EXISTS underage_device_locks') ||
      q.includes('CREATE TABLE IF NOT EXISTS moderation_samples')
    ) {
      if (q.includes('CREATE TRIGGER trg_ledger_entries_immutable')) {
        state.triggers.add('trg_ledger_entries_immutable');
      }
      if (q.includes('ON DELETE RESTRICT')) {
        state.constraints = {
          wallet_accounts: 'RESTRICT',
          ledger_holds: 'RESTRICT',
          ledger_entries: 'RESTRICT',
          iap_transactions: 'RESTRICT',
          payout_requests: 'RESTRICT'
        };
      }
      return { rowCount: 0, rows: [] };
    }
    if (q.includes('INTENTIONAL_BAD_SQL_FOR_TEST')) {
      const err = new Error('syntax error at or near "INTENTIONAL_BAD_SQL_FOR_TEST"');
      err.code = '42601';
      throw err;
    }

    // Enforce append-only trigger on ledger_entries
    if (/^UPDATE\s+ledger_entries\b/i.test(q) || /^DELETE\s+FROM\s+ledger_entries\b/i.test(q)) {
      const op = /^UPDATE/i.test(q) ? 'UPDATE' : 'DELETE';
      const err = new Error(`ledger_entries is append-only: ${op} is forbidden`);
      err.code = '55000';
      throw err;
    }

    // Enforce ON DELETE RESTRICT when deleting from users
    if (/^DELETE\s+FROM\s+users\s+WHERE\s+uid\s*=\s*\$1/i.test(q)) {
      const uid = params[0];
      const hasFinancialRows =
        state.wallets.has(uid) ||
        state.ledger.some((e) => e.uid === uid) ||
        Array.from(state.holds.values()).some((h) => h.uid === uid) ||
        Array.from(state.iap.values()).some((t) => t.uid === uid) ||
        state.payouts.some((p) => p.uid === uid);
      if (hasFinancialRows) {
        const err = new Error(`update or delete on table "users" violates foreign key constraint on financial table`);
        err.code = '23503';
        throw err;
      }
      state.users.delete(uid);
      return { rowCount: 1, rows: [] };
    }

    // Users
    if (q.startsWith('INSERT INTO users (uid, display_name, email, referral_code, is_admin, admin_role)')) {
      const [uid, display_name, email, referral_code, is_admin, admin_role] = params;
      if (!state.users.has(uid)) {
        state.users.set(uid, {
          uid,
          display_name,
          email,
          photo_url: null,
          bio: '',
          country: '',
          country_code: '',
          gender: '',
          dob: null,
          age_years: 0,
          age_verified: false,
          age_verification_status: 'UNVERIFIED',
          status: 'ACTIVE',
          status_reason: null,
          underage_locked: false,
          underage_locked_reason: null,
          underage_locked_at: null,
          is_admin: Boolean(is_admin) || uid === PLATFORM_TREASURY_UID,
          admin_role: admin_role || (uid === PLATFORM_TREASURY_UID ? 'SUPER_ADMIN' : 'NONE'),
          is_agent: false,
          is_host: false,
          host_status: 'NONE',
          agent_commission_percent: 20,
          host_commission_percent: 50,
          agency_id: '',
          agency_name: '',
          referral_code,
          profile_visibility: 'PUBLIC',
          direct_message_permission: 'EVERYONE',
          accepted_terms_at: null,
          accepted_privacy_at: null,
          deleted_at: null
        });
      } else {
        const u = state.users.get(uid);
        if (!u.display_name && display_name) u.display_name = display_name;
        if (!u.email && email) u.email = email;
      }
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('INSERT INTO users (uid, display_name, country, referral_code)')) {
      const [uid, display_name, country, referral_code] = params;
      if (!state.users.has(uid)) {
        state.users.set(uid, {
          uid, display_name, email: '', country, referral_code,
          age_years: 0, age_verified: false, age_verification_status: 'UNVERIFIED',
          status: 'ACTIVE', status_reason: null, underage_locked: false, underage_locked_reason: null, underage_locked_at: null,
          is_admin: false, admin_role: 'NONE', is_agent: false, is_host: false, host_status: 'NONE',
          agent_commission_percent: 20, host_commission_percent: 50, agency_id: '', agency_name: '',
          profile_visibility: 'PUBLIC', direct_message_permission: 'EVERYONE'
        });
      }
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT * FROM users WHERE uid = $1')) {
      const u = state.users.get(params[0]);
      return { rowCount: u ? 1 : 0, rows: u ? [{ ...u }] : [] };
    }
    if (q.startsWith('SELECT 1 FROM underage_device_locks WHERE device_hash = $1')) {
      return { rowCount: state.underageDeviceLocks.has(params[0]) ? 1 : 0, rows: state.underageDeviceLocks.has(params[0]) ? [{ '?column?': 1 }] : [] };
    }
    if (q.startsWith('INSERT INTO underage_device_locks')) {
      const [device_hash, uid, dob_submitted] = params;
      if (!state.underageDeviceLocks.has(device_hash)) {
        state.underageDeviceLocks.set(device_hash, { device_hash, uid, dob_submitted, reason: 'UNDERAGE_SUBMISSION' });
      }
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('UPDATE users SET host_status = CASE WHEN is_host = FALSE')) {
      const [cleanAgency, uid] = params;
      const u = state.users.get(uid);
      if (!u.is_host && u.host_status !== 'APPROVED') u.host_status = 'PENDING';
      if (cleanAgency) u.agency_name = cleanAgency;
      return { rowCount: 1, rows: [{ ...u }] };
    }
    if (q.startsWith('UPDATE users SET is_agent = COALESCE')) {
      const [isAgent, isHost, isAdmin, agentP, hostP, agencyId, uid, agencyName, adminRole] = params;
      const u = state.users.get(uid);
      if (isAgent !== null) u.is_agent = isAgent;
      if (isHost !== null) {
        u.is_host = isHost;
        u.host_status = isHost ? 'APPROVED' : 'NONE';
      }
      if (isAdmin !== null) u.is_admin = isAdmin;
      if (adminRole !== null && adminRole !== undefined) u.admin_role = adminRole;
      if (agentP !== null) u.agent_commission_percent = agentP;
      if (hostP !== null) u.host_commission_percent = hostP;
      if (agencyId) u.agency_id = agencyId;
      if (agencyName !== null && agencyName !== undefined) u.agency_name = agencyName;
      return { rowCount: 1, rows: [{ ...u }] };
    }
    if (q.startsWith('UPDATE users SET underage_locked = TRUE')) {
      const [lockedAt, dob, ageYears, uid] = params;
      const u = state.users.get(uid);
      u.underage_locked = true; u.underage_locked_reason = 'UNDERAGE_SUBMISSION'; u.underage_locked_at = lockedAt;
      u.age_verified = false; u.age_verification_status = 'REJECTED_UNDERAGE'; u.dob = dob; u.age_years = ageYears;
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('UPDATE users SET dob = $1, age_years = $2')) {
      const [dob, ageYears, termsAt, privacyAt, verified, status, uid] = params;
      const u = state.users.get(uid);
      u.dob = dob; u.age_years = ageYears; u.accepted_terms_at = termsAt; u.accepted_privacy_at = privacyAt;
      u.age_verified = verified; u.age_verification_status = status;
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('UPDATE users SET age_verified=$1')) {
      const [verified, status, uid] = params;
      const u = state.users.get(uid);
      u.age_verified = verified; u.age_verification_status = status;
      return { rowCount: 1, rows: [{ ...u }] };
    }
    if (q.startsWith("UPDATE users SET deleted_at=$1, display_name='حساب محذوف'")) {
      const [deletedAt, uid] = params;
      const u = state.users.get(uid);
      if (u) {
        u.deleted_at = deletedAt;
        u.display_name = 'حساب محذوف';
        u.email = '';
        u.dob = null;
        u.bio = '';
        u.photo_url = null;
      }
      return { rowCount: u ? 1 : 0, rows: [] };
    }

    // Wallets
    if (q.startsWith('INSERT INTO wallet_accounts')) {
      const [uid] = params;
      if (!state.wallets.has(uid)) {
        state.wallets.set(uid, {
          uid, points_balance: 0, debt_coins: 0, held_coins: 0,
          host_earnings_coins: 0, sub_agents_earnings_coins: 0,
          reserved_payout_coins: 0, platform_revenue_coins: 0,
          total_accumulated_points: 0, total_points_spent: 0, last_daily_claim_ms: 0
        });
      }
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT * FROM wallet_accounts WHERE uid = $1')) {
      const w = state.wallets.get(params[0]);
      return { rowCount: w ? 1 : 0, rows: w ? [{ ...w }] : [] };
    }
    if (q === 'SELECT * FROM wallet_accounts') {
      return { rowCount: state.wallets.size, rows: Array.from(state.wallets.values(), (w) => ({ ...w })) };
    }
    if (q.includes('SET points_balance = points_balance + $1, debt_coins = debt_coins - $2')) {
      const [addPts, settleDebt, totalAcc, uid] = params;
      const w = state.wallets.get(uid);
      w.points_balance += addPts; w.debt_coins -= settleDebt; w.total_accumulated_points += totalAcc;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET points_balance = points_balance - $1, debt_coins = debt_coins + $2')) {
      const [deducted, addedDebt, uid] = params;
      const w = state.wallets.get(uid);
      w.points_balance -= deducted; w.debt_coins += addedDebt;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET points_balance = points_balance - $1, held_coins = held_coins + $1')) {
      const [amt, uid] = params;
      const w = state.wallets.get(uid);
      if (w.points_balance < amt) return { rowCount: 0, rows: [] };
      w.points_balance -= amt; w.held_coins += amt;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET held_coins = held_coins - $1, points_balance = points_balance + $2, total_points_spent = total_points_spent + $3')) {
      const [rem, rel, cap, uid] = params;
      const w = state.wallets.get(uid);
      if (w.held_coins < rem) return { rowCount: 0, rows: [] };
      w.held_coins -= rem; w.points_balance += rel; w.total_points_spent += cap;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET held_coins = GREATEST(0, held_coins - $1), points_balance = points_balance + $1')) {
      const [rem, uid] = params;
      const w = state.wallets.get(uid);
      w.held_coins = Math.max(0, w.held_coins - rem); w.points_balance += rem;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET points_balance = points_balance - $1, total_points_spent = total_points_spent + $1')) {
      const [amt, uid] = params;
      const w = state.wallets.get(uid);
      if (w.points_balance < amt) return { rowCount: 0, rows: [] };
      w.points_balance -= amt; w.total_points_spent += amt;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET points_balance = points_balance + $1, total_accumulated_points = total_accumulated_points + $1, last_daily_claim_ms = $2')) {
      const [amt, claimMs, uid] = params;
      const w = state.wallets.get(uid);
      w.points_balance += amt; w.total_accumulated_points += amt; w.last_daily_claim_ms = claimMs;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET points_balance = points_balance + $1, total_accumulated_points = total_accumulated_points + $1, updated_at = NOW()')) {
      const [amt, uid] = params;
      const w = state.wallets.get(uid);
      w.points_balance += amt; w.total_accumulated_points += amt;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET host_earnings_coins = host_earnings_coins + $1')) {
      const [amt, uid] = params;
      const w = state.wallets.get(uid);
      w.host_earnings_coins += amt;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET sub_agents_earnings_coins = sub_agents_earnings_coins + $1')) {
      const [amt, uid] = params;
      const w = state.wallets.get(uid);
      w.sub_agents_earnings_coins += amt;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET platform_revenue_coins = platform_revenue_coins + $1')) {
      const [amt, uid] = params;
      const w = state.wallets.get(uid);
      w.platform_revenue_coins += amt;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET sub_agents_earnings_coins = sub_agents_earnings_coins - $1, host_earnings_coins = host_earnings_coins - $2, reserved_payout_coins = reserved_payout_coins + $3')) {
      const [fromSub, fromHost, total, uid] = params;
      const w = state.wallets.get(uid);
      w.sub_agents_earnings_coins -= fromSub; w.host_earnings_coins -= fromHost; w.reserved_payout_coins += total;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET reserved_payout_coins = GREATEST(0, reserved_payout_coins - $1), sub_agents_earnings_coins = sub_agents_earnings_coins + $2, host_earnings_coins = host_earnings_coins + $3')) {
      const [amt, fromSub, fromHost, uid] = params;
      const w = state.wallets.get(uid);
      w.reserved_payout_coins = Math.max(0, w.reserved_payout_coins - amt); w.sub_agents_earnings_coins += fromSub; w.host_earnings_coins += fromHost;
      return { rowCount: 1, rows: [{ ...w }] };
    }
    if (q.includes('SET reserved_payout_coins = GREATEST(0, reserved_payout_coins - $1), updated_at = NOW()')) {
      const [amt, uid] = params;
      const w = state.wallets.get(uid);
      w.reserved_payout_coins = Math.max(0, w.reserved_payout_coins - amt);
      return { rowCount: 1, rows: [{ ...w }] };
    }

    // Economy Config
    if (q.startsWith('SELECT * FROM economy_config WHERE id = 1')) {
      return state.economyConfig ? { rowCount: 1, rows: [{ ...state.economyConfig }] } : { rowCount: 0, rows: [] };
    }
    if (q.startsWith('INSERT INTO economy_config')) {
      const [hostP, agentP, platP, updatedBy] = params;
      state.economyConfig = {
        id: 1, host_commission_percent: hostP, agent_commission_percent: agentP, platform_commission_percent: platP,
        min_platform_percent: 15, coins_per_usd: 250, min_payout_coins: 500, payout_hold_hours: 24,
        daily_free_reward_cap_coins: 25, updated_by: updatedBy
      };
      return { rowCount: 1, rows: [{ ...state.economyConfig }] };
    }

    // Daily device & activity
    if (q.startsWith('SELECT * FROM daily_device_claims WHERE device_hash = $1 AND day_key = $2')) {
      const r = state.dailyDeviceClaims.get(`${params[0]}:${params[1]}`);
      return { rowCount: r ? 1 : 0, rows: r ? [{ ...r }] : [] };
    }
    if (q.startsWith('SELECT free_coins_claimed FROM daily_device_claims WHERE device_hash = $1 AND day_key = $2')) {
      const r = state.dailyDeviceClaims.get(`${params[0]}:${params[1]}`);
      return { rowCount: r ? 1 : 0, rows: r ? [{ free_coins_claimed: r.free_coins_claimed }] : [] };
    }
    if (q.startsWith('INSERT INTO daily_device_claims')) {
      if (params.length === 4) {
        const [device_hash, day_key, last_daily_claim_ms, coins] = params;
        const k = `${device_hash}:${day_key}`;
        const prev = state.dailyDeviceClaims.get(k) || { device_hash, day_key, last_daily_claim_ms: 0, free_coins_claimed: 0 };
        prev.last_daily_claim_ms = last_daily_claim_ms;
        prev.free_coins_claimed += coins;
        state.dailyDeviceClaims.set(k, prev);
      } else {
        const [device_hash, day_key, coins] = params;
        const k = `${device_hash}:${day_key}`;
        const prev = state.dailyDeviceClaims.get(k) || { device_hash, day_key, last_daily_claim_ms: 0, free_coins_claimed: 0 };
        prev.free_coins_claimed += coins;
        state.dailyDeviceClaims.set(k, prev);
      }
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('INSERT INTO daily_activity_counters')) {
      const [uid, dayKey, type, cnt] = params;
      const k = `${uid}:${dayKey}:${type}`;
      const next = (state.dailyActivities.get(k) || 0) + cnt;
      state.dailyActivities.set(k, next);
      return { rowCount: 1, rows: [{ activity_count: next }] };
    }
    if (q.startsWith('SELECT activity_count FROM daily_activity_counters')) {
      const [uid, dayKey, type] = params;
      const cnt = state.dailyActivities.get(`${uid}:${dayKey}:${type}`);
      return { rowCount: cnt !== undefined ? 1 : 0, rows: cnt !== undefined ? [{ activity_count: cnt }] : [] };
    }

    // Free reward dayKey sum
    if (q.includes('SELECT COALESCE(SUM(delta), 0)::bigint AS total_free FROM ledger_entries')) {
      const [uid, dayKey] = params;
      const total = state.ledger
        .filter((e) => e.uid === uid && (e.entry_type === 'DAILY_FREE' || e.entry_type === 'MISSION_REWARD'))
        .filter((e) => (e.metadata?.dayKey || '') === dayKey)
        .reduce((acc, e) => acc + Number(e.delta), 0);
      return { rowCount: 1, rows: [{ total_free: total }] };
    }

    // IAP
    if (q.startsWith('SELECT * FROM iap_transactions WHERE purchase_token = $1')) {
      const r = state.iap.get(params[0]);
      return { rowCount: r ? 1 : 0, rows: r ? [{ ...r }] : [] };
    }
    if (q.startsWith('INSERT INTO iap_transactions')) {
      const [purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros, purchase_state, acknowledged, obfuscated_external_account_id, verification_source] = params;
      if (state.iap.has(purchase_token)) return { rowCount: 0, rows: [] };
      const row = {
        purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros,
        currency_code: 'USD', purchase_state, acknowledged, obfuscated_external_account_id,
        is_voided: false, voided_at: null, voided_reason: null, verification_source, verified_at: new Date().toISOString()
      };
      state.iap.set(purchase_token, row);
      return { rowCount: 1, rows: [{ ...row }] };
    }
    if (q.startsWith('UPDATE iap_transactions SET acknowledged = TRUE')) {
      const r = state.iap.get(params[0]);
      if (r) r.acknowledged = true;
      return { rowCount: r ? 1 : 0, rows: [] };
    }
    if (q.startsWith('UPDATE iap_transactions SET is_voided = TRUE')) {
      const [reason, token] = params;
      const r = state.iap.get(token);
      if (r) {
        r.is_voided = true; r.voided_at = new Date().toISOString(); r.voided_reason = reason;
      }
      return { rowCount: r ? 1 : 0, rows: [] };
    }

    // Holds
    if (q.startsWith('SELECT * FROM ledger_holds WHERE idempotency_key = $1')) {
      const h = Array.from(state.holds.values()).find((x) => x.idempotency_key === params[0]);
      return { rowCount: h ? 1 : 0, rows: h ? [{ ...h }] : [] };
    }
    if (q.startsWith('SELECT * FROM ledger_holds WHERE hold_id = $1')) {
      const h = state.holds.get(params[0]);
      return { rowCount: h ? 1 : 0, rows: h ? [{ ...h }] : [] };
    }
    if (q === 'SELECT * FROM ledger_holds') {
      return { rowCount: state.holds.size, rows: Array.from(state.holds.values(), (h) => ({ ...h })) };
    }
    if (q.startsWith('INSERT INTO ledger_holds')) {
      const [hold_id, uid, purpose, reference_id, amount_coins, idempotency_key] = params;
      const h = {
        hold_id, uid, purpose, reference_id, amount_coins, captured_coins: 0, released_coins: 0,
        status: 'ACTIVE', idempotency_key, created_at: new Date().toISOString(), settled_at: null
      };
      state.holds.set(hold_id, h);
      return { rowCount: 1, rows: [{ ...h }] };
    }
    if (q.startsWith('UPDATE ledger_holds SET captured_coins = captured_coins + $1')) {
      const [cap, rel, holdId] = params;
      const h = state.holds.get(holdId);
      h.captured_coins += cap; h.released_coins += rel; h.status = 'CAPTURED'; h.settled_at = new Date().toISOString();
      return { rowCount: 1, rows: [{ ...h }] };
    }
    if (q.startsWith('UPDATE ledger_holds SET released_coins = released_coins + $1')) {
      const [rem, holdId] = params;
      const h = state.holds.get(holdId);
      h.released_coins += rem; h.status = 'RELEASED'; h.settled_at = new Date().toISOString();
      return { rowCount: 1, rows: [{ ...h }] };
    }

    // Agencies
    if (q.startsWith('INSERT INTO agencies')) {
      const [id, owner_uid, name, country, commission_percent] = params;
      if (!state.agencies.has(id)) {
        state.agencies.set(id, { id, owner_uid, name, country, commission_percent, status: 'ACTIVE' });
      } else if (!q.includes('ON CONFLICT (id) DO NOTHING')) {
        const a = state.agencies.get(id);
        a.commission_percent = commission_percent;
        if (name) a.name = name;
      }
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('INSERT INTO agency_memberships')) {
      const [uid, agency_id, parent_agent_uid, commission_percent] = params;
      state.agencyMemberships.set(uid, { uid, agency_id, parent_agent_uid, commission_percent });
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT COUNT(*)::int AS cnt FROM agency_memberships WHERE parent_agent_uid=$1')) {
      const cnt = Array.from(state.agencyMemberships.values()).filter((m) => m.parent_agent_uid === params[0]).length;
      return { rowCount: 1, rows: [{ cnt }] };
    }
    if (q.startsWith('SELECT parent_agent_uid FROM agency_memberships WHERE uid = $1')) {
      const m = state.agencyMemberships.get(params[0]);
      return { rowCount: m ? 1 : 0, rows: m ? [{ parent_agent_uid: m.parent_agent_uid }] : [] };
    }

    // Age verifications
    if (q.startsWith('INSERT INTO age_verifications')) {
      const [uid, display_name, email, dob, age_years, status, reviewer_uid, rejection_reason, accepted_terms_at, accepted_privacy_at, reviewed_at] = params;
      const row = {
        uid, display_name, email, dob, age_years, status: status || 'PENDING_REVIEW',
        reviewer_uid: reviewer_uid ?? null, rejection_reason: rejection_reason ?? null,
        accepted_terms_at, accepted_privacy_at,
        submitted_at: new Date().toISOString(), reviewed_at: reviewed_at ?? null
      };
      state.ageVerifications.set(uid, row);
      return { rowCount: 1, rows: [{ ...row }] };
    }
    if (q.startsWith('UPDATE age_verifications SET status=$1')) {
      const [status, reviewer_uid, rejection_reason, uid] = params;
      const row = state.ageVerifications.get(uid);
      if (!row) return { rowCount: 0, rows: [] };
      row.status = status; row.reviewer_uid = reviewer_uid; row.rejection_reason = rejection_reason;
      row.reviewed_at = new Date().toISOString();
      return { rowCount: 1, rows: [{ ...row }] };
    }
    if (q.startsWith('SELECT * FROM age_verifications')) {
      const target = q.includes('WHERE status = $1') ? params[0] : null;
      const rows = Array.from(state.ageVerifications.values()).filter((r) => !target || r.status === target);
      return { rowCount: rows.length, rows };
    }
    if (q.startsWith('DELETE FROM age_verifications WHERE uid=$1')) {
      state.ageVerifications.delete(params[0]);
      return { rowCount: 1, rows: [] };
    }

    // Payouts
    if (q.startsWith('SELECT id FROM payout_requests WHERE uid = $1 AND status IN')) {
      const found = state.payouts.filter((p) => p.uid === params[0] && (p.status === 'PENDING_REVIEW' || p.status === 'APPROVED'));
      return { rowCount: found.length > 0 ? 1 : 0, rows: found.slice(0, 1).map((p) => ({ id: p.id })) };
    }
    if (q.startsWith('INSERT INTO payout_requests')) {
      const [id, uid, amount_coins, usd_equivalent_cents, payout_method, bank_account_holder, bank_name, iban_or_account, iban_masked, from_sub_agents_coins, from_host_coins, earliest_review_at] = params;
      const row = {
        id, uid, amount_coins, usd_equivalent_cents, payout_method, bank_account_holder, bank_name,
        iban_or_account, iban_masked, from_sub_agents_coins, from_host_coins,
        earliest_review_at, status: 'PENDING_REVIEW', created_at: new Date().toISOString(), reviewed_at: null
      };
      state.payouts.push(row);
      return { rowCount: 1, rows: [{ ...row }] };
    }
    if (q.startsWith('SELECT * FROM payout_requests WHERE id = $1')) {
      const r = state.payouts.find((p) => p.id === params[0]);
      return { rowCount: r ? 1 : 0, rows: r ? [{ ...r }] : [] };
    }
    if (q === 'SELECT * FROM payout_requests') {
      return { rowCount: state.payouts.length, rows: state.payouts.map((p) => ({ ...p })) };
    }
    if (q.startsWith('SELECT p.*, u.display_name FROM payout_requests')) {
      const rows = (q.includes('WHERE p.uid = $1') ? state.payouts.filter((p) => p.uid === params[0]) : state.payouts)
        .map((p) => ({ ...p, display_name: state.users.get(p.uid)?.display_name || '' }));
      return { rowCount: rows.length, rows };
    }
    if (q.startsWith('UPDATE payout_requests SET status=$1')) {
      const [status, reviewer_uid, reviewer_note, payment_reference, id] = params;
      const p = state.payouts.find((x) => x.id === id);
      p.status = status; p.reviewer_uid = reviewer_uid; p.reviewer_note = reviewer_note;
      if (payment_reference !== null) p.payment_reference = payment_reference;
      p.reviewed_at = new Date().toISOString();
      return { rowCount: 1, rows: [{ ...p }] };
    }

    // Moderation & Blocks
    if (q.startsWith('INSERT INTO moderation_reports')) {
      const [id, report_type, target_user_id, stream_id, reporter_uid, reason, details] = params;
      const row = {
        id, report_type, target_user_id, stream_id, reporter_uid, reason, details,
        status: 'PENDING_REVIEW', action_taken: null, reviewer_uid: null, created_at: new Date().toISOString(), reviewed_at: null
      };
      state.moderationReports.set(id, row);
      return { rowCount: 1, rows: [{ ...row }] };
    }
    if (q.startsWith('UPDATE moderation_reports SET status=$1')) {
      const [status, action_taken, reviewer_uid, id] = params;
      const row = state.moderationReports.get(id);
      if (!row) return { rowCount: 0, rows: [] };
      row.status = status; row.action_taken = action_taken; row.reviewer_uid = reviewer_uid;
      row.reviewed_at = new Date().toISOString();
      return { rowCount: 1, rows: [{ ...row }] };
    }
    if (q.startsWith('INSERT INTO user_blocks')) {
      const [blocker_uid, blocked_uid] = params;
      if (!state.blocks.some((b) => b.blocker_uid === blocker_uid && b.blocked_uid === blocked_uid)) {
        state.blocks.push({ blocker_uid, blocked_uid });
      }
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('DELETE FROM user_blocks WHERE blocker_uid=$1 AND blocked_uid=$2')) {
      state.blocks = state.blocks.filter((b) => !(b.blocker_uid === params[0] && b.blocked_uid === params[1]));
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('DELETE FROM user_blocks WHERE blocker_uid=$1 OR blocked_uid=$1')) {
      state.blocks = state.blocks.filter((b) => b.blocker_uid !== params[0] && b.blocked_uid !== params[0]);
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT blocked_uid FROM user_blocks WHERE blocker_uid=$1')) {
      const rows = state.blocks.filter((b) => b.blocker_uid === params[0]).map((b) => ({ blocked_uid: b.blocked_uid }));
      return { rowCount: rows.length, rows };
    }

    // Ledger entries
    if (q.startsWith('SELECT * FROM ledger_entries WHERE idempotency_key = $1')) {
      const found = state.ledger.filter((e) => e.idempotency_key === params[0]);
      return { rowCount: found.length, rows: found };
    }
    if (q.startsWith('SELECT 1 FROM ledger_entries WHERE idempotency_key IN ($1, $2)')) {
      const found = state.ledger.filter((e) => e.idempotency_key === params[0] || e.idempotency_key === params[1]);
      return { rowCount: found.length, rows: found };
    }
    if (q.startsWith('SELECT * FROM ledger_entries WHERE uid = $1 ORDER BY created_at DESC LIMIT $2')) {
      const [uid, limit] = params;
      const rows = state.ledger.filter((e) => e.uid === uid).slice().reverse().slice(0, limit);
      return { rowCount: rows.length, rows };
    }
    if (q === 'SELECT * FROM ledger_entries') {
      return { rowCount: state.ledger.length, rows: [...state.ledger] };
    }
    if (q.includes("SELECT DISTINCT COALESCE(metadata->>'recipientUid', reference_id) AS recipient_uid FROM ledger_entries")) {
      const uid = params[0];
      const recips = Array.from(new Set(
        state.ledger.filter((e) => e.uid === uid && e.entry_type === 'GIFT_SENT').map((e) => e.metadata?.recipientUid || e.reference_id).filter(Boolean)
      ));
      return { rowCount: recips.length, rows: recips.map((recipient_uid) => ({ recipient_uid })) };
    }
    if (q.startsWith('INSERT INTO ledger_entries')) {
      let entry_uuid, uid, entry_type, bucket = 'POINTS', delta, balance_after, idempotency_key, reference_id = null, description, metadata = '{}';
      if (q.includes("VALUES ($1,$2,'PURCHASE_DEBT_SETTLEMENT','DEBT'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'PURCHASE_DEBT_SETTLEMENT'; bucket = 'DEBT';
      } else if (q.includes("VALUES ($1,$2,'PURCHASE','POINTS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'PURCHASE'; bucket = 'POINTS';
      } else if (q.includes("VALUES ($1,$2,'HOLD_RESERVE','POINTS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'HOLD_RESERVE';
      } else if (q.includes("VALUES ($1,$2,'HOLD_RELEASE','POINTS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'HOLD_RELEASE';
      } else if (q.includes("VALUES ($1,$2,'DAILY_FREE','POINTS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, description, metadata] = params;
        entry_type = 'DAILY_FREE';
      } else if (q.includes("VALUES ($1,$2,'MISSION_REWARD','POINTS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'MISSION_REWARD';
      } else if (q.includes("VALUES ($1,$2,'GIFT_SENT','POINTS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'GIFT_SENT';
      } else if (q.includes("VALUES ($1,$2,'GIFT_RECEIVE','HOST_EARNINGS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'GIFT_RECEIVE'; bucket = 'HOST_EARNINGS';
      } else if (q.includes("VALUES ($1,$2,'AGENT_COMMISSION','AGENCY_EARNINGS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'AGENT_COMMISSION'; bucket = 'AGENCY_EARNINGS';
      } else if (q.includes("VALUES ($1,$2,'PLATFORM_REVENUE','PLATFORM_REVENUE'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'PLATFORM_REVENUE'; bucket = 'PLATFORM_REVENUE';
      } else if (q.includes("VALUES ($1,$2,'REFUND_CLAWBACK','POINTS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'REFUND_CLAWBACK';
      } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL_RESERVE','RESERVED_PAYOUT'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        entry_type = 'WITHDRAWAL_RESERVE'; bucket = 'RESERVED_PAYOUT';
      } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL_REFUND','AGENCY_EARNINGS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description] = params;
        entry_type = 'WITHDRAWAL_REFUND'; bucket = 'AGENCY_EARNINGS';
      } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL_REFUND','HOST_EARNINGS'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description] = params;
        entry_type = 'WITHDRAWAL_REFUND'; bucket = 'HOST_EARNINGS';
      } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL','RESERVED_PAYOUT'")) {
        [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description] = params;
        entry_type = 'WITHDRAWAL'; bucket = 'RESERVED_PAYOUT';
      } else if (q.includes("VALUES ($1,$2,$3,'HELD_POINTS'")) {
        [entry_uuid, uid, entry_type, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
        bucket = 'HELD_POINTS';
      } else {
        [entry_uuid, uid, entry_type, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
      }
      if (!state.ledger.some((e) => e.idempotency_key === idempotency_key)) {
        state.ledger.push({
          id: state.ledger.length + 1,
          entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description,
          metadata: typeof metadata === 'string' ? JSON.parse(metadata) : (metadata || {}),
          created_at: new Date().toISOString()
        });
      }
      return { rowCount: 1, rows: [] };
    }

    // Audit logs
    if (q.startsWith('INSERT INTO audit_logs')) {
      let actor_uid, action, target_id, details = {};
      if (q.includes("VALUES ($1,'UPDATE_ECONOMY_CONFIG','economy_config',$2)")) {
        [actor_uid, details] = params;
        action = 'UPDATE_ECONOMY_CONFIG'; target_id = 'economy_config';
      } else if (q.includes("VALUES ($1, 'IAP_PURCHASE_CREDITED', $2, $3)")) {
        [actor_uid, target_id, details] = params; action = 'IAP_PURCHASE_CREDITED';
      } else if (q.includes("VALUES ($1, 'IAP_PURCHASE_VOIDED', $2, $3)")) {
        [actor_uid, target_id, details] = params; action = 'IAP_PURCHASE_VOIDED';
      } else if (q.includes("VALUES ('system', 'REFUND_CLAWBACK_DEBT', $1, $2)")) {
        actor_uid = 'system'; action = 'REFUND_CLAWBACK_DEBT'; [target_id, details] = params;
      } else if (q.includes("VALUES ('system', 'HOST_EARNINGS_REVIEW_FLAGGED', $1, $2)")) {
        actor_uid = 'system'; action = 'HOST_EARNINGS_REVIEW_FLAGGED'; [target_id, details] = params;
      } else if (q.includes("VALUES ($1,'HOST_APPLICATION_SUBMITTED',$2,$3)")) {
        [actor_uid, target_id, details] = params; action = 'HOST_APPLICATION_SUBMITTED';
      } else if (q.includes("VALUES ($1,'REVIEW_AGE_VERIFICATION',$2,$3)")) {
        [actor_uid, target_id, details] = params; action = 'REVIEW_AGE_VERIFICATION';
      } else if (q.includes("VALUES ($1,'CREATE_PAYOUT_REQUEST',$2,$3)")) {
        [actor_uid, target_id, details] = params; action = 'CREATE_PAYOUT_REQUEST';
      } else if (q.includes("VALUES ($1,$2,$3,$4)")) {
        [actor_uid, action, target_id, details] = params;
      } else if (q.includes("VALUES ($1,'ADMIN_UPDATE_USER_ROLES',$2,$3)")) {
        [actor_uid, target_id, details] = params; action = 'ADMIN_UPDATE_USER_ROLES';
      } else if (q.includes("VALUES ($1,'RESOLVE_MODERATION_REPORT',$2,$3)")) {
        [actor_uid, target_id, details] = params; action = 'RESOLVE_MODERATION_REPORT';
      } else if (q.includes("VALUES ($1,'ACCOUNT_DELETED',$1,'{}')")) {
        [actor_uid] = params; target_id = actor_uid; action = 'ACCOUNT_DELETED'; details = '{}';
      }
      state.audits.push({
        id: state.audits.length + 1,
        actor_uid,
        action,
        target_id,
        details: typeof details === 'string' ? JSON.parse(details) : (details || {}),
        created_at: new Date().toISOString()
      });
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT * FROM audit_logs')) {
      let rows = [...state.audits];
      const matches = [...q.matchAll(/(actor_uid|action|target_id) = \$(\d+)/g)];
      for (const m of matches) {
        const col = m[1];
        const val = params[Number(m[2]) - 1];
        rows = rows.filter((r) => r[col] === val);
      }
      const limit = Number(params[params.length - 1] || 100);
      rows = rows.slice().reverse().slice(0, limit);
      return { rowCount: rows.length, rows };
    }

    // CallStore tables (C2)
    if (q.startsWith('SELECT config_json FROM call_governance_config WHERE id = 1')) {
      return state.callGovernanceConfig
        ? { rowCount: 1, rows: [{ config_json: JSON.parse(JSON.stringify(state.callGovernanceConfig)) }] }
        : { rowCount: 0, rows: [] };
    }
    if (q.startsWith('INSERT INTO call_governance_config')) {
      state.callGovernanceConfig = JSON.parse(params[0]);
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT payload_json FROM call_sessions')) {
      const rows = Array.from(state.callSessions.values()).map((c) => ({ payload_json: JSON.parse(JSON.stringify(c)) }));
      return { rowCount: rows.length, rows };
    }
    if (q.startsWith('INSERT INTO call_sessions')) {
      const [call_id, _room, _caller, _callee, _type, _status, payloadStr] = params;
      state.callSessions.set(call_id, JSON.parse(payloadStr));
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT payload_json FROM call_alerts')) {
      const rows = Array.from(state.callAlerts.values()).map((a) => ({ payload_json: JSON.parse(JSON.stringify(a)) }));
      return { rowCount: rows.length, rows };
    }
    if (q.startsWith('INSERT INTO call_alerts')) {
      const [alert_id, _dedup, payloadStr] = params;
      state.callAlerts.set(alert_id, JSON.parse(payloadStr));
      return { rowCount: 1, rows: [] };
    }
    if (q.startsWith('SELECT payload_json FROM call_completions')) {
      const rows = Array.from(state.callCompletions.values())
        .sort((a, b) => (b.loggedAt || 0) - (a.loggedAt || 0))
        .map((l) => ({ payload_json: JSON.parse(JSON.stringify(l)) }));
      return { rowCount: rows.length, rows };
    }
    if (q.startsWith('INSERT INTO call_completions')) {
      const [call_id, _status, _type, _caller, _callee, payloadStr] = params;
      if (!state.callCompletions.has(call_id)) {
        state.callCompletions.set(call_id, JSON.parse(payloadStr));
      }
      return { rowCount: 1, rows: [] };
    }

    throw new Error(`Unhandled SQL in WP-3 PG harness: ${q}`);
  }

  const client = {
    query: (sql, params) => executeQuery(sql, params),
    release: () => {}
  };

  return {
    pool: {
      query: (sql, params) => executeQuery(sql, params),
      connect: async () => client,
      end: async () => {}
    },
    getState: () => state
  };
}

/**
 * Shared contract test suite executed identically against BOTH MemoryLedgerStore and PostgresLedgerStore.
 */
async function runSharedLedgerContractTests(createStoreForRun) {
  let clockMs = 1_760_000_000_000;
  const clock = () => clockMs;
  const store = await createStoreForRun(clock);

  // 1. User creation + +18 approval + host application sets users.agency_name + writes audit log
  await store.ensureUser({ uid: 'host_c1', displayName: 'مضيف العقد', email: 'host@ayer.test' });
  await store.submitAgeVerification({
    uid: 'host_c1',
    displayName: 'مضيف العقد',
    email: 'host@ayer.test',
    dob: '1994-05-10',
    acceptedTermsAt: clockMs,
    acceptedPrivacyAt: clockMs
  });
  const ageReviewedHost = await store.reviewAgeVerification({
    reviewerUid: 'admin_c1',
    targetUid: 'host_c1',
    decision: 'APPROVED'
  });
  assert.equal(ageReviewedHost.user.ageVerified, true);
  const hostAgeAudits = await store.getAuditLogs({ action: 'REVIEW_AGE_VERIFICATION', targetId: 'host_c1' });
  assert.equal(hostAgeAudits.length, 1);
  assert.equal(hostAgeAudits[0].details.decision, 'APPROVED');

  const hostApplied = await store.applyForHost({ uid: 'host_c1', agencyName: 'وكالة النخبة' });
  assert.equal(hostApplied.user.hostStatus, 'PENDING');
  assert.equal(hostApplied.user.agencyName, 'وكالة النخبة');
  const hostAudits = await store.getAuditLogs({ action: 'HOST_APPLICATION_SUBMITTED', targetId: 'host_c1' });
  assert.equal(hostAudits.length, 1);
  assert.equal(hostAudits[0].details.agencyName, 'وكالة النخبة');

  // 2. Admin sets roles, commissions, and agencyName -> reflected on user & audit log
  const promotedAgent = await store.adminSetUserRolesAndCommission({
    reviewerUid: 'admin_c1',
    targetUid: 'agent_c1',
    isAgent: true,
    agentCommissionPercent: 25,
    agencyName: 'وكالة الشرق'
  });
  assert.equal(promotedAgent.isAgent, true);
  assert.equal(promotedAgent.agencyName, 'وكالة الشرق');
  assert.ok(promotedAgent.agencyId.startsWith('ag_'));

  const promotedHost = await store.adminSetUserRolesAndCommission({
    reviewerUid: 'admin_c1',
    targetUid: 'host_c1',
    isHost: true,
    hostCommissionPercent: 55
  });
  assert.equal(promotedHost.isHost, true);
  assert.equal(promotedHost.hostStatus, 'APPROVED');
  assert.equal(promotedHost.agencyName, 'وكالة النخبة');

  await store.recruitSubAgent({
    agentUid: 'agent_c1',
    subAgentUid: 'host_c1',
    subAgentName: 'مضيف العقد',
    country: 'SA'
  });

  // 3. Age verification submit + review writes REVIEW_AGE_VERIFICATION audit log
  await store.submitAgeVerification({
    uid: 'host_c1',
    displayName: 'مضيف العقد',
    email: 'host@ayer.test',
    dob: '1994-05-10',
    ageYears: 31,
    acceptedTermsAt: clockMs,
    acceptedPrivacyAt: clockMs
  });
  const ageReviewed = await store.reviewAgeVerification({
    reviewerUid: 'admin_c1',
    targetUid: 'host_c1',
    decision: 'APPROVED'
  });
  assert.equal(ageReviewed.user.ageVerified, true);
  const ageAudits = await store.getAuditLogs({ action: 'REVIEW_AGE_VERIFICATION', targetId: 'host_c1' });
  assert.ok(ageAudits.length >= 1);
  assert.equal(ageAudits[0].details.decision, 'APPROVED');

  // 4. Daily bonus + verified activity + mission reward + synthetic dayKey rollover
  const bonusDay1 = await store.claimDailyBonus({ uid: 'buyer_c1', deviceId: 'dev_c1' });
  assert.equal(bonusDay1.creditedCoins, 10);
  await store.recordServerActivity({ uid: 'buyer_c1', activityType: 'STREAM_WATCHED', count: 1 });
  const missionDay1 = await store.claimMissionReward({ uid: 'buyer_c1', missionId: 'mission_stream', deviceId: 'dev_c1' });
  assert.equal(missionDay1.creditedCoins, 10);
  await assert.rejects(
    () => store.claimMissionReward({ uid: 'buyer_c1', missionId: 'mission_stream', deviceId: 'dev_c1' }),
    (err) => err.code === 'MISSION_ALREADY_CLAIMED'
  );

  // Advance clock by 24h + 1ms -> new dayKey allows daily bonus again without DB NOW() drift
  clockMs += 24 * 60 * 60 * 1000 + 1;
  const bonusDay2 = await store.claimDailyBonus({ uid: 'buyer_c1', deviceId: 'dev_c1' });
  assert.equal(bonusDay2.creditedCoins, 10);
  assert.equal(bonusDay2.wallet.pointsBalance, 30);

  // 5. Purchase -> Hold -> Capture -> Gift -> Payout -> Reject -> Void Clawback -> Reconcile
  const credited = await store.creditPlayPurchase({
    uid: 'buyer_c1',
    packageId: 'pkg_1200',
    productId: 'ayer_coins_1200',
    coinsCredited: 1400,
    priceUsd: 9.99,
    purchaseToken: 'pt_contract_1200'
  });
  assert.equal(credited.wallet.pointsBalance, 1430);

  // Verify buyer_c1 as +18 before hold/gift operations (WP-4 universal gate)
  await store.submitAgeVerification({
    uid: 'buyer_c1',
    displayName: 'مشتري العقد',
    email: 'buyer@ayer.test',
    dob: '1993-02-02',
    acceptedTermsAt: clockMs,
    acceptedPrivacyAt: clockMs
  });
  await store.reviewAgeVerification({ reviewerUid: 'admin_c1', targetUid: 'buyer_c1', decision: 'APPROVED' });

  const hold = await store.holdCoins({ uid: 'buyer_c1', amount: 30, purpose: 'MATCH_HOLD', idempotencyKey: 'hold_c1' });
  assert.equal(hold.wallet.heldCoins, 30);
  const cap = await store.captureHold({ holdId: hold.hold.holdId, captureAmount: 20, idempotencyKey: 'cap_c1' });
  assert.equal(cap.wallet.pointsBalance, 1410);
  assert.equal(cap.wallet.heldCoins, 0);

  // Send 2x gift_rocket (600 each = 1200 coins): host 55% (330*2=660), agent 25% (150*2=300), platform 20% (120*2=240)
  await store.sendGiftAtomic({ senderUid: 'buyer_c1', recipientUid: 'host_c1', giftId: 'gift_rocket', idempotencyKey: 'gift_c1_a' });
  await store.sendGiftAtomic({ senderUid: 'buyer_c1', recipientUid: 'host_c1', giftId: 'gift_rocket', idempotencyKey: 'gift_c1_b' });

  const payout = await store.createPayoutRequest({
    uid: 'host_c1',
    amountCoins: 500,
    bankAccountHolder: 'مضيف العقد',
    bankName: 'الراجحي',
    ibanOrAccount: 'SA4420000001234567891234'
  });
  assert.equal(payout.request.usdEquivalentCents, 200);
  assert.equal(payout.request.ibanMasked, '********1234');
  assert.equal(payout.request.ibanOrAccount, '********1234');

  const rejectedPayout = await store.reviewPayoutRequest({
    reviewerUid: 'admin_c1',
    requestId: payout.request.id,
    decision: 'REJECTED',
    note: 'تحقق من الاسم'
  });
  assert.equal(rejectedPayout.wallet.hostEarningsCoins, 660);

  // 6. Moderation report + resolve writes RESOLVE_MODERATION_REPORT audit log
  const rep = await store.createModerationReport({
    reportType: 'USER_PROFILE',
    targetUserId: 'spam_c1',
    reporterUid: 'buyer_c1',
    reason: 'SPAM'
  });
  const resolvedRep = await store.resolveModerationReport({
    reviewerUid: 'admin_c1',
    reportId: rep.id,
    status: 'RESOLVED',
    actionTaken: 'WARNING'
  });
  assert.equal(resolvedRep.status, 'RESOLVED');
  const repAudits = await store.getAuditLogs({ action: 'RESOLVE_MODERATION_REPORT', targetId: rep.id });
  assert.equal(repAudits.length, 1);
  assert.equal(repAudits[0].details.targetUserId, 'spam_c1');

  // 7. Block user + account deletion anonymizes PII, clears blocks & age verification, logs ACCOUNT_DELETED, preserves financial ledger
  await store.blockUser({ blockerUid: 'buyer_c1', blockedUid: 'spam_c1' });
  assert.deepEqual(await store.getBlockedUsers('buyer_c1'), ['spam_c1']);
  const deleted = await store.deleteUserAccount({ uid: 'buyer_c1' });
  assert.equal(deleted.deleted, true);
  assert.deepEqual(await store.getBlockedUsers('buyer_c1'), []);
  const postDeleteSummary = await store.getWalletSummary('buyer_c1', 20);
  assert.equal(postDeleteSummary.user.displayName, 'حساب محذوف');
  assert.equal(postDeleteSummary.wallet.pointsBalance, 210);
  assert.ok(postDeleteSummary.transactions.length >= 4);
  const delAudits = await store.getAuditLogs({ action: 'ACCOUNT_DELETED', targetId: 'buyer_c1' });
  assert.equal(delAudits.length, 1);

  // 8. Ledger conservation 100% intact
  const recon = reconcileLedgerSnapshot(await store.exportReconciliationSnapshot());
  assert.equal(recon.ok, true, recon.discrepancies.join('\n'));
}

test('WP-3 C6: Versioned SQL migrations, schema.sql invariants, append-only ledger trigger, and ON DELETE RESTRICT', async () => {
  // Verify migration files & schema.sql define ON DELETE RESTRICT and append-only trigger
  const migrations = loadMigrations();
  assert.ok(migrations.length >= 4);
  assert.equal(migrations[0].version, '001');
  assert.equal(migrations[1].version, '002');
  assert.equal(migrations[2].version, '003');
  assert.equal(migrations[3].version, '004');

  const schemaSql = fs.readFileSync(path.resolve('backend/sql/schema.sql'), 'utf8');
  assert.ok(schemaSql.includes('agency_name TEXT NOT NULL DEFAULT'));
  assert.ok(schemaSql.includes('trg_ledger_entries_immutable'));
  assert.ok(schemaSql.includes('BEFORE UPDATE OR DELETE ON ledger_entries'));
  assert.ok(schemaSql.includes('CREATE TABLE IF NOT EXISTS call_completions'));
  for (const finTable of ['wallet_accounts', 'ledger_holds', 'ledger_entries', 'iap_transactions', 'payout_requests']) {
    const tableBlockMatch = schemaSql.match(new RegExp(`CREATE TABLE IF NOT EXISTS ${finTable}[\\s\\S]*?\\);`));
    assert.ok(tableBlockMatch, `Missing ${finTable} in schema.sql`);
    assert.ok(tableBlockMatch[0].includes('REFERENCES users(uid) ON DELETE RESTRICT'), `${finTable} must use ON DELETE RESTRICT`);
    assert.ok(!tableBlockMatch[0].includes('ON DELETE CASCADE'), `${finTable} must NOT use ON DELETE CASCADE`);
  }

  // Run migrations cleanly on fresh DB & verify idempotency
  const harness = createStatefulPgHarness();
  const run1 = await runMigrations(harness.pool);
  assert.equal(run1.ok, true);
  assert.equal(run1.applied.length, migrations.length);
  assert.equal(run1.skipped.length, 0);

  const run2 = await runMigrations(harness.pool);
  assert.equal(run2.applied.length, 0);
  assert.equal(run2.skipped.length, migrations.length);

  const status = await getMigrationStatus(harness.pool);
  assert.equal(status.applied.length, migrations.length);
  assert.equal(status.pending.length, 0);

  // Tampering an already-applied migration fails closed with MIGRATION_CHECKSUM_MISMATCH
  const tmpMigDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ayer-mig-'));
  try {
    for (const m of migrations) {
      fs.writeFileSync(path.join(tmpMigDir, m.filename), m.sql, 'utf8');
    }
    fs.writeFileSync(path.join(tmpMigDir, migrations[0].filename), `${migrations[0].sql}\n-- tampered`, 'utf8');
    await assert.rejects(
      () => runMigrations(harness.pool, { migrationsDir: tmpMigDir }),
      (err) => err instanceof MigrationError && err.code === 'MIGRATION_CHECKSUM_MISMATCH'
    );

    // Failed pending migration rolls back atomically without recording version 005
    fs.writeFileSync(path.join(tmpMigDir, migrations[0].filename), migrations[0].sql, 'utf8');
    fs.writeFileSync(path.join(tmpMigDir, '005_bad_migration.sql'), 'INTENTIONAL_BAD_SQL_FOR_TEST;', 'utf8');
    await assert.rejects(() => runMigrations(harness.pool, { migrationsDir: tmpMigDir }));
    assert.equal(harness.getState().schemaMigrations.has('005'), false);
  } finally {
    fs.rmSync(tmpMigDir, { recursive: true, force: true });
  }

  // Verify database-level triggers & ON DELETE RESTRICT protections on ledger_entries & users
  const pgStore = new PostgresLedgerStore({ pool: harness.pool, env: { BANK_ENCRYPTION_KEY: 'wp3-c6-key' } });
  await pgStore.creditPlayPurchase({
    uid: 'fin_protected_user',
    packageId: 'pkg_100',
    productId: 'ayer_coins_100',
    coinsCredited: 110,
    priceUsd: 0.99,
    purchaseToken: 'pt_restrict_1'
  });

  await assert.rejects(
    () => harness.pool.query(`UPDATE ledger_entries SET delta = 999999 WHERE uid = $1`, ['fin_protected_user']),
    (err) => err.code === '55000'
  );
  await assert.rejects(
    () => harness.pool.query(`DELETE FROM ledger_entries WHERE uid = $1`, ['fin_protected_user']),
    (err) => err.code === '55000'
  );
  await assert.rejects(
    () => harness.pool.query(`DELETE FROM users WHERE uid = $1`, ['fin_protected_user']),
    (err) => err.code === '23503'
  );
});

test('WP-3 C1: Shared contract test suite passes identically on MemoryLedgerStore and PostgresLedgerStore', async () => {
  await runSharedLedgerContractTests(async (clock) => {
    const store = new MemoryLedgerStore({
      nowProvider: clock,
      env: { BANK_ENCRYPTION_KEY: 'wp3-shared-contract-key' },
      payoutHoldMs: 24 * 60 * 60 * 1000
    });
    await store.init();
    return store;
  });

  await runSharedLedgerContractTests(async (clock) => {
    const harness = createStatefulPgHarness();
    const store = new PostgresLedgerStore({
      pool: harness.pool,
      nowProvider: clock,
      env: { BANK_ENCRYPTION_KEY: 'wp3-shared-contract-key' },
      payoutHoldMs: 24 * 60 * 60 * 1000
    });
    await store.init();
    return store;
  });
});

test('WP-3 C2: Call governance config, sessions, alerts, and completions persist in PostgreSQL across server restarts', async () => {
  const harness = createStatefulPgHarness();
  const sharedEnv = {
    LIVEKIT_URL: 'wss://test.livekit.cloud',
    LIVEKIT_API_KEY: 'lk_wp3_key',
    LIVEKIT_API_SECRET: 'lk_wp3_secret_should_never_leak',
    ADMIN_UIDS: 'admin_c2',
    ALLOW_DEV_AUTH: 'true'
  };

  let createdCallId = '';
  const tokenVerifier = async (token) => {
    if (!token.startsWith('u:')) return null;
    const uid = token.slice(2);
    return { uid, name: uid, email: `${uid}@ayer.test`, isAdmin: uid === 'admin_c2' };
  };

  // Boot Server 1 backed by PG Pool (no DATA_FILE)
  const srv1 = createCallServer({ env: sharedEnv, pool: harness.pool, tokenVerifier });
  await new Promise((resolve) => srv1.server.listen(0, '127.0.0.1', resolve));
  const baseUrl1 = `http://127.0.0.1:${srv1.server.address().port}`;

  try {
    const healthRes = await fetch(`${baseUrl1}/health`);
    assert.equal(healthRes.status, 200);
    const health = await healthRes.json();
    assert.equal(health.storageMode, 'postgres');
    assert.equal(health.callPersistenceMode, 'postgres');
    assert.equal(health.realtimeArchitecture, 'single-instance');

    const cfgRes = await fetch(`${baseUrl1}/api/admin/calls/config`, {
      method: 'PUT',
      headers: { Authorization: 'Bearer u:admin_c2', 'Content-Type': 'application/json' },
      body: JSON.stringify({ monthlyBudgetAmount: 777, maxCallDurationMinutes: 45 })
    });
    assert.equal(cfgRes.status, 200);

    const ledger1 = await srv1.getLedger();
    await ledger1.submitAgeVerification({
      uid: 'caller_pg',
      displayName: 'كريم',
      email: 'caller_pg@ayer.test',
      dob: '1993-07-07',
      acceptedTermsAt: 1000,
      acceptedPrivacyAt: 1000
    });
    await ledger1.reviewAgeVerification({ reviewerUid: 'admin_c2', targetUid: 'caller_pg', decision: 'APPROVED' });

    const initRes = await fetch(`${baseUrl1}/api/calls/initiate`, {
      method: 'POST',
      headers: { Authorization: 'Bearer u:caller_pg', 'Content-Type': 'application/json' },
      body: JSON.stringify({ callerName: 'كريم', calleeId: 'callee_pg', calleeName: 'ليلى', callType: 'VIDEO' })
    });
    assert.equal(initRes.status, 201);
    createdCallId = (await initRes.json()).call.callId;

    const accRes = await fetch(`${baseUrl1}/api/calls/${createdCallId}/accept`, {
      method: 'POST',
      headers: { Authorization: 'Bearer u:callee_pg' }
    });
    assert.equal(accRes.status, 200);

    const endRes = await fetch(`${baseUrl1}/api/calls/${createdCallId}/end`, {
      method: 'POST',
      headers: { Authorization: 'Bearer u:caller_pg', 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: 'USER_HANGUP' })
    });
    assert.equal(endRes.status, 200);
  } finally {
    await new Promise((r) => srv1.server.close(r));
  }

  // Verify PG tables hold the persisted call state
  const pgState = harness.getState();
  assert.equal(pgState.callGovernanceConfig.monthlyBudgetAmount, 777);
  assert.equal(pgState.callSessions.get(createdCallId)?.status, 'ENDED');
  assert.equal(pgState.callCompletions.get(createdCallId)?.callType, 'VIDEO');

  // Boot Server 2 against the SAME PG Pool -> must load persisted call config & logs on startup
  const srv2 = createCallServer({ env: sharedEnv, pool: harness.pool, tokenVerifier });
  await new Promise((resolve) => srv2.server.listen(0, '127.0.0.1', resolve));
  const baseUrl2 = `http://127.0.0.1:${srv2.server.address().port}`;

  try {
    const cfgAfter = await fetch(`${baseUrl2}/api/admin/calls/config`, {
      headers: { Authorization: 'Bearer u:admin_c2' }
    });
    assert.equal(cfgAfter.status, 200);
    const cfgJson = await cfgAfter.json();
    assert.equal(cfgJson.monthlyBudgetAmount, 777);
    assert.equal(cfgJson.maxCallDurationMinutes, 45);

    const logsAfter = await fetch(`${baseUrl2}/api/admin/calls/logs`, {
      headers: { Authorization: 'Bearer u:admin_c2' }
    });
    assert.equal(logsAfter.status, 200);
    const logsJson = await logsAfter.json();
    assert.equal(logsJson.total, 1);
    assert.equal(logsJson.logs[0].callId, createdCallId);

    // Direct PostgresCallStore check
    const directCallStore = new PostgresCallStore({ pool: harness.pool });
    const loaded = await directCallStore.loadState();
    assert.equal(loaded.calls.length, 1);
    assert.equal(loaded.callLogs.length, 1);
  } finally {
    await new Promise((r) => srv2.server.close(r));
  }
});
