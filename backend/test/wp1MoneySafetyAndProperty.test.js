import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { createCallServer } from '../src/server.js';
import {
  MemoryLedgerStore,
  PostgresLedgerStore,
  SERVER_GIFT_CATALOG,
  PLATFORM_TREASURY_UID
} from '../src/ledgerStore.js';
import { reconcileLedgerSnapshot } from '../src/reconcileLedger.js';
import {
  resolveBankEncryptionKey,
  encryptSensitiveField,
  decryptSensitiveField
} from '../src/cryptoFields.js';

function startWp1Server({
  nowMsRef = { value: 1_750_000_000_000 },
  bankKeyHex = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
} = {}) {
  const ledger = new MemoryLedgerStore({
    nowProvider: () => nowMsRef.value,
    env: { BANK_ENCRYPTION_KEY: bankKeyHex }
  });
  const env = {
    LIVEKIT_URL: 'wss://livekit.test.invalid',
    LIVEKIT_API_KEY: 'lk_key',
    LIVEKIT_API_SECRET: 'lk_secret_12345678901234567890',
    ANDROID_PACKAGE_NAME: 'com.aistudio.ayer.live',
    ADMIN_UIDS: 'admin_1,admin_2',
    PAYOUTS_ENABLED: 'true',
    MISSION_REWARDS_ENABLED: 'true',
    PAID_MEDIA_ENABLED: 'true',
    AGENCIES_ENABLED: 'true',
    BANK_ENCRYPTION_KEY: bankKeyHex
  };
  const playBillingVerifier = async ({ productId, purchaseToken, expectedObfuscatedAccountId }) => ({
    orderId: `GPA.${purchaseToken.slice(0, 8)}`,
    purchaseState: 0,
    acknowledged: true,
    obfuscatedExternalAccountId: expectedObfuscatedAccountId || null,
    verificationSource: 'mock_play_v3',
    productId
  });
  const tokenVerifier = async (token) => {
    if (!token.startsWith('u:')) return null;
    const uid = token.slice(2);
    return {
      uid,
      name: uid,
      email: `${uid}@example.com`,
      isAdmin: uid === 'admin_1' || uid === 'admin_2'
    };
  };
  const { server, realtimeEngine } = createCallServer({ env, ledger, tokenVerifier, playBillingVerifier });
  realtimeEngine.now = () => nowMsRef.value;
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address();
      resolve({
        baseUrl: `http://127.0.0.1:${addr.port}`,
        ledger,
        realtimeEngine,
        nowMsRef,
        close: () => new Promise((r) => server.close(r))
      });
    });
  });
}

async function api(baseUrl, method, path, token, body = null, extraHeaders = {}) {
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...extraHeaders
    },
    ...(body !== null ? { body: JSON.stringify(body) } : {})
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, json };
}

async function approveAdult(ledger, uid) {
  await ledger.submitAgeVerification({
    uid,
    displayName: uid,
    email: `${uid}@example.com`,
    dob: '1994-06-15',
    acceptedTermsAt: 10,
    acceptedPrivacyAt: 10
  });
  await ledger.reviewAgeVerification({ reviewerUid: 'admin_1', targetUid: uid, decision: 'APPROVED' });
}

test('WP-1 A1: Free rewards enforce server-verified activities, device cooldown, and 25-coin daily cap', async () => {
  const srv = await startWp1Server();
  try {
    // 1. Unverifiable legacy missions & unverified activity claims are rejected
    for (const badId of ['mission_chat', 'mission_daily_login', 'daily_milestone']) {
      const r = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:u1', { missionId: badId });
      assert.equal(r.status, 400);
      assert.equal(r.json.error, 'INVALID_MISSION');
    }
    const notVerified = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:u1', { missionId: 'mission_stream' });
    assert.equal(notVerified.status, 403);
    assert.equal(notVerified.json.error, 'MISSION_NOT_VERIFIED');

    // 2. Host starts broadcast, u1 joins stream -> server verifies STREAM_WATCHED
    await srv.ledger.submitAgeVerification({
      uid: 'host_a1', displayName: 'Host', email: 'h@e.com', dob: '1995-01-01', ageYears: 30, acceptedTermsAt: 10, acceptedPrivacyAt: 10
    });
    await srv.ledger.reviewAgeVerification({ reviewerUid: 'admin_1', targetUid: 'host_a1', decision: 'APPROVED' });
    const streamRes = await api(srv.baseUrl, 'POST', '/api/streams', 'u:host_a1', { title: 'بث تجربة' });
    assert.equal(streamRes.status, 201);
    await approveAdult(srv.ledger, 'u1');
    const joinStream = await api(srv.baseUrl, 'POST', `/api/streams/${streamRes.json.stream.id}/join`, 'u:u1');
    assert.equal(joinStream.status, 200);

    // 3. u1 claims daily bonus (10) on dev-A -> second account u2 on SAME dev-A blocked by device cooldown (429)
    const bonusU1 = await api(srv.baseUrl, 'POST', '/api/wallet/daily-bonus', 'u:u1', {}, { 'X-Device-Id': 'device-A' });
    assert.equal(bonusU1.status, 200);
    assert.equal(bonusU1.json.creditedCoins, 10);

    const bonusU2SameDev = await api(srv.baseUrl, 'POST', '/api/wallet/daily-bonus', 'u:u2', {}, { 'X-Device-Id': 'device-A' });
    assert.equal(bonusU2SameDev.status, 429);
    assert.equal(bonusU2SameDev.json.error, 'DAILY_BONUS_DEVICE_COOLDOWN');

    // 4. u1 claims mission_stream (10) -> 20/25 daily free coins
    const mStream = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:u1', { missionId: 'mission_stream' }, { 'X-Device-Id': 'device-A' });
    assert.equal(mStream.status, 200);
    assert.equal(mStream.json.creditedCoins, 10);

    // 5. u1 sends a gift (uses 5 coins -> balance 15) -> unlocks GIFT_SENT -> claims mission_gifts (5) -> hits 25/25 cap
    const giftRes = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:u1', {
      recipientUid: 'host_a1',
      giftId: 'gift_rose',
      idempotencyKey: 'a1_gift_1'
    });
    assert.equal(giftRes.status, 200);
    const mGift = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:u1', { missionId: 'mission_gifts' }, { 'X-Device-Id': 'device-A' });
    assert.equal(mGift.status, 200);
    assert.equal(mGift.json.creditedCoins, 5);

    // 6. Even with MATCH_COMPLETED activity, u1 has hit the 25-coin daily free cap -> 429
    await srv.ledger.recordServerActivity({ uid: 'u1', activityType: 'MATCH_COMPLETED', count: 1 });
    const overCap = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:u1', { missionId: 'mission_matches' }, { 'X-Device-Id': 'device-A' });
    assert.equal(overCap.status, 429);
    assert.equal(overCap.json.error, 'DAILY_REWARD_CAP_REACHED');

    const recon = reconcileLedgerSnapshot(await srv.ledger.exportReconciliationSnapshot());
    assert.equal(recon.ok, true, recon.discrepancies.join('\n'));
  } finally {
    await srv.close();
  }
});

test('WP-1 A2 + A5 + R-4: Server gift catalog, strict entryType allowlist, and global economy commission cap <= 85%', async () => {
  const srv = await startWp1Server();
  try {
    // 1. Gift catalog endpoint returns authoritative costs
    const catalogRes = await api(srv.baseUrl, 'GET', '/api/wallet/gifts', 'u:buyer_x');
    assert.equal(catalogRes.status, 200);
    assert.equal(catalogRes.json.gifts.length, Object.keys(SERVER_GIFT_CATALOG).length);

    // 2. Non-admin cannot access or mutate economy config
    const forbidGet = await api(srv.baseUrl, 'GET', '/api/admin/economy/config', 'u:buyer_x');
    assert.equal(forbidGet.status, 403);
    const forbidPut = await api(srv.baseUrl, 'PUT', '/api/admin/economy/config', 'u:buyer_x', { agentCommissionPercent: 30 });
    assert.equal(forbidPut.status, 403);

    // 3. Admin cannot set host + agent > 85% (must leave >= 15% for platform)
    const capReject = await api(srv.baseUrl, 'PUT', '/api/admin/economy/config', 'u:admin_1', {
      hostCommissionPercent: 60,
      agentCommissionPercent: 30
    });
    assert.equal(capReject.status, 400);
    assert.equal(capReject.json.error, 'INVALID_COMMISSION_SPLIT');

    // Valid global config: host=60, agent=25 => platform=15
    const validPut = await api(srv.baseUrl, 'PUT', '/api/admin/economy/config', 'u:admin_1', {
      hostCommissionPercent: 60,
      agentCommissionPercent: 25
    });
    assert.equal(validPut.status, 200);
    assert.equal(validPut.json.platformCommissionPercent, 15);

    // 4. Buyer purchases coins & tries tampered gift price / unknown giftId / spend GIFT_SENT
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:buyer_x', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_1200',
      purchaseToken: 'pt_a5_test'
    });
    await approveAdult(srv.ledger, 'buyer_x');

    const priceMismatch = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:buyer_x', {
      recipientUid: 'host_x',
      giftId: 'gift_rocket',
      coinsCost: 1,
      idempotencyKey: 'tamper_1'
    });
    assert.equal(priceMismatch.status, 400);
    assert.equal(priceMismatch.json.error, 'GIFT_PRICE_MISMATCH');

    const unknownGift = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:buyer_x', {
      recipientUid: 'host_x',
      giftId: 'gift_hacked',
      coinsCost: 1,
      idempotencyKey: 'tamper_2'
    });
    assert.equal(unknownGift.status, 400);
    assert.equal(unknownGift.json.error, 'UNKNOWN_GIFT_ID');

    const badSpend = await api(srv.baseUrl, 'POST', '/api/wallet/spend', 'u:buyer_x', {
      amount: 10,
      entryType: 'GIFT_SENT',
      idempotencyKey: 'bad_spend_1'
    });
    assert.equal(badSpend.status, 400);
    assert.equal(badSpend.json.error, 'INVALID_ENTRY_TYPE');

    // Setup agent_x -> host_x without per-user override so global defaults (or assigned rates) apply
    await api(srv.baseUrl, 'POST', '/api/admin/users/agent_x/roles', 'u:admin_1', { isAgent: true, agentCommissionPercent: 25 });
    await api(srv.baseUrl, 'POST', '/api/admin/users/host_x/roles', 'u:admin_1', { isHost: true, hostCommissionPercent: 60 });
    await api(srv.baseUrl, 'POST', '/api/agency/sub-agents', 'u:agent_x', { subAgentUid: 'host_x', subAgentName: 'Host X', country: 'SA' });

    const goodGift = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:buyer_x', {
      recipientUid: 'host_x',
      giftId: 'gift_diamond', // 100 coins
      idempotencyKey: 'good_gift_100'
    });
    assert.equal(goodGift.status, 200);
    assert.equal(goodGift.json.recipientEarningsAdded, 60);
    assert.equal(goodGift.json.agentCommissionAdded, 25);
    assert.equal(goodGift.json.platformRevenueAdded, 15);
    assert.equal(goodGift.json.recipientEarningsAdded + goodGift.json.agentCommissionAdded + goodGift.json.platformRevenueAdded, 100);

    const platWallet = (await srv.ledger.ensureUser({ uid: PLATFORM_TREASURY_UID })).wallet;
    assert.equal(platWallet.platformRevenueCoins, 15);

    const recon = reconcileLedgerSnapshot(await srv.ledger.exportReconciliationSnapshot());
    assert.equal(recon.ok, true, recon.discrepancies.join('\n'));
  } finally {
    await srv.close();
  }
});

test('WP-1 A3 + A6: Encrypted IBAN, masked output, 250 coins = $1, max-1 active payout, 24h hold, self-approval block, dual-bucket refund', async () => {
  // Verify fail-closed production guard for BANK_ENCRYPTION_KEY
  assert.throws(
    () => resolveBankEncryptionKey({ NODE_ENV: 'production' }),
    /BANK_ENCRYPTION_KEY is required in production/
  );
  const keyBuf = resolveBankEncryptionKey({ BANK_ENCRYPTION_KEY: 'my-secret-passphrase' });
  const enc = encryptSensitiveField('SA4420000001234567891234', keyBuf);
  assert.ok(enc.startsWith('enc:v1:'));
  assert.equal(decryptSensitiveField(enc, keyBuf), 'SA4420000001234567891234');

  const srv = await startWp1Server();
  try {
    // Make admin_1 also an agent+host with earnings across BOTH buckets to test separation of duties & dual-bucket refund
    await api(srv.baseUrl, 'POST', '/api/admin/users/admin_1/roles', 'u:admin_2', {
      isHost: true,
      isAgent: true,
      hostCommissionPercent: 50,
      agentCommissionPercent: 20
    });
    await api(srv.baseUrl, 'POST', '/api/admin/users/sub_host/roles', 'u:admin_2', { isHost: true, hostCommissionPercent: 50 });
    await api(srv.baseUrl, 'POST', '/api/agency/sub-agents', 'u:admin_1', { subAgentUid: 'sub_host', subAgentName: 'Sub', country: 'EG' });

    await srv.ledger.submitAgeVerification({
      uid: 'admin_1', displayName: 'Admin One', email: 'a1@e.com', dob: '1990-01-01', ageYears: 36, acceptedTermsAt: 1, acceptedPrivacyAt: 1
    });
    await srv.ledger.reviewAgeVerification({ reviewerUid: 'admin_2', targetUid: 'admin_1', decision: 'APPROVED' });

    // Buyer buys 3000 + 600 = 3600 coins and sends gifts so admin_1 gets earnings in both hostEarnings and subAgentsEarnings
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:payer', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_3000',
      purchaseToken: 'pt_payer_3000'
    });
    await approveAdult(srv.ledger, 'payer');
    // 1) gift to sub_host (600 * 20% = 120 to admin_1 subAgentsEarningsCoins)
    await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:payer', { recipientUid: 'sub_host', giftId: 'gift_rocket', idempotencyKey: 'g_sub_1' });
    // 2) gift to admin_1 directly (600 * 50% + 600 * 50% = 600 to admin_1 hostEarningsCoins)
    await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:payer', { recipientUid: 'admin_1', giftId: 'gift_rocket', idempotencyKey: 'g_adm_1' });
    await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:payer', { recipientUid: 'admin_1', giftId: 'gift_rocket', idempotencyKey: 'g_adm_2' });

    const beforePayout = (await srv.ledger.ensureUser({ uid: 'admin_1' })).wallet;
    assert.equal(beforePayout.subAgentsEarningsCoins, 120);
    assert.equal(beforePayout.hostEarningsCoins, 600);

    // Submit 500-coin payout (takes 120 from subAgentsEarnings + 380 from hostEarnings)
    const createRes = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:admin_1', {
      amountCoins: 500,
      bankAccountHolder: 'أحمد الإداري',
      bankName: 'AlRajhi',
      ibanOrAccount: 'SA4420000001234567891234'
    });
    assert.equal(createRes.status, 201);
    assert.equal(createRes.json.request.usdEquivalentCents, 200); // 500 / 250 = $2.00
    assert.equal(createRes.json.request.ibanMasked, '********1234');
    assert.equal(createRes.json.request.ibanOrAccount, '********1234');
    assert.equal(createRes.json.wallet.subAgentsEarningsCoins, 0);
    assert.equal(createRes.json.wallet.hostEarningsCoins, 220);
    assert.equal(createRes.json.wallet.reservedPayoutCoins, 500);

    // Encrypted at rest in internal store (plaintext IBAN must NOT appear)
    const rawStored = srv.ledger.payoutRequests.get(createRes.json.request.id);
    assert.ok(rawStored.ibanOrAccount.startsWith('enc:v1:'));
    assert.ok(!rawStored.ibanOrAccount.includes('1234567891234'));

    // Second concurrent payout rejected (409 ACTIVE_PAYOUT_EXISTS)
    const dupActive = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:admin_1', {
      amountCoins: 500,
      bankAccountHolder: 'أحمد الإداري',
      bankName: 'AlRajhi',
      ibanOrAccount: 'SA4420000001234567891234'
    });
    assert.equal(dupActive.status, 409);
    assert.equal(dupActive.json.error, 'ACTIVE_PAYOUT_EXISTS');

    // admin_1 cannot approve own payout -> 403 SELF_APPROVAL_FORBIDDEN
    const selfApprove = await api(srv.baseUrl, 'POST', `/api/admin/payouts/${createRes.json.request.id}/review`, 'u:admin_1', {
      decision: 'APPROVED'
    });
    assert.equal(selfApprove.status, 403);
    assert.equal(selfApprove.json.error, 'SELF_APPROVAL_FORBIDDEN');

    // admin_2 cannot approve before 24h hold expires -> 409 PAYOUT_HOLD_ACTIVE
    const holdBlocked = await api(srv.baseUrl, 'POST', `/api/admin/payouts/${createRes.json.request.id}/review`, 'u:admin_2', {
      decision: 'APPROVED'
    });
    assert.equal(holdBlocked.status, 409);
    assert.equal(holdBlocked.json.error, 'PAYOUT_HOLD_ACTIVE');

    // admin_2 rejects -> EXACT dual-bucket restoration (120 subAgents + 380 host)
    const rejected = await api(srv.baseUrl, 'POST', `/api/admin/payouts/${createRes.json.request.id}/review`, 'u:admin_2', {
      decision: 'REJECTED',
      note: 'تحقق مستندات'
    });
    assert.equal(rejected.status, 200);
    assert.equal(rejected.json.wallet.subAgentsEarningsCoins, 120);
    assert.equal(rejected.json.wallet.hostEarningsCoins, 600);
    assert.equal(rejected.json.wallet.reservedPayoutCoins, 0);

    // Resubmit, advance 24h, admin_2 approves & pays
    const res2 = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:admin_1', {
      amountCoins: 500,
      bankAccountHolder: 'أحمد الإداري',
      bankName: 'AlRajhi',
      ibanOrAccount: 'SA4420000001234567891234'
    });
    assert.equal(res2.status, 201);
    srv.nowMsRef.value += 24 * 60 * 60 * 1000 + 5000;
    const paidRes = await api(srv.baseUrl, 'POST', `/api/admin/payouts/${res2.json.request.id}/review`, 'u:admin_2', {
      decision: 'PAID',
      paymentReference: 'WIRE-A6-001'
    });
    assert.equal(paidRes.status, 200);
    assert.equal(paidRes.json.request.status, 'PAID');

    const recon = reconcileLedgerSnapshot(await srv.ledger.exportReconciliationSnapshot());
    assert.equal(recon.ok, true, recon.discrepancies.join('\n'));
  } finally {
    await srv.close();
  }
});

test('WP-1 A8: Stale match queue hold expiry and partner hold capture failure rollback preserve balance conservation', async () => {
  const srv = await startWp1Server();
  try {
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:m1', {
      packageName: 'com.aistudio.ayer.live', productId: 'ayer_coins_100', purchaseToken: 'pt_m1'
    });
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:m2', {
      packageName: 'com.aistudio.ayer.live', productId: 'ayer_coins_100', purchaseToken: 'pt_m2'
    });
    await approveAdult(srv.ledger, 'm1');
    await approveAdult(srv.ledger, 'm2');

    // 1. m1 enters queue (holds 3 coins: 110 -> 107)
    const j1 = await api(srv.baseUrl, 'POST', '/api/match/join', 'u:m1', { chatType: 'VIDEO' });
    assert.equal(j1.status, 200);
    assert.equal(j1.json.status, 'WAITING');
    assert.equal(j1.json.wallet.pointsBalance, 107);
    assert.equal(j1.json.wallet.heldCoins, 3);

    // Advance 95s -> queue entry expires -> m1 polls -> pruned and hold released (107 -> 110, heldCoins -> 0)
    srv.nowMsRef.value += 95_000;
    const pollExpired = await api(srv.baseUrl, 'GET', '/api/match/poll', 'u:m1');
    assert.equal(pollExpired.status, 200);
    assert.equal(pollExpired.json.status, 'IDLE');
    const m1AfterExpiry = (await srv.ledger.ensureUser({ uid: 'm1' })).wallet;
    assert.equal(m1AfterExpiry.pointsBalance, 110);
    assert.equal(m1AfterExpiry.heldCoins, 0);

    // 2. Partner hold capture failure rollback: m1 waits with corrupt/pre-settled holdId -> m2 joins -> partner capture fails -> m2's hold is released & match aborted
    const j1b = await api(srv.baseUrl, 'POST', '/api/match/join', 'u:m1', { chatType: 'VIDEO' });
    assert.equal(j1b.json.status, 'WAITING');
    // Force-release m1's hold underneath to simulate a capture conflict on partner
    await srv.ledger.releaseHold({ holdId: j1b.json.holdId, reason: 'SIMULATED_RACE' });

    const j2Fail = await api(srv.baseUrl, 'POST', '/api/match/join', 'u:m2', { chatType: 'VIDEO' });
    assert.equal(j2Fail.status, 409);
    const m2WalletAfter = (await srv.ledger.ensureUser({ uid: 'm2' })).wallet;
    assert.equal(m2WalletAfter.pointsBalance, 110);
    assert.equal(m2WalletAfter.heldCoins, 0);
    assert.equal(srv.realtimeEngine.getActiveMatch('u:m2'), null);

    const recon = reconcileLedgerSnapshot(await srv.ledger.exportReconciliationSnapshot());
    assert.equal(recon.ok, true, recon.discrepancies.join('\n'));
  } finally {
    await srv.close();
  }
});

test('WP-1 Exit Gate Property Test: 200 randomized financial operations maintain 100% ledger conservation (MemoryLedgerStore)', async () => {
  let now = 1_750_000_000_000;
  const store = new MemoryLedgerStore({
    nowProvider: () => now,
    env: { BANK_ENCRYPTION_KEY: 'prop-test-bank-key' }
  });

  const buyers = ['buyer_0', 'buyer_1', 'buyer_2'];
  const hosts = ['host_0', 'host_1'];
  const agents = ['agent_0'];
  const admins = ['admin_a', 'admin_b'];
  const giftIds = Object.keys(SERVER_GIFT_CATALOG);

  await store.adminSetUserRolesAndCommission({ reviewerUid: 'admin_a', targetUid: 'agent_0', isAgent: true, agentCommissionPercent: 20 });
  await store.submitAgeVerification({
    uid: 'agent_0', displayName: 'agent_0', email: 'agent_0@e.com', dob: '1992-03-03', ageYears: 34, acceptedTermsAt: 1, acceptedPrivacyAt: 1
  });
  await store.reviewAgeVerification({ reviewerUid: 'admin_a', targetUid: 'agent_0', decision: 'APPROVED' });
  for (const h of hosts) {
    await store.adminSetUserRolesAndCommission({ reviewerUid: 'admin_a', targetUid: h, isHost: true, hostCommissionPercent: 55 });
    await store.recruitSubAgent({ agentUid: 'agent_0', subAgentUid: h, subAgentName: h, country: 'SA' });
    await store.submitAgeVerification({
      uid: h, displayName: h, email: `${h}@e.com`, dob: '1994-05-05', ageYears: 32, acceptedTermsAt: 1, acceptedPrivacyAt: 1
    });
    await store.reviewAgeVerification({ reviewerUid: 'admin_a', targetUid: h, decision: 'APPROVED' });
  }
  for (const b of buyers) {
    await approveAdult(store, b);
  }

  // Deterministic PRNG (Mulberry32)
  let seed = 0x57503121;
  const rand = () => {
    seed |= 0;
    seed = (seed + 0x6D2B79F5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const pick = (arr) => arr[Math.floor(rand() * arr.length)];

  const activeHoldIds = [];
  const activeTokensByUid = new Map(buyers.map((u) => [u, []]));
  const openPayoutIds = [];

  for (let step = 0; step < 220; step++) {
    now += 3_600_000; // Advance 1h per op so 24h payout holds & bonuses cycle naturally
    const op = Math.floor(rand() * 9);

    try {
      if (op === 0 || op === 1) {
        // Play purchase
        const uid = pick(buyers);
        const coins = pick([110, 615, 1400]);
        const ptok = `pt_prop_${step}_${uid}`;
        await store.creditPlayPurchase({
          uid, packageId: `pkg_${coins}`, productId: 'ayer_coins_100', coinsCredited: coins, priceUsd: 1.0, purchaseToken: ptok
        });
        activeTokensByUid.get(uid).push(ptok);
      } else if (op === 2) {
        // Gift
        const uid = pick(buyers);
        const host = pick(hosts);
        const giftId = pick(giftIds);
        await store.sendGiftAtomic({
          senderUid: uid, recipientUid: host, giftId, idempotencyKey: `prop_gift_${step}`
        });
      } else if (op === 3) {
        // Spend
        const uid = pick(buyers);
        const amt = 1 + Math.floor(rand() * 25);
        await store.spendCoins({
          uid, amount: amt, entryType: 'MATCH_SPEND', idempotencyKey: `prop_spend_${step}`
        });
      } else if (op === 4) {
        // Hold coins
        const uid = pick(buyers);
        const amt = 3 + Math.floor(rand() * 15);
        const h = await store.holdCoins({ uid, amount: amt, purpose: 'MATCH_HOLD', idempotencyKey: `prop_hold_${step}` });
        activeHoldIds.push(h.hold.holdId);
      } else if (op === 5 && activeHoldIds.length > 0) {
        // Capture (partial or full) or release hold
        const idx = Math.floor(rand() * activeHoldIds.length);
        const holdId = activeHoldIds.splice(idx, 1)[0];
        if (rand() < 0.65) {
          const cap = rand() < 0.5 ? null : 2;
          await store.captureHold({ holdId, captureAmount: cap, entryType: 'MATCH_SPEND', creditPlatformRevenue: true, idempotencyKey: `prop_cap_${step}` });
        } else {
          await store.releaseHold({ holdId, reason: 'PROP_CANCEL', idempotencyKey: `prop_rel_${step}` });
        }
      } else if (op === 6) {
        // Payout request by host/agent
        const uid = pick([...hosts, ...agents]);
        const res = await store.createPayoutRequest({
          uid, amountCoins: 500, bankAccountHolder: 'Test Host', bankName: 'TestBank', ibanOrAccount: 'SA11223344556677'
        });
        openPayoutIds.push(res.request.id);
      } else if (op === 7 && openPayoutIds.length > 0) {
        // Admin reviews open payout (REJECTED or PAID)
        const idx = Math.floor(rand() * openPayoutIds.length);
        const reqId = openPayoutIds[idx];
        const decision = rand() < 0.45 ? 'REJECTED' : 'PAID';
        await store.reviewPayoutRequest({
          reviewerUid: pick(admins),
          requestId: reqId,
          decision,
          note: 'prop review',
          paymentReference: decision === 'PAID' ? `REF-${step}` : null
        });
        openPayoutIds.splice(idx, 1);
      } else if (op === 8) {
        // Daily bonus or RTDN clawback
        const uid = pick(buyers);
        if (rand() < 0.7) {
          await store.claimDailyBonus({ uid, deviceId: `dev_${uid}` });
        } else {
          const tokens = activeTokensByUid.get(uid);
          if (tokens.length > 0) {
            const ptok = tokens.shift();
            await store.processVoidedPurchase({ purchaseToken: ptok, reason: 'PROP_CLAWBACK' });
          }
        }
      }
    } catch (err) {
      // Expected domain guards (insufficient coins, debt block, 24h payout hold, cooldown, active payout) are valid rejections
      const expectedCodes = new Set([
        'INSUFFICIENT_COINS', 'INSUFFICIENT_EARNINGS', 'ACCOUNT_HAS_DEBT', 'DAILY_BONUS_COOLDOWN',
        'DAILY_BONUS_DEVICE_COOLDOWN', 'DAILY_REWARD_CAP_REACHED', 'ACTIVE_PAYOUT_EXISTS',
        'PAYOUT_HOLD_ACTIVE', 'INVALID_CAPTURE_AMOUNT'
      ]);
      assert.ok(expectedCodes.has(err?.code), `Unexpected error at step ${step}: ${err?.code || err?.message}`);
    }

    const recon = reconcileLedgerSnapshot(await store.exportReconciliationSnapshot());
    assert.equal(recon.ok, true, `Conservation violated at step ${step}: ${recon.discrepancies.join('; ')}`);
  }

  const finalSnap = await store.exportReconciliationSnapshot();
  const finalRecon = reconcileLedgerSnapshot(finalSnap);
  assert.equal(finalRecon.ok, true);
  assert.ok(finalRecon.totals.totalMintedCoins > 0);
});

test('WP-1 PostgresLedgerStore SQL parity: hold/capture/release, gift splits, economy config, payouts, and reconciliation', async () => {
  const db = {
    users: new Map(),
    wallets: new Map(),
    iap: new Map(),
    holds: new Map(),
    payouts: [],
    ledger: [],
    audits: [],
    subAgents: [],
    ageVerifications: new Map(),
    dailyActivities: new Map(),
    dailyDeviceClaims: new Map(),
    economyConfig: null
  };

  const exec = {
    async query(sql, params = []) {
      const q = sql.replace(/\s+/g, ' ').trim();
      if (q === 'BEGIN' || q === 'COMMIT' || q === 'ROLLBACK') return { rowCount: 0, rows: [] };

      if (q.startsWith('INSERT INTO users')) {
        const isSubAgentInsert = q.includes('country');
        const uid = params[0];
        const displayName = params[1];
        const email = isSubAgentInsert ? '' : params[2];
        const country = isSubAgentInsert ? params[2] : '';
        const referralCode = params[3];
        const isAdmin = isSubAgentInsert ? false : Boolean(params[4]);
        const adminRole = isSubAgentInsert ? 'NONE' : (params[5] || 'NONE');
        if (!db.users.has(uid)) {
          db.users.set(uid, {
            uid, display_name: displayName, email, photo_url: null, bio: '',
            country, country_code: '',
            gender: '', dob: null, age_years: 0, age_verified: false, age_verification_status: 'UNVERIFIED',
            status: 'ACTIVE', status_reason: null, underage_locked: false, underage_locked_reason: null, underage_locked_at: null,
            is_admin: isAdmin, admin_role: adminRole, is_agent: false, is_host: false, host_status: 'NONE',
            agent_commission_percent: 20, host_commission_percent: 50, agency_id: '', referral_code: referralCode,
            profile_visibility: 'PUBLIC', direct_message_permission: 'EVERYONE',
            accepted_terms_at: null, accepted_privacy_at: null, deleted_at: null
          });
        } else if (q.includes('DO UPDATE SET display_name=')) {
          const u = db.users.get(uid);
          u.display_name = displayName;
          u.country = country;
        }
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('INSERT INTO agencies')) {
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('INSERT INTO agency_memberships')) {
        const [uid, agency_id, parent_agent_uid, pct] = params;
        const idx = db.subAgents.findIndex((s) => s.uid === uid);
        const row = { uid, agency_id, role: 'SUB_AGENT', parent_agent_uid, commission_percent: pct };
        if (idx >= 0) db.subAgents[idx] = row; else db.subAgents.push(row);
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('SELECT COUNT(*)::int AS cnt FROM agency_memberships WHERE parent_agent_uid=$1')) {
        const cnt = db.subAgents.filter((s) => s.parent_agent_uid === params[0]).length;
        return { rowCount: 1, rows: [{ cnt }] };
      }
      if (q.startsWith('SELECT parent_agent_uid FROM agency_memberships WHERE uid = $1')) {
        const found = db.subAgents.find((s) => s.uid === params[0]);
        return { rowCount: found ? 1 : 0, rows: found ? [{ parent_agent_uid: found.parent_agent_uid }] : [] };
      }
      if (q.startsWith('INSERT INTO wallet_accounts')) {
        const [uid] = params;
        if (!db.wallets.has(uid)) {
          db.wallets.set(uid, {
            uid, points_balance: 0, debt_coins: 0, held_coins: 0, host_earnings_coins: 0,
            sub_agents_earnings_coins: 0, reserved_payout_coins: 0, platform_revenue_coins: 0,
            total_accumulated_points: 0, total_points_spent: 0, last_daily_claim_ms: 0
          });
        }
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('SELECT * FROM users WHERE uid = $1')) {
        const u = db.users.get(params[0]);
        return { rowCount: u ? 1 : 0, rows: u ? [{ ...u }] : [] };
      }
      if (q.startsWith('SELECT * FROM wallet_accounts WHERE uid = $1')) {
        const w = db.wallets.get(params[0]);
        return { rowCount: w ? 1 : 0, rows: w ? [{ ...w }] : [] };
      }
      if (q === 'SELECT * FROM wallet_accounts' || q.startsWith('SELECT * FROM wallet_accounts ORDER BY uid')) {
        return { rowCount: db.wallets.size, rows: [...db.wallets.values()].map((w) => ({ ...w })) };
      }
      if (q.startsWith('SELECT * FROM economy_config WHERE id = 1')) {
        return db.economyConfig ? { rowCount: 1, rows: [{ ...db.economyConfig }] } : { rowCount: 0, rows: [] };
      }
      if (q.startsWith('INSERT INTO economy_config')) {
        const [hostP, agentP, platP, updatedBy] = params;
        db.economyConfig = {
          id: 1, host_commission_percent: hostP, agent_commission_percent: agentP, platform_commission_percent: platP,
          min_platform_percent: 15, coins_per_usd: 250, min_payout_coins: 500, payout_hold_hours: 24,
          daily_free_reward_cap_coins: 25, updated_by: updatedBy
        };
        return { rowCount: 1, rows: [{ ...db.economyConfig }] };
      }
      if (q.startsWith('UPDATE users SET is_agent = COALESCE')) {
        const [isAgent, isHost, isAdmin, agentP, hostP, agencyId, uid, agencyName, adminRole] = params;
        const u = db.users.get(uid);
        if (isAgent !== null) u.is_agent = isAgent;
        if (isHost !== null) {
          u.is_host = isHost;
          u.host_status = isHost ? 'APPROVED' : 'NONE';
        }
        if (isAdmin !== null) u.is_admin = isAdmin;
        if (adminRole !== null && adminRole !== undefined) u.admin_role = adminRole;
        if (agentP !== null) u.agent_commission_percent = agentP;
        if (hostP !== null) u.host_commission_percent = hostP;
        if (agencyId !== null && agencyId !== '') u.agency_id = agencyId;
        if (agencyName !== null && agencyName !== undefined) u.agency_name = agencyName;
        return { rowCount: 1, rows: [{ ...u }] };
      }
      if (q.startsWith('UPDATE users SET dob = $1, age_years = $2')) {
        const [dob, ageYears, termsAt, privacyAt, verified, status, uid] = params;
        const u = db.users.get(uid);
        u.dob = dob; u.age_years = ageYears;
        u.accepted_terms_at = termsAt; u.accepted_privacy_at = privacyAt;
        u.age_verified = verified; u.age_verification_status = status;
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('INSERT INTO age_verifications')) {
        const [uid, display_name, email, dob, age_years, status, reviewer_uid, rejection_reason, accepted_terms_at, accepted_privacy_at, reviewed_at] = params;
        const row = {
          uid, display_name, email, dob, age_years, status, reviewer_uid, rejection_reason,
          accepted_terms_at, accepted_privacy_at, submitted_at: new Date().toISOString(), reviewed_at
        };
        db.ageVerifications.set(uid, row);
        return { rowCount: 1, rows: [{ ...row }] };
      }
      if (q.startsWith('UPDATE age_verifications')) {
        const [status, reviewer_uid, rejection_reason, uid] = params;
        const row = db.ageVerifications.get(uid) || { uid };
        row.status = status; row.reviewer_uid = reviewer_uid; row.rejection_reason = rejection_reason;
        row.reviewed_at = new Date().toISOString();
        db.ageVerifications.set(uid, row);
        return { rowCount: 1, rows: [{ ...row }] };
      }
      if (q.startsWith('UPDATE users SET age_verified=$1')) {
        const [verified, status, uid] = params;
        const u = db.users.get(uid);
        u.age_verified = verified; u.age_verification_status = status;
        return { rowCount: 1, rows: [{ ...u }] };
      }
      if (q.startsWith('INSERT INTO iap_transactions')) {
        const [purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros, purchase_state, acknowledged, obfuscated_external_account_id, verification_source] = params;
        if (db.iap.has(purchase_token)) return { rowCount: 0, rows: [] };
        const row = {
          purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros,
          currency_code: 'USD', purchase_state, acknowledged, obfuscated_external_account_id,
          is_voided: false, verification_source
        };
        db.iap.set(purchase_token, row);
        return { rowCount: 1, rows: [{ ...row }] };
      }
      if (q.includes('SET points_balance = points_balance + $1, debt_coins = debt_coins - $2')) {
        const [addPts, settleDebt, totalAcc, uid] = params;
        const w = db.wallets.get(uid);
        w.points_balance += addPts; w.debt_coins -= settleDebt; w.total_accumulated_points += totalAcc;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('SELECT * FROM ledger_holds WHERE idempotency_key = $1')) {
        const h = [...db.holds.values()].find((x) => x.idempotency_key === params[0]);
        return { rowCount: h ? 1 : 0, rows: h ? [{ ...h }] : [] };
      }
      if (q.startsWith('SELECT * FROM ledger_holds WHERE hold_id = $1')) {
        const h = db.holds.get(params[0]);
        return { rowCount: h ? 1 : 0, rows: h ? [{ ...h }] : [] };
      }
      if (q === 'SELECT * FROM ledger_holds' || q.startsWith('SELECT * FROM ledger_holds ORDER BY created_at')) {
        return { rowCount: db.holds.size, rows: [...db.holds.values()].map((h) => ({ ...h })) };
      }
      if (q.includes('SET points_balance = points_balance - $1, held_coins = held_coins + $1')) {
        const [amt, uid] = params;
        const w = db.wallets.get(uid);
        if (w.points_balance < amt) return { rowCount: 0, rows: [] };
        w.points_balance -= amt; w.held_coins += amt;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('INSERT INTO ledger_holds')) {
        const [hold_id, uid, purpose, reference_id, amount_coins, idempotency_key] = params;
        const h = {
          hold_id, uid, purpose, reference_id, amount_coins, captured_coins: 0, released_coins: 0,
          status: 'ACTIVE', idempotency_key, created_at: new Date().toISOString(), settled_at: null
        };
        db.holds.set(hold_id, h);
        return { rowCount: 1, rows: [{ ...h }] };
      }
      if (q.includes('SET held_coins = held_coins - $1, points_balance = points_balance + $2, total_points_spent = total_points_spent + $3')) {
        const [rem, rel, cap, uid] = params;
        const w = db.wallets.get(uid);
        if (w.held_coins < rem) return { rowCount: 0, rows: [] };
        w.held_coins -= rem; w.points_balance += rel; w.total_points_spent += cap;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('UPDATE ledger_holds SET captured_coins = captured_coins + $1')) {
        const [cap, rel, holdId] = params;
        const h = db.holds.get(holdId);
        h.captured_coins += cap; h.released_coins += rel; h.status = 'CAPTURED'; h.settled_at = new Date().toISOString();
        return { rowCount: 1, rows: [{ ...h }] };
      }
      if (q.includes('SET held_coins = GREATEST(0, held_coins - $1), points_balance = points_balance + $1')) {
        const [rem, uid] = params;
        const w = db.wallets.get(uid);
        w.held_coins = Math.max(0, w.held_coins - rem); w.points_balance += rem;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('UPDATE ledger_holds SET released_coins = released_coins + $1')) {
        const [rem, holdId] = params;
        const h = db.holds.get(holdId);
        h.released_coins += rem; h.status = 'RELEASED'; h.settled_at = new Date().toISOString();
        return { rowCount: 1, rows: [{ ...h }] };
      }
      if (q.includes('SET points_balance = points_balance - $1, total_points_spent = total_points_spent + $1')) {
        const [amt, uid] = params;
        const w = db.wallets.get(uid);
        if (w.points_balance < amt) return { rowCount: 0, rows: [] };
        w.points_balance -= amt; w.total_points_spent += amt;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.includes('SET host_earnings_coins = host_earnings_coins + $1')) {
        const [amt, uid] = params;
        const w = db.wallets.get(uid);
        w.host_earnings_coins += amt;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.includes('SET sub_agents_earnings_coins = sub_agents_earnings_coins + $1')) {
        const [amt, uid] = params;
        const w = db.wallets.get(uid);
        w.sub_agents_earnings_coins += amt;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.includes('SET platform_revenue_coins = platform_revenue_coins + $1')) {
        const [amt, uid] = params;
        const w = db.wallets.get(uid);
        w.platform_revenue_coins += amt;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('INSERT INTO daily_activity_counters')) {
        const [uid, dayKey, type, cnt] = params;
        const k = `${uid}:${dayKey}:${type}`;
        const next = (db.dailyActivities.get(k) || 0) + cnt;
        db.dailyActivities.set(k, next);
        return { rowCount: 1, rows: [{ activity_count: next }] };
      }
      if (q.startsWith('SELECT id FROM payout_requests WHERE uid = $1 AND status IN')) {
        const uid = params[0];
        const found = db.payouts.filter((p) => p.uid === uid && (p.status === 'PENDING_REVIEW' || p.status === 'APPROVED'));
        return { rowCount: found.length > 0 ? 1 : 0, rows: found.slice(0, 1).map((p) => ({ id: p.id })) };
      }
      if (q.includes('SET sub_agents_earnings_coins = sub_agents_earnings_coins - $1, host_earnings_coins = host_earnings_coins - $2, reserved_payout_coins = reserved_payout_coins + $3')) {
        const [fromSub, fromHost, total, uid] = params;
        const w = db.wallets.get(uid);
        w.sub_agents_earnings_coins -= fromSub; w.host_earnings_coins -= fromHost; w.reserved_payout_coins += total;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('INSERT INTO payout_requests')) {
        const [id, uid, amount_coins, usd_equivalent_cents, payout_method, bank_account_holder, bank_name, iban_or_account, iban_masked, from_sub_agents_coins, from_host_coins, earliest_review_at] = params;
        const row = {
          id, uid, amount_coins, usd_equivalent_cents, payout_method, bank_account_holder, bank_name,
          iban_or_account, iban_masked, from_sub_agents_coins, from_host_coins,
          earliest_review_at, status: 'PENDING_REVIEW', created_at: new Date().toISOString()
        };
        db.payouts.push(row);
        return { rowCount: 1, rows: [{ ...row }] };
      }
      if (q.startsWith('SELECT * FROM payout_requests WHERE id = $1')) {
        const r = db.payouts.find((p) => p.id === params[0]);
        return { rowCount: r ? 1 : 0, rows: r ? [{ ...r }] : [] };
      }
      if (q === 'SELECT * FROM payout_requests' || q.startsWith('SELECT * FROM payout_requests ORDER BY created_at')) {
        return { rowCount: db.payouts.length, rows: db.payouts.map((p) => ({ ...p })) };
      }
      if (q.includes('SET reserved_payout_coins = GREATEST(0, reserved_payout_coins - $1), sub_agents_earnings_coins = sub_agents_earnings_coins + $2, host_earnings_coins = host_earnings_coins + $3')) {
        const [amt, fromSub, fromHost, uid] = params;
        const w = db.wallets.get(uid);
        w.reserved_payout_coins = Math.max(0, w.reserved_payout_coins - amt); w.sub_agents_earnings_coins += fromSub; w.host_earnings_coins += fromHost;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('UPDATE payout_requests SET status=$1')) {
        const [status, reviewer_uid, reviewer_note, payment_reference, id] = params;
        const p = db.payouts.find((x) => x.id === id);
        p.status = status; p.reviewer_uid = reviewer_uid; p.reviewer_note = reviewer_note; p.payment_reference = payment_reference;
        return { rowCount: 1, rows: [{ ...p }] };
      }
      if (q.startsWith('SELECT * FROM ledger_entries WHERE idempotency_key = $1')) {
        const found = db.ledger.filter((e) => e.idempotency_key === params[0]);
        return { rowCount: found.length, rows: found };
      }
      if (q.startsWith('SELECT 1 FROM ledger_entries WHERE idempotency_key IN ($1, $2)')) {
        const found = db.ledger.filter((e) => e.idempotency_key === params[0] || e.idempotency_key === params[1]);
        return { rowCount: found.length, rows: found };
      }
      if (q === 'SELECT * FROM ledger_entries' || q.startsWith('SELECT * FROM ledger_entries ORDER BY id')) {
        return { rowCount: db.ledger.length, rows: [...db.ledger] };
      }
      if (q.startsWith('INSERT INTO ledger_entries')) {
        let entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata;
        if (q.includes("VALUES ($1,$2,'PURCHASE','POINTS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'PURCHASE'; bucket = 'POINTS';
        } else if (q.includes("VALUES ($1,$2,'HOLD_RESERVE','POINTS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'HOLD_RESERVE'; bucket = 'POINTS';
        } else if (q.includes("VALUES ($1,$2,'HOLD_RELEASE','POINTS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'HOLD_RELEASE'; bucket = 'POINTS';
        } else if (q.includes("VALUES ($1,$2,'GIFT_SENT','POINTS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'GIFT_SENT'; bucket = 'POINTS';
        } else if (q.includes("VALUES ($1,$2,'GIFT_RECEIVE','HOST_EARNINGS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'GIFT_RECEIVE'; bucket = 'HOST_EARNINGS';
        } else if (q.includes("VALUES ($1,$2,'AGENT_COMMISSION','AGENCY_EARNINGS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'AGENT_COMMISSION'; bucket = 'AGENCY_EARNINGS';
        } else if (q.includes("VALUES ($1,$2,'PLATFORM_REVENUE','PLATFORM_REVENUE'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'PLATFORM_REVENUE'; bucket = 'PLATFORM_REVENUE';
        } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL_RESERVE','RESERVED_PAYOUT'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          entry_type = 'WITHDRAWAL_RESERVE'; bucket = 'RESERVED_PAYOUT';
        } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL_REFUND','AGENCY_EARNINGS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description] = params;
          entry_type = 'WITHDRAWAL_REFUND'; bucket = 'AGENCY_EARNINGS'; metadata = '{}';
        } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL_REFUND','HOST_EARNINGS'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description] = params;
          entry_type = 'WITHDRAWAL_REFUND'; bucket = 'HOST_EARNINGS'; metadata = '{}';
        } else if (q.includes("VALUES ($1,$2,'WITHDRAWAL','RESERVED_PAYOUT'")) {
          [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description] = params;
          entry_type = 'WITHDRAWAL'; bucket = 'RESERVED_PAYOUT'; metadata = '{}';
        } else if (q.includes("VALUES ($1,$2,$3,'HELD_POINTS'")) {
          [entry_uuid, uid, entry_type, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          bucket = 'HELD_POINTS';
        } else {
          [entry_uuid, uid, entry_type, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          bucket = 'POINTS';
        }
        if (!db.ledger.some((e) => e.idempotency_key === idempotency_key)) {
          db.ledger.push({
            id: db.ledger.length + 1,
            entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description,
            metadata: metadata ? JSON.parse(metadata) : {}
          });
        }
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('INSERT INTO audit_logs')) {
        db.audits.push({ sql: q, params });
        return { rowCount: 1, rows: [] };
      }
      throw new Error(`Unhandled PG SQL in WP-1 parity test: ${q}`);
    },
    release() {}
  };

  let now = 1_750_000_000_000;
  const pgStore = new PostgresLedgerStore({
    pool: { query: (s, p) => exec.query(s, p), connect: async () => exec },
    nowProvider: () => now,
    env: { BANK_ENCRYPTION_KEY: 'pg-parity-secret' }
  });

  await pgStore.updateEconomyConfig({ reviewerUid: 'admin_a', hostCommissionPercent: 60, agentCommissionPercent: 25 });
  await pgStore.adminSetUserRolesAndCommission({ reviewerUid: 'admin_a', targetUid: 'pg_agent', isAgent: true, agentCommissionPercent: 25 });
  await pgStore.adminSetUserRolesAndCommission({ reviewerUid: 'admin_a', targetUid: 'pg_host', isHost: true, hostCommissionPercent: 60 });
  await pgStore.recruitSubAgent({ agentUid: 'pg_agent', subAgentUid: 'pg_host', subAgentName: 'PG Host', country: 'SA' });
  await pgStore.submitAgeVerification({
    uid: 'pg_host', displayName: 'PG Host', email: 'h@pg.com', dob: '1995-01-01', ageYears: 30, acceptedTermsAt: 1, acceptedPrivacyAt: 1
  });
  await pgStore.reviewAgeVerification({ reviewerUid: 'admin_a', targetUid: 'pg_host', decision: 'APPROVED' });

  await pgStore.creditPlayPurchase({
    uid: 'pg_buyer', packageId: 'pkg_1200', productId: 'ayer_coins_1200', coinsCredited: 1400, priceUsd: 9.99, purchaseToken: 'pg_wp1_1'
  });
  await approveAdult(pgStore, 'pg_buyer');

  // Gift 600 + 600 => host gets 720, agent gets 300, platform gets 180
  await pgStore.sendGiftAtomic({ senderUid: 'pg_buyer', recipientUid: 'pg_host', giftId: 'gift_rocket', idempotencyKey: 'pg_g1' });
  await pgStore.sendGiftAtomic({ senderUid: 'pg_buyer', recipientUid: 'pg_host', giftId: 'gift_rocket', idempotencyKey: 'pg_g2' });

  // Hold 10 -> capture 4 (releases 6), Hold 10 -> full release
  const h1 = await pgStore.holdCoins({ uid: 'pg_buyer', amount: 10, purpose: 'MATCH_HOLD', idempotencyKey: 'pg_h1' });
  const c1 = await pgStore.captureHold({ holdId: h1.hold.holdId, captureAmount: 4, idempotencyKey: 'pg_c1' });
  assert.equal(c1.wallet.pointsBalance, 196);
  assert.equal(c1.wallet.heldCoins, 0);

  const h2 = await pgStore.holdCoins({ uid: 'pg_buyer', amount: 10, purpose: 'MATCH_HOLD', idempotencyKey: 'pg_h2' });
  const r2 = await pgStore.releaseHold({ holdId: h2.hold.holdId, reason: 'TIMEOUT' });
  assert.equal(r2.wallet.pointsBalance, 196);

  // Payout 500 -> reject (restores)
  const po = await pgStore.createPayoutRequest({
    uid: 'pg_host', amountCoins: 500, bankAccountHolder: 'PG Host', bankName: 'SABB', ibanOrAccount: 'SA99000011112222'
  });
  assert.equal(po.request.usdEquivalentCents, 200);
  assert.equal(po.request.ibanMasked, '********2222');
  const rej = await pgStore.reviewPayoutRequest({ reviewerUid: 'admin_b', requestId: po.request.id, decision: 'REJECTED', note: 'retry' });
  assert.equal(rej.wallet.hostEarningsCoins, 720);

  const snap = await pgStore.exportReconciliationSnapshot();
  const recon = reconcileLedgerSnapshot(snap);
  assert.equal(recon.ok, true, recon.discrepancies.join('\n'));
  assert.equal(recon.totals.totalPlatformRevenue, 184); // 180 from gifts + 4 from captureHold
});
