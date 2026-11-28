import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CallEngineError } from './callEngine.js';
import {
  encryptSensitiveField,
  maskAccountNumber,
  resolveBankEncryptionKey
} from './cryptoFields.js';
import { runMigrations } from './migrator.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const PLATFORM_TREASURY_UID = '__platform__';
export const MIN_PLATFORM_PERCENT = 15;
export const COINS_PER_USD = 250; // $0.0040/coin < net Play proceeds on cheapest pack ($0.004515/coin after 30% Google cut)
export const MIN_PAYOUT_COINS = 500;
export const PAYOUT_HOLD_HOURS = 24;
export const PAYOUT_HOLD_MS = PAYOUT_HOLD_HOURS * 60 * 60 * 1000;
export const DAILY_BONUS_COINS = 10;
export const DAILY_CLAIM_COOLDOWN_MS = 24 * 60 * 60 * 1000;
export const DAILY_FREE_REWARD_CAP_COINS = 25;

export const ALLOWED_CLIENT_SPEND_ENTRY_TYPES = new Set([
  'MATCH_SPEND',
  'CALL_MINUTE_CHARGE',
  'BOOST_PROFILE'
]);

const SPEND_DESCRIPTIONS = Object.freeze({
  MATCH_SPEND: 'مطابقة عشوائية',
  CALL_MINUTE_CHARGE: 'رسوم دقائق مكالمة',
  BOOST_PROFILE: 'تعزيز الظهور في المنصة'
});

export const SERVER_GIFT_CATALOG = Object.freeze({
  gift_rose: Object.freeze({ giftId: 'gift_rose', name: 'وردة حمراء 🌹', coinsCost: 5 }),
  gift_heart: Object.freeze({ giftId: 'gift_heart', name: 'قلب نيون 💖', coinsCost: 15 }),
  gift_crown: Object.freeze({ giftId: 'gift_crown', name: 'تاج الملوك 👑', coinsCost: 50 }),
  gift_diamond: Object.freeze({ giftId: 'gift_diamond', name: 'ماسة زرقاء 💎', coinsCost: 100 }),
  gift_car: Object.freeze({ giftId: 'gift_car', name: 'سيارة رياضية 🏎️', coinsCost: 300 }),
  gift_rocket: Object.freeze({ giftId: 'gift_rocket', name: 'صاروخ الفضاء 🚀', coinsCost: 600 })
});

export const SERVER_MISSION_CATALOG = Object.freeze({
  mission_stream: Object.freeze({
    missionId: 'mission_stream',
    rewardCoins: 10,
    requiredActivity: 'STREAM_WATCHED',
    minCount: 1,
    description: 'مشاهدة البث المباشر'
  }),
  mission_matches: Object.freeze({
    missionId: 'mission_matches',
    rewardCoins: 10,
    requiredActivity: 'MATCH_COMPLETED',
    minCount: 1,
    description: 'إجراء مطابقة عشوائية'
  }),
  mission_gifts: Object.freeze({
    missionId: 'mission_gifts',
    rewardCoins: 5,
    requiredActivity: 'GIFT_SENT',
    minCount: 1,
    description: 'إرسال هدية تشجيعية'
  })
});

export const DEFAULT_ECONOMY_CONFIG = Object.freeze({
  hostCommissionPercent: 50,
  agentCommissionPercent: 20,
  platformCommissionPercent: 30,
  minPlatformPercent: MIN_PLATFORM_PERCENT,
  coinsPerUsd: COINS_PER_USD,
  minPayoutCoins: MIN_PAYOUT_COINS,
  payoutHoldHours: PAYOUT_HOLD_HOURS,
  dailyFreeRewardCapCoins: DAILY_FREE_REWARD_CAP_COINS
});

function generateReferralCode(uid) {
  const hash = crypto.createHash('sha256').update(String(uid)).digest('hex').slice(0, 6).toUpperCase();
  return `AYER-${hash}`;
}

function hashDeviceId(deviceId) {
  const raw = String(deviceId || '').trim();
  if (!raw) return null;
  return crypto.createHash('sha256').update(raw, 'utf8').digest('hex');
}

export function normalizeDigits(input) {
  return String(input || '').replace(/[٠-٩]/g, (ch) => String(ch.charCodeAt(0) - 0x0660)).replace(/[۰-۹]/g, (ch) => String(ch.charCodeAt(0) - 0x06F0));
}

export function computeExactAgeFromDob(dobInput, nowMs = Date.now()) {
  const normalized = normalizeDigits(String(dobInput || '').trim());
  const match = normalized.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) {
    throw new CallEngineError('INVALID_DOB', 'يرجى إدخال تاريخ الميلاد بصيغة صحيحة (YYYY-MM-DD)', 400);
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (year < 1900 || month < 1 || month > 12 || day < 1 || day > 31) {
    throw new CallEngineError('INVALID_DOB', 'تاريخ الميلاد المدخل غير صالح.', 400);
  }
  const dobDate = new Date(Date.UTC(year, month - 1, day, 0, 0, 0, 0));
  if (
    dobDate.getUTCFullYear() !== year ||
    dobDate.getUTCMonth() !== month - 1 ||
    dobDate.getUTCDate() !== day
  ) {
    throw new CallEngineError('INVALID_DOB', 'اليوم غير مطابق للشهر المحدد.', 400);
  }
  if (dobDate.getTime() > nowMs) {
    throw new CallEngineError('INVALID_DOB', 'لا يمكن أن يكون تاريخ الميلاد في المستقبل.', 400);
  }
  const nowDate = new Date(nowMs);
  let ageYears = nowDate.getUTCFullYear() - year;
  const nowMonth = nowDate.getUTCMonth() + 1;
  const nowDay = nowDate.getUTCDate();
  if (nowMonth < month || (nowMonth === month && nowDay < day)) {
    ageYears -= 1;
  }
  return {
    normalizedDob: normalized,
    ageYears,
    isAdult: ageYears >= 18
  };
}

const VALID_USER_STATUSES = new Set(['ACTIVE', 'SUSPENDED', 'BANNED']);
const VALID_ADMIN_ROLES = new Set(['NONE', 'SUPER_ADMIN', 'FINANCE_ADMIN', 'MODERATION_ADMIN', 'AGENCY_MANAGER']);

function resolveAdminRoleSync(rawRole, isAdminFlag = false) {
  const upper = String(rawRole || '').trim().toUpperCase();
  if (VALID_ADMIN_ROLES.has(upper) && upper !== 'NONE') return upper;
  if (upper === 'NONE' && rawRole !== undefined && rawRole !== null && !isAdminFlag) return 'NONE';
  return isAdminFlag ? 'SUPER_ADMIN' : 'NONE';
}

export function assertAccountActive(user) {
  if (!user) {
    throw new CallEngineError('USER_NOT_FOUND', 'الحساب غير موجود', 404);
  }
  if (user.deletedAt) {
    throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);
  }
  if (user.status === 'BANNED') {
    throw new CallEngineError('ACCOUNT_BANNED', 'تم حظر هذا الحساب نهائياً لمخالفة سياسات المنصة.', 403, {
      status: 'BANNED',
      reason: user.statusReason || null
    });
  }
  if (user.status === 'SUSPENDED') {
    throw new CallEngineError('ACCOUNT_SUSPENDED', 'تم إيقاف هذا الحساب مؤقتاً من قِبل الإدارة.', 403, {
      status: 'SUSPENDED',
      reason: user.statusReason || null
    });
  }
}

export function assertAdultVerified(user) {
  assertAccountActive(user);
  if (user.underageLocked) {
    throw new CallEngineError('UNDERAGE_LOCKED', 'تم قفل هذا الحساب لعدم استيفاء الحد الأدنى للعمر (+18 سنة).', 403);
  }
  if (!user.ageVerified && !user.isAdmin) {
    throw new CallEngineError('AGE_VERIFICATION_REQUIRED', 'يلزم توثيق العمر (+18) واعتماده قبل استخدام هذه الميزة.', 403);
  }
}

function validateAndResolveGift({ giftId, coinsCost }) {
  const cleanGiftId = String(giftId || '').trim();
  const item = SERVER_GIFT_CATALOG[cleanGiftId];
  if (!item) {
    throw new CallEngineError('UNKNOWN_GIFT_ID', 'معرف الهدية غير معتمد في كتالوج الخادم', 400);
  }
  if (coinsCost !== undefined && coinsCost !== null && Number(coinsCost) !== item.coinsCost) {
    throw new CallEngineError('GIFT_PRICE_MISMATCH', 'سعر الهدية لا يطابق السعر الرسمي المعتمد على الخادم', 400, {
      expectedCoinsCost: item.coinsCost,
      receivedCoinsCost: Number(coinsCost)
    });
  }
  return item;
}

function validateCommissionPair(hostPct, agentPct) {
  const h = Number(hostPct);
  const a = Number(agentPct);
  if (!Number.isInteger(h) || h < 10 || h > 75) {
    throw new CallEngineError('INVALID_COMMISSION', 'نسبة عمولة المضيف يجب أن تكون بين 10% و 75%', 400);
  }
  if (!Number.isInteger(a) || a < 5 || a > 50) {
    throw new CallEngineError('INVALID_COMMISSION', 'نسبة عمولة الوكيل يجب أن تكون بين 5% و 50%', 400);
  }
  if (h + a > 100 - MIN_PLATFORM_PERCENT) {
    throw new CallEngineError(
      'INVALID_COMMISSION_SPLIT',
      `مجموع عمولة المضيف والوكيل (${h + a}%) يتجاوز الحد الأقصى المسموح (${100 - MIN_PLATFORM_PERCENT}%) لضمان حصة المنصة (${MIN_PLATFORM_PERCENT}% على الأقل).`,
      400
    );
  }
  return {
    hostCommissionPercent: h,
    agentCommissionPercent: a,
    platformCommissionPercent: 100 - h - a
  };
}

function computeRevenueSplit({ cost, hostCommissionPercent, agentCommissionPercent, hasAgent }) {
  const safeHostPct = Math.max(10, Math.min(75, Number(hostCommissionPercent || 50)));
  const maxAgentPct = Math.max(0, 100 - MIN_PLATFORM_PERCENT - safeHostPct);
  const safeAgentPct = hasAgent ? Math.max(0, Math.min(maxAgentPct, Math.min(50, Number(agentCommissionPercent || 20)))) : 0;

  const hostShare = Math.floor((cost * safeHostPct) / 100);
  const agentShare = hasAgent ? Math.floor((cost * safeAgentPct) / 100) : 0;
  const platformShare = cost - hostShare - agentShare;
  return { hostShare, agentShare, platformShare };
}

function sanitizeSpendDescription(entryType, rawDescription) {
  const base = SPEND_DESCRIPTIONS[entryType] || 'خصم نقاط';
  const suffix = String(rawDescription || '').replace(/[\r\n\t]+/g, ' ').trim().slice(0, 60);
  return suffix ? `${base} — ${suffix}` : base;
}

function createEphemeralKey() {
  return crypto.randomBytes(32);
}

export class MemoryLedgerStore {
  constructor({
    nowProvider = () => Date.now(),
    env = process.env,
    payoutHoldMs = PAYOUT_HOLD_MS,
    ageVerificationProvider = null
  } = {}) {
    this.mode = 'memory';
    this.now = nowProvider;
    this.payoutHoldMs = payoutHoldMs;
    this.ageVerificationProvider = ageVerificationProvider;
    this.bankKey = resolveBankEncryptionKey(env, createEphemeralKey());
    this.users = new Map();
    this.wallets = new Map();
    this.ledger = [];
    this.idempotencyKeys = new Set();
    this.holds = new Map(); // holdId -> hold
    this.holdIdempotency = new Map(); // idempotencyKey -> holdId
    this.iapTokens = new Map();
    this.agencies = new Map();
    this.agencyMemberships = new Map();
    this.payoutRequests = new Map();
    this.ageVerifications = new Map();
    this.underageDeviceLocks = new Map(); // deviceHash -> { uid, dobSubmitted, reason, lockedAtMs }
    this.moderationReports = new Map();
    this.moderationSamples = new Map();
    this.moderationDailyUsage = new Map(); // dayKey -> { dayKey, sampleCount, estimatedCostUsd }
    this.blocks = new Map(); // blockerUid -> Set<blockedUid>
    this.auditLogs = [];
    this.economyConfig = { ...DEFAULT_ECONOMY_CONFIG };
    this.deviceDailyClaims = new Map(); // `${deviceHash}:${dayKey}` -> { lastDailyClaimMs, freeCoinsClaimed }
    this.userDailyFreeCoins = new Map(); // `${uid}:${dayKey}` -> number
    this.dailyActivityCounters = new Map(); // `${uid}:${dayKey}:${activityType}` -> number
    this._lockChain = Promise.resolve();
  }

  async init() {
    return { ok: true, mode: this.mode };
  }

  async _withLock(fn) {
    const run = this._lockChain.then(() => fn());
    this._lockChain = run.catch(() => {});
    return run;
  }

  _ensureUserSync({ uid, displayName = '', email = '' }) {
    const safeUid = String(uid || '').trim();
    if (!safeUid) {
      throw new CallEngineError('INVALID_UID', 'معرف المستخدم مطلوب', 400);
    }
    let user = this.users.get(safeUid);
    if (!user) {
      const isTreasury = safeUid === PLATFORM_TREASURY_UID;
      user = {
        uid: safeUid,
        displayName: isTreasury ? 'خزينة المنصة' : (displayName || 'مستخدم Ayer'),
        email: email || '',
        photoUrl: null,
        bio: '',
        country: '',
        countryCode: '',
        gender: '',
        dob: null,
        ageYears: 0,
        ageVerified: false,
        ageVerificationStatus: 'UNVERIFIED',
        isAdmin: isTreasury,
        adminRole: isTreasury ? 'SUPER_ADMIN' : 'NONE',
        status: 'ACTIVE',
        statusReason: null,
        statusUpdatedAtMs: null,
        statusUpdatedBy: null,
        underageLocked: false,
        underageLockedReason: null,
        underageLockedAtMs: null,
        isAgent: false,
        isHost: false,
        hostStatus: 'NONE',
        agentCommissionPercent: this.economyConfig.agentCommissionPercent,
        hostCommissionPercent: this.economyConfig.hostCommissionPercent,
        agencyId: '',
        agencyName: '',
        referralCode: generateReferralCode(safeUid),
        profileVisibility: 'PUBLIC',
        directMessagePermission: 'EVERYONE',
        acceptedTermsAt: null,
        acceptedPrivacyAt: null,
        deletedAt: null,
        createdAtMs: this.now(),
        updatedAtMs: this.now()
      };
      this.users.set(safeUid, user);
    } else {
      if (displayName && !user.displayName) user.displayName = displayName;
      if (email && !user.email) user.email = email;
    }

    if (!this.wallets.has(safeUid)) {
      this.wallets.set(safeUid, {
        uid: safeUid,
        pointsBalance: 0,
        debtCoins: 0,
        heldCoins: 0,
        hostEarningsCoins: 0,
        subAgentsEarningsCoins: 0,
        reservedPayoutCoins: 0,
        platformRevenueCoins: 0,
        totalAccumulatedPoints: 0,
        totalPointsSpent: 0,
        lastDailyClaimMs: 0,
        updatedAtMs: this.now()
      });
    }
    return { user: this.users.get(safeUid), wallet: this.wallets.get(safeUid) };
  }

  _appendLedgerSync({ uid, entryType, bucket = 'POINTS', delta, balanceAfter, idempotencyKey, referenceId = null, description, metadata = {} }) {
    if (this.idempotencyKeys.has(idempotencyKey)) {
      return null;
    }
    this.idempotencyKeys.add(idempotencyKey);
    const entry = {
      id: this.ledger.length + 1,
      entryUuid: crypto.randomUUID(),
      uid,
      entryType,
      bucket,
      delta,
      balanceAfter,
      idempotencyKey,
      referenceId,
      description,
      metadata,
      createdAtMs: this.now()
    };
    this.ledger.unshift(entry);
    return entry;
  }

  _auditSync({ actorUid, action, targetId = null, details = {} }) {
    this.auditLogs.unshift({
      id: this.auditLogs.length + 1,
      actorUid,
      action,
      targetId,
      details,
      createdAtMs: this.now()
    });
  }

  _creditPlatformRevenueSync({ amount, idempotencyKey, referenceId = null, description, metadata = {} }) {
    if (amount <= 0) return null;
    const { wallet: platWallet } = this._ensureUserSync({ uid: PLATFORM_TREASURY_UID });
    platWallet.platformRevenueCoins += amount;
    platWallet.updatedAtMs = this.now();
    return this._appendLedgerSync({
      uid: PLATFORM_TREASURY_UID,
      entryType: 'PLATFORM_REVENUE',
      bucket: 'PLATFORM_REVENUE',
      delta: amount,
      balanceAfter: platWallet.platformRevenueCoins,
      idempotencyKey,
      referenceId,
      description,
      metadata
    });
  }

  _recordActivitySync({ uid, activityType, count = 1 }) {
    const dayKey = new Date(this.now()).toISOString().slice(0, 10);
    const key = `${uid}:${dayKey}:${activityType}`;
    const next = (this.dailyActivityCounters.get(key) || 0) + Number(count || 1);
    this.dailyActivityCounters.set(key, next);
    return next;
  }

  _publicPayoutView(reqItem) {
    const masked = reqItem.ibanMasked || maskAccountNumber(reqItem.ibanOrAccount);
    return {
      id: reqItem.id,
      uid: reqItem.uid,
      displayName: reqItem.displayName,
      amountCoins: reqItem.amountCoins,
      usdEquivalentCents: reqItem.usdEquivalentCents,
      payoutMethod: reqItem.payoutMethod,
      bankAccountHolder: reqItem.bankAccountHolder,
      bankName: reqItem.bankName,
      ibanOrAccount: masked,
      ibanMasked: masked,
      fromSubAgentsCoins: reqItem.fromSubAgentsCoins,
      fromHostCoins: reqItem.fromHostCoins,
      earliestReviewAtMs: reqItem.earliestReviewAtMs,
      status: reqItem.status,
      reviewerUid: reqItem.reviewerUid,
      reviewerNote: reqItem.reviewerNote,
      paymentReference: reqItem.paymentReference,
      createdAtMs: reqItem.createdAtMs,
      reviewedAtMs: reqItem.reviewedAtMs
    };
  }

  async recordServerActivity({ uid, activityType, count = 1 }) {
    return this._withLock(() => {
      this._ensureUserSync({ uid });
      const totalToday = this._recordActivitySync({ uid, activityType, count });
      return { uid, activityType, countToday: totalToday };
    });
  }

  async getEconomyConfig() {
    return this._withLock(() => ({ ...this.economyConfig }));
  }

  async updateEconomyConfig({ reviewerUid, hostCommissionPercent, agentCommissionPercent }) {
    return this._withLock(() => {
      const nextHost = hostCommissionPercent !== undefined ? hostCommissionPercent : this.economyConfig.hostCommissionPercent;
      const nextAgent = agentCommissionPercent !== undefined ? agentCommissionPercent : this.economyConfig.agentCommissionPercent;
      const split = validateCommissionPair(nextHost, nextAgent);
      this.economyConfig = {
        ...this.economyConfig,
        ...split
      };
      this._auditSync({
        actorUid: reviewerUid,
        action: 'UPDATE_ECONOMY_CONFIG',
        targetId: 'economy_config',
        details: split
      });
      return { ...this.economyConfig };
    });
  }

  async ensureUser(input) {
    return this._withLock(() => {
      const { user, wallet } = this._ensureUserSync(input);
      return { user: { ...user }, wallet: { ...wallet } };
    });
  }

  async getUserGateState(uid) {
    return this._withLock(() => {
      const { user, wallet } = this._ensureUserSync({ uid });
      return { user: { ...user }, wallet: { ...wallet } };
    });
  }

  async assertUserCanOperate(uid, { requireAgeVerified = true } = {}) {
    return this._withLock(() => {
      const { user, wallet } = this._ensureUserSync({ uid });
      if (requireAgeVerified) {
        assertAdultVerified(user);
      } else {
        assertAccountActive(user);
      }
      return { user: { ...user }, wallet: { ...wallet } };
    });
  }

  async getIapTransaction(purchaseToken) {
    return this._withLock(() => {
      const tx = this.iapTokens.get(purchaseToken);
      return tx ? { ...tx } : null;
    });
  }

  async markIapAcknowledged(purchaseToken) {
    return this._withLock(() => {
      const tx = this.iapTokens.get(purchaseToken);
      if (tx) {
        tx.acknowledged = true;
      }
      return Boolean(tx);
    });
  }

  async getWalletSummary(uid, limit = 50) {
    return this._withLock(() => {
      const { user, wallet } = this._ensureUserSync({ uid });
      const entries = this.ledger
        .filter((e) => e.uid === uid)
        .slice(0, Math.max(1, Math.min(200, Number(limit) || 50)))
        .map((e) => ({
          ...e,
          type: e.entryType,
          amount: e.delta,
          timestamp: e.createdAtMs
        }));
      return {
        user: { ...user },
        wallet: { ...wallet },
        transactions: entries
      };
    });
  }

  async creditPlayPurchase({
    uid,
    displayName = '',
    email = '',
    packageId,
    productId,
    coinsCredited,
    priceUsd = 0,
    purchaseToken,
    orderId = null,
    purchaseState = 0,
    acknowledged = true,
    obfuscatedExternalAccountId = null,
    verificationSource = 'google_play_androidpublisher_v3'
  }) {
    return this._withLock(() => {
      if (!purchaseToken) {
        throw new CallEngineError('MISSING_PURCHASE_TOKEN', 'رمز شراء Google Play مطلوب', 400);
      }
      if (this.iapTokens.has(purchaseToken)) {
        const existing = this.iapTokens.get(purchaseToken);
        if (existing.uid === uid) {
          const { wallet } = this._ensureUserSync({ uid });
          return {
            wallet: { ...wallet },
            creditedCoins: existing.coinsCredited,
            duplicate: true,
            alreadyCredited: true
          };
        } else {
          throw new CallEngineError('DUPLICATE_PURCHASE_TOKEN_OTHER_USER', 'رمز الشراء مسجل لحساب مستخدم آخر', 409);
        }
      }
      const coins = Number(coinsCredited);
      if (!Number.isInteger(coins) || coins <= 0) {
        throw new CallEngineError('INVALID_COINS_AMOUNT', 'قيمة النقاط غير صالحة', 400);
      }

      const { user, wallet } = this._ensureUserSync({ uid, displayName, email });
      assertAccountActive(user);

      const nowMs = this.now();
      this.iapTokens.set(purchaseToken, {
        purchaseToken,
        orderId,
        uid,
        packageId,
        productId,
        coinsCredited: coins,
        amountMicros: Math.round(Number(priceUsd || 0) * 1_000_000),
        currencyCode: 'USD',
        purchaseState,
        acknowledged,
        obfuscatedExternalAccountId,
        isVoided: false,
        voidedAtMs: null,
        voidedReason: null,
        verificationSource,
        verifiedAtMs: nowMs
      });

      const currentDebt = wallet.debtCoins || 0;
      const debtSettled = Math.min(currentDebt, coins);
      const coinsToCredit = coins - debtSettled;

      wallet.debtCoins = currentDebt - debtSettled;
      wallet.pointsBalance += coinsToCredit;
      wallet.totalAccumulatedPoints += coins;
      wallet.updatedAtMs = nowMs;

      if (debtSettled > 0) {
        this._appendLedgerSync({
          uid,
          entryType: 'PURCHASE_DEBT_SETTLEMENT',
          bucket: 'DEBT',
          delta: -debtSettled,
          balanceAfter: wallet.debtCoins,
          idempotencyKey: `debt_settle_${purchaseToken}`,
          referenceId: orderId || purchaseToken,
          description: `سداد مديونية سابقة من شحن Google Play (-${debtSettled} نقطة دين)`,
          metadata: { purchaseToken, orderId, debtSettled, remainingDebt: wallet.debtCoins }
        });
      }

      const entry = this._appendLedgerSync({
        uid,
        entryType: 'PURCHASE',
        bucket: 'POINTS',
        delta: coinsToCredit,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: `iap_${purchaseToken}`,
        referenceId: orderId || purchaseToken,
        description: debtSettled > 0
          ? `شحن Google Play (+${coins} نقطة، سُدد منها ${debtSettled} دين، والمضاف ${coinsToCredit})`
          : `شحن Google Play (+${coins} نقطة)`,
        metadata: { packageId, productId, verificationSource, debtSettled }
      });

      this._auditSync({
        actorUid: uid,
        action: 'IAP_PURCHASE_CREDITED',
        targetId: packageId,
        details: { coinsCredited: coins, debtSettled, orderId }
      });

      return {
        wallet: { ...wallet },
        entry,
        creditedCoins: coins,
        debtSettled,
        duplicate: false,
        alreadyCredited: false
      };
    });
  }

  async holdCoins({ uid, amount, purpose = 'MATCH_HOLD', referenceId = null, idempotencyKey }) {
    return this._withLock(() => {
      const holdAmount = Number(amount);
      if (!Number.isInteger(holdAmount) || holdAmount <= 0) {
        throw new CallEngineError('INVALID_AMOUNT', 'قيمة الحجز يجب أن تكون عدداً صحيحاً موجباً', 400);
      }
      const key = String(idempotencyKey || `hold_${uid}_${crypto.randomUUID()}`);
      const existingHoldId = this.holdIdempotency.get(key);
      if (existingHoldId && this.holds.has(existingHoldId)) {
        const existingHold = this.holds.get(existingHoldId);
        const { wallet } = this._ensureUserSync({ uid: existingHold.uid });
        return { hold: { ...existingHold }, wallet: { ...wallet }, duplicate: true };
      }

      const { user, wallet } = this._ensureUserSync({ uid });
      assertAdultVerified(user);
      if ((wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن حجز النقاط لوجود مديونية مستردات على الحساب.', 403, {
          debtCoins: wallet.debtCoins
        });
      }
      if (wallet.pointsBalance < holdAmount) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لحجز العملية', 402, {
          balance: wallet.pointsBalance,
          required: holdAmount
        });
      }

      const nowMs = this.now();
      wallet.pointsBalance -= holdAmount;
      wallet.heldCoins += holdAmount;
      wallet.updatedAtMs = nowMs;

      const holdId = `hold_${crypto.randomUUID()}`;
      const hold = {
        holdId,
        uid,
        purpose: String(purpose || 'MATCH_HOLD'),
        referenceId,
        amountCoins: holdAmount,
        capturedCoins: 0,
        releasedCoins: 0,
        status: 'ACTIVE',
        idempotencyKey: key,
        createdAtMs: nowMs,
        settledAtMs: null
      };
      this.holds.set(holdId, hold);
      this.holdIdempotency.set(key, holdId);

      this._appendLedgerSync({
        uid,
        entryType: 'HOLD_RESERVE',
        bucket: 'POINTS',
        delta: -holdAmount,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: `hold_res_${key}`,
        referenceId: holdId,
        description: `حجز مؤقت (${purpose}) (-${holdAmount} نقطة)`,
        metadata: { holdId, purpose, referenceId }
      });

      return { hold: { ...hold }, wallet: { ...wallet }, duplicate: false };
    });
  }

  async captureHold({
    holdId,
    captureAmount = null,
    entryType = 'MATCH_SPEND',
    description = null,
    creditPlatformRevenue = true,
    idempotencyKey = null
  }) {
    return this._withLock(() => {
      const hold = this.holds.get(holdId);
      if (!hold) {
        throw new CallEngineError('HOLD_NOT_FOUND', 'سجل الحجز غير موجود', 404);
      }
      const capKey = String(idempotencyKey || `capture_${holdId}`);
      if (hold.status !== 'ACTIVE') {
        if (hold.status === 'CAPTURED' && this.idempotencyKeys.has(`cap_mark_${capKey}`)) {
          const { wallet } = this._ensureUserSync({ uid: hold.uid });
          return { hold: { ...hold }, wallet: { ...wallet }, duplicate: true };
        }
        throw new CallEngineError('HOLD_ALREADY_SETTLED', 'تمت تسوية هذا الحجز مسبقاً', 409);
      }

      const remaining = hold.amountCoins - hold.capturedCoins - hold.releasedCoins;
      const toCapture = captureAmount === null || captureAmount === undefined ? remaining : Number(captureAmount);
      if (!Number.isInteger(toCapture) || toCapture < 0 || toCapture > remaining) {
        throw new CallEngineError('INVALID_CAPTURE_AMOUNT', 'مبلغ الالتقاط يتجاوز الرصيد المحجوز', 400);
      }
      const toRelease = remaining - toCapture;

      const { wallet } = this._ensureUserSync({ uid: hold.uid });
      if (wallet.heldCoins < remaining) {
        throw new CallEngineError('HELD_BALANCE_CORRUPT', 'رصيد الحجز غير متطابق', 409);
      }

      const nowMs = this.now();
      wallet.heldCoins -= remaining;
      wallet.pointsBalance += toRelease;
      wallet.totalPointsSpent += toCapture;
      wallet.updatedAtMs = nowMs;

      hold.capturedCoins += toCapture;
      hold.releasedCoins += toRelease;
      hold.status = 'CAPTURED';
      hold.settledAtMs = nowMs;
      this.idempotencyKeys.add(`cap_mark_${capKey}`);

      if (toRelease > 0) {
        this._appendLedgerSync({
          uid: hold.uid,
          entryType: 'HOLD_RELEASE',
          bucket: 'POINTS',
          delta: toRelease,
          balanceAfter: wallet.pointsBalance,
          idempotencyKey: `${capKey}_rel`,
          referenceId: holdId,
          description: `فك جزء غير مستخدم من الحجز (+${toRelease} نقطة)`,
          metadata: { holdId }
        });
      }

      if (toCapture > 0) {
        this._appendLedgerSync({
          uid: hold.uid,
          entryType,
          bucket: 'HELD_POINTS',
          delta: -toCapture,
          balanceAfter: wallet.heldCoins,
          idempotencyKey: `${capKey}_cap`,
          referenceId: hold.referenceId || holdId,
          description: description || `تأكيد خصم محجوز (${toCapture} نقطة)`,
          metadata: { holdId, purpose: hold.purpose }
        });

        if (creditPlatformRevenue) {
          this._creditPlatformRevenueSync({
            amount: toCapture,
            idempotencyKey: `${capKey}_plat`,
            referenceId: hold.referenceId || holdId,
            description: `إيراد منصة من التزام محجوز (${entryType}) (+${toCapture} نقطة)`,
            metadata: { uid: hold.uid, holdId, entryType }
          });
        }
      }

      return { hold: { ...hold }, wallet: { ...wallet }, duplicate: false };
    });
  }

  async releaseHold({ holdId, reason = 'HOLD_RELEASED', idempotencyKey = null }) {
    return this._withLock(() => {
      const hold = this.holds.get(holdId);
      if (!hold) {
        throw new CallEngineError('HOLD_NOT_FOUND', 'سجل الحجز غير موجود', 404);
      }
      if (hold.status === 'RELEASED') {
        const { wallet } = this._ensureUserSync({ uid: hold.uid });
        return { hold: { ...hold }, wallet: { ...wallet }, duplicate: true };
      }
      if (hold.status !== 'ACTIVE') {
        throw new CallEngineError('HOLD_ALREADY_SETTLED', 'لا يمكن فك حجز تم التقاطه مسبقاً', 409);
      }

      const remaining = hold.amountCoins - hold.capturedCoins - hold.releasedCoins;
      const { wallet } = this._ensureUserSync({ uid: hold.uid });
      const nowMs = this.now();
      wallet.heldCoins = Math.max(0, wallet.heldCoins - remaining);
      wallet.pointsBalance += remaining;
      wallet.updatedAtMs = nowMs;

      hold.releasedCoins += remaining;
      hold.status = 'RELEASED';
      hold.settledAtMs = nowMs;

      const relKey = String(idempotencyKey || `rel_${holdId}`);
      this._appendLedgerSync({
        uid: hold.uid,
        entryType: 'HOLD_RELEASE',
        bucket: 'POINTS',
        delta: remaining,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: relKey,
        referenceId: holdId,
        description: `استرجاع حجز غير مستخدم (${reason}) (+${remaining} نقطة)`,
        metadata: { holdId, reason }
      });

      return { hold: { ...hold }, wallet: { ...wallet }, duplicate: false };
    });
  }

  async spendCoins({ uid, amount, entryType = 'MATCH_SPEND', description, idempotencyKey, referenceId = null, metadata = {} }) {
    return this._withLock(() => {
      if (!ALLOWED_CLIENT_SPEND_ENTRY_TYPES.has(entryType)) {
        throw new CallEngineError('INVALID_ENTRY_TYPE', 'نوع عملية الخصم غير مسموح به', 400, { entryType });
      }
      const spendAmount = Number(amount);
      if (!Number.isInteger(spendAmount) || spendAmount <= 0) {
        throw new CallEngineError('INVALID_AMOUNT', 'قيمة الخصم يجب أن تكون عدداً صحيحاً موجباً', 400);
      }
      const key = String(idempotencyKey || `spend_${uid}_${crypto.randomUUID()}`);
      if (this.idempotencyKeys.has(key)) {
        const existing = this.ledger.find((e) => e.idempotencyKey === key);
        const { wallet } = this._ensureUserSync({ uid });
        return { wallet: { ...wallet }, entry: existing ? { ...existing } : null, duplicate: true };
      }

      const { user, wallet } = this._ensureUserSync({ uid });
      assertAdultVerified(user);
      if ((wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن استخدام النقاط لوجود مديونية مستردات على الحساب. يرجى سداد المديونية أولاً.', 403, {
          debtCoins: wallet.debtCoins
        });
      }
      if (wallet.pointsBalance < spendAmount) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لإتمام العملية', 402, {
          balance: wallet.pointsBalance,
          required: spendAmount
        });
      }

      wallet.pointsBalance -= spendAmount;
      wallet.totalPointsSpent += spendAmount;
      wallet.updatedAtMs = this.now();

      const safeDescription = sanitizeSpendDescription(entryType, description);
      const entry = this._appendLedgerSync({
        uid,
        entryType,
        bucket: 'POINTS',
        delta: -spendAmount,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: key,
        referenceId,
        description: safeDescription,
        metadata
      });

      this._creditPlatformRevenueSync({
        amount: spendAmount,
        idempotencyKey: `${key}_plat`,
        referenceId,
        description: `إيراد منصة (${entryType}) (+${spendAmount} نقطة)`,
        metadata: { uid, entryType }
      });

      return { wallet: { ...wallet }, entry, duplicate: false };
    });
  }

  async claimDailyBonus({ uid, deviceId = null }) {
    return this._withLock(() => {
      const { user, wallet } = this._ensureUserSync({ uid });
      assertAccountActive(user);
      const nowMs = this.now();
      if (wallet.lastDailyClaimMs > 0 && nowMs - wallet.lastDailyClaimMs < DAILY_CLAIM_COOLDOWN_MS) {
        throw new CallEngineError('DAILY_BONUS_COOLDOWN', 'تم استلام المكافأة اليومية بالفعل. حاول بعد 24 ساعة.', 429, {
          nextAvailableAtMs: wallet.lastDailyClaimMs + DAILY_CLAIM_COOLDOWN_MS
        });
      }

      const dayKey = new Date(nowMs).toISOString().slice(0, 10);
      const deviceHash = hashDeviceId(deviceId);
      if (deviceHash) {
        const devRec = this.deviceDailyClaims.get(`${deviceHash}:${dayKey}`) || { lastDailyClaimMs: 0, freeCoinsClaimed: 0 };
        if (devRec.lastDailyClaimMs > 0 && nowMs - devRec.lastDailyClaimMs < DAILY_CLAIM_COOLDOWN_MS) {
          throw new CallEngineError('DAILY_BONUS_DEVICE_COOLDOWN', 'تم استلام المكافأة اليومية على هذا الجهاز بالفعل اليوم.', 429, {
            nextAvailableAtMs: devRec.lastDailyClaimMs + DAILY_CLAIM_COOLDOWN_MS
          });
        }
        if (devRec.freeCoinsClaimed + DAILY_BONUS_COINS > this.economyConfig.dailyFreeRewardCapCoins) {
          throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية على هذا الجهاز.', 429);
        }
      }

      const userFreeToday = this.userDailyFreeCoins.get(`${uid}:${dayKey}`) || 0;
      if (userFreeToday + DAILY_BONUS_COINS > this.economyConfig.dailyFreeRewardCapCoins) {
        throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية لهذا الحساب.', 429);
      }

      wallet.pointsBalance += DAILY_BONUS_COINS;
      wallet.totalAccumulatedPoints += DAILY_BONUS_COINS;
      wallet.lastDailyClaimMs = nowMs;
      wallet.updatedAtMs = nowMs;

      this.userDailyFreeCoins.set(`${uid}:${dayKey}`, userFreeToday + DAILY_BONUS_COINS);
      if (deviceHash) {
        const devRec = this.deviceDailyClaims.get(`${deviceHash}:${dayKey}`) || { lastDailyClaimMs: 0, freeCoinsClaimed: 0 };
        this.deviceDailyClaims.set(`${deviceHash}:${dayKey}`, {
          lastDailyClaimMs: nowMs,
          freeCoinsClaimed: devRec.freeCoinsClaimed + DAILY_BONUS_COINS
        });
      }

      const entry = this._appendLedgerSync({
        uid,
        entryType: 'DAILY_FREE',
        bucket: 'POINTS',
        delta: DAILY_BONUS_COINS,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: `daily_${uid}_${dayKey}_${nowMs}`,
        description: `مكافأة الدخول اليومي (+${DAILY_BONUS_COINS} نقطة)`,
        metadata: { dayKey }
      });

      return { wallet: { ...wallet }, entry, creditedCoins: DAILY_BONUS_COINS };
    });
  }

  async claimMissionReward({ uid, missionId, deviceId = null }) {
    return this._withLock(() => {
      const cleanId = String(missionId || '').trim();
      const mission = SERVER_MISSION_CATALOG[cleanId];
      if (!mission) {
        throw new CallEngineError('INVALID_MISSION', 'معرف المهمة غير صالح أو غير موثق من الخادم', 400);
      }
      const { user, wallet } = this._ensureUserSync({ uid });
      assertAccountActive(user);

      const nowMs = this.now();
      const dayKey = new Date(nowMs).toISOString().slice(0, 10);
      const idempotencyKey = `mission_${uid}_${dayKey}_${cleanId}`;
      if (this.idempotencyKeys.has(idempotencyKey)) {
        throw new CallEngineError('MISSION_ALREADY_CLAIMED', 'تم استلام مكافأة هذه المهمة اليوم بالفعل.', 409);
      }

      // A1: Verify authoritative server-side activity occurred today
      const activityCount = this.dailyActivityCounters.get(`${uid}:${dayKey}:${mission.requiredActivity}`) || 0;
      if (activityCount < mission.minCount) {
        throw new CallEngineError(
          'MISSION_NOT_VERIFIED',
          `لم يتم تسجيل نشاط فعلي كافٍ على الخادم لإكمال مهمة (${mission.description}).`,
          403,
          { requiredActivity: mission.requiredActivity, recordedCount: activityCount }
        );
      }

      const rewardCoins = mission.rewardCoins;
      const userFreeToday = this.userDailyFreeCoins.get(`${uid}:${dayKey}`) || 0;
      if (userFreeToday + rewardCoins > this.economyConfig.dailyFreeRewardCapCoins) {
        throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية لهذا الحساب.', 429);
      }

      const deviceHash = hashDeviceId(deviceId);
      if (deviceHash) {
        const devRec = this.deviceDailyClaims.get(`${deviceHash}:${dayKey}`) || { lastDailyClaimMs: 0, freeCoinsClaimed: 0 };
        if (devRec.freeCoinsClaimed + rewardCoins > this.economyConfig.dailyFreeRewardCapCoins) {
          throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية على هذا الجهاز.', 429);
        }
        this.deviceDailyClaims.set(`${deviceHash}:${dayKey}`, {
          ...devRec,
          freeCoinsClaimed: devRec.freeCoinsClaimed + rewardCoins
        });
      }

      this.userDailyFreeCoins.set(`${uid}:${dayKey}`, userFreeToday + rewardCoins);

      wallet.pointsBalance += rewardCoins;
      wallet.totalAccumulatedPoints += rewardCoins;
      wallet.updatedAtMs = nowMs;

      const entry = this._appendLedgerSync({
        uid,
        entryType: 'MISSION_REWARD',
        bucket: 'POINTS',
        delta: rewardCoins,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey,
        referenceId: cleanId,
        description: `مكافأة مهمة يومية موثقة (${cleanId}) (+${rewardCoins} نقطة)`,
        metadata: { dayKey, missionId: cleanId }
      });

      return { wallet: { ...wallet }, entry, creditedCoins: rewardCoins };
    });
  }

  async applyForHost({ uid, agencyName = '' }) {
    return this._withLock(() => {
      const { user } = this._ensureUserSync({ uid });
      assertAdultVerified(user);
      if (!user.isHost && user.hostStatus !== 'APPROVED') {
        user.hostStatus = 'PENDING';
      }
      if (String(agencyName || '').trim()) {
        user.agencyName = String(agencyName).trim();
      }
      user.updatedAtMs = this.now();
      this._auditSync({
        actorUid: uid,
        action: 'HOST_APPLICATION_SUBMITTED',
        targetId: uid,
        details: { agencyName: user.agencyName }
      });
      return { user: { ...user } };
    });
  }

  async sendGiftAtomic({ senderUid, recipientUid, giftId, giftName = null, coinsCost = null, idempotencyKey, streamId = null }) {
    return this._withLock(() => {
      if (!recipientUid || senderUid === recipientUid) {
        throw new CallEngineError('INVALID_RECIPIENT', 'لا يمكن إرسال الهدية لهذا المستلم', 400);
      }

      const sender = this._ensureUserSync({ uid: senderUid });
      assertAdultVerified(sender.user);
      if ((sender.wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن إرسال الهدايا لوجود مديونية مستردات على الحساب.', 403, {
          debtCoins: sender.wallet.debtCoins
        });
      }

      const catalogGift = validateAndResolveGift({ giftId, coinsCost });
      const cost = catalogGift.coinsCost;
      const authoritativeGiftName = catalogGift.name;

      const key = String(idempotencyKey || `gift_${senderUid}_${crypto.randomUUID()}`);
      if (this.idempotencyKeys.has(key)) {
        const { wallet } = this._ensureUserSync({ uid: senderUid });
        return { senderWallet: { ...wallet }, duplicate: true, coinsCost: cost };
      }

      const recipient = this._ensureUserSync({ uid: recipientUid });

      if (sender.wallet.pointsBalance < cost) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لإرسال هذه الهدية', 402);
      }

      const membership = this.agencyMemberships.get(recipientUid);
      const hasAgent = Boolean(membership?.parentAgentUid && this.users.has(membership.parentAgentUid));
      const agent = hasAgent ? this._ensureUserSync({ uid: membership.parentAgentUid }) : null;

      const { hostShare, agentShare, platformShare } = computeRevenueSplit({
        cost,
        hostCommissionPercent: recipient.user.hostCommissionPercent || this.economyConfig.hostCommissionPercent,
        agentCommissionPercent: agent ? (agent.user.agentCommissionPercent || this.economyConfig.agentCommissionPercent) : 0,
        hasAgent
      });

      const nowMs = this.now();
      sender.wallet.pointsBalance -= cost;
      sender.wallet.totalPointsSpent += cost;
      sender.wallet.updatedAtMs = nowMs;

      recipient.wallet.hostEarningsCoins += hostShare;
      recipient.wallet.updatedAtMs = nowMs;

      const spendEntry = this._appendLedgerSync({
        uid: senderUid,
        entryType: 'GIFT_SENT',
        bucket: 'POINTS',
        delta: -cost,
        balanceAfter: sender.wallet.pointsBalance,
        idempotencyKey: key,
        referenceId: streamId || recipientUid,
        description: `إرسال هدية ${authoritativeGiftName} (-${cost} نقطة)`,
        metadata: { recipientUid, giftId: catalogGift.giftId, streamId }
      });

      this._appendLedgerSync({
        uid: recipientUid,
        entryType: 'GIFT_RECEIVE',
        bucket: 'HOST_EARNINGS',
        delta: hostShare,
        balanceAfter: recipient.wallet.hostEarningsCoins,
        idempotencyKey: `${key}_rx`,
        referenceId: streamId || senderUid,
        description: `أرباح هدية ${authoritativeGiftName} (+${hostShare} نقطة أرباح)`,
        metadata: { senderUid, giftId: catalogGift.giftId, streamId }
      });

      if (agent && agentShare > 0) {
        agent.wallet.subAgentsEarningsCoins += agentShare;
        agent.wallet.updatedAtMs = nowMs;
        this._appendLedgerSync({
          uid: agent.user.uid,
          entryType: 'AGENT_COMMISSION',
          bucket: 'AGENCY_EARNINGS',
          delta: agentShare,
          balanceAfter: agent.wallet.subAgentsEarningsCoins,
          idempotencyKey: `${key}_ag`,
          referenceId: recipientUid,
          description: `عمولة وكالة على هدية مضيف (+${agentShare} نقطة)`,
          metadata: { hostUid: recipientUid, giftId: catalogGift.giftId }
        });
      }

      if (platformShare > 0) {
        this._creditPlatformRevenueSync({
          amount: platformShare,
          idempotencyKey: `${key}_plat`,
          referenceId: streamId || recipientUid,
          description: `حصة المنصة من هدية ${authoritativeGiftName} (+${platformShare} نقطة)`,
          metadata: { senderUid, recipientUid, giftId: catalogGift.giftId }
        });
      }

      // Record server-side gift activity for A1 missions
      this._recordActivitySync({ uid: senderUid, activityType: 'GIFT_SENT', count: 1 });

      return {
        senderWallet: { ...sender.wallet },
        recipientEarningsAdded: hostShare,
        agentCommissionAdded: agentShare,
        platformRevenueAdded: platformShare,
        coinsCost: cost,
        giftName: authoritativeGiftName,
        entry: spendEntry,
        duplicate: false
      };
    });
  }

  async submitAgeVerification({ uid, displayName = '', email = '', dob, acceptedTermsAt, acceptedPrivacyAt, deviceId = null }) {
    const nowMs = this.now();
    const deviceHash = hashDeviceId(deviceId);

    const preCheck = await this._withLock(() => {
      const { user } = this._ensureUserSync({ uid, displayName, email });
      assertAccountActive(user);
      if (user.underageLocked || (deviceHash && this.underageDeviceLocks.has(deviceHash))) {
        throw new CallEngineError(
          'UNDERAGE_LOCKED',
          'عذراً، هذا الحساب أو الجهاز مقيد نهائياً لعدم استيفاء شرط العمر (+18 سنة).',
          403
        );
      }
      const ageCheck = computeExactAgeFromDob(dob, nowMs);
      if (!ageCheck.isAdult) {
        user.underageLocked = true;
        user.underageLockedReason = 'UNDERAGE_SUBMISSION';
        user.underageLockedAtMs = nowMs;
        user.ageVerified = false;
        user.ageVerificationStatus = 'REJECTED_UNDERAGE';
        user.dob = ageCheck.normalizedDob;
        user.ageYears = ageCheck.ageYears;
        user.updatedAtMs = nowMs;

        if (deviceHash) {
          this.underageDeviceLocks.set(deviceHash, {
            uid,
            dobSubmitted: ageCheck.normalizedDob,
            reason: 'UNDERAGE_SUBMISSION',
            lockedAtMs: nowMs
          });
        }
        this.ageVerifications.set(uid, {
          uid,
          displayName: user.displayName,
          email: user.email,
          dob: ageCheck.normalizedDob,
          ageYears: ageCheck.ageYears,
          status: 'REJECTED_UNDERAGE',
          reviewerUid: 'system_age_gate',
          rejectionReason: 'العمر المحسوب أقل من 18 عاماً',
          acceptedTermsAt: Number(acceptedTermsAt || nowMs),
          acceptedPrivacyAt: Number(acceptedPrivacyAt || nowMs),
          submittedAtMs: nowMs,
          reviewedAtMs: nowMs
        });
        this._auditSync({
          actorUid: uid,
          action: 'UNDERAGE_ATTEMPT_LOCKED',
          targetId: uid,
          details: { dob: ageCheck.normalizedDob, ageYears: ageCheck.ageYears, deviceLocked: Boolean(deviceHash) }
        });
        return { underageLockedNow: true, ageYears: ageCheck.ageYears };
      }
      if (!acceptedTermsAt || !acceptedPrivacyAt) {
        throw new CallEngineError('LEGAL_CONSENT_REQUIRED', 'الموافقة على الشروط وسياسة الخصوصية مطلوبة', 400);
      }
      return { underageLockedNow: false, ageCheck };
    });

    if (preCheck.underageLockedNow) {
      throw new CallEngineError(
        'UNDERAGE_FORBIDDEN',
        'التطبيق مخصص للبالغين (+18) فقط. تم تقييد الحساب والجهاز.',
        403,
        { underageLocked: true, ageYears: preCheck.ageYears }
      );
    }

    const { ageCheck } = preCheck;
    let providerOutcome = { status: 'PENDING_REVIEW', provider: 'manual_admin_review', rejectionReason: null };
    if (this.ageVerificationProvider && typeof this.ageVerificationProvider.verify === 'function') {
      const ext = await this.ageVerificationProvider.verify({
        uid,
        dob: ageCheck.normalizedDob,
        ageYears: ageCheck.ageYears
      });
      if (ext?.status) {
        providerOutcome = {
          status: String(ext.status).toUpperCase(),
          provider: ext.provider || 'external_idv',
          rejectionReason: ext.rejectionReason || null
        };
      }
    }

    return this._withLock(() => {
      const { user } = this._ensureUserSync({ uid, displayName, email });
      user.dob = ageCheck.normalizedDob;
      user.ageYears = ageCheck.ageYears;
      user.acceptedTermsAt = Number(acceptedTermsAt);
      user.acceptedPrivacyAt = Number(acceptedPrivacyAt);
      user.ageVerified = providerOutcome.status === 'APPROVED';
      user.ageVerificationStatus = providerOutcome.status;
      user.updatedAtMs = nowMs;

      const submission = {
        uid,
        displayName: user.displayName,
        email: user.email,
        dob: ageCheck.normalizedDob,
        ageYears: ageCheck.ageYears,
        status: providerOutcome.status,
        provider: providerOutcome.provider,
        reviewerUid: providerOutcome.status === 'PENDING_REVIEW' ? null : `provider:${providerOutcome.provider}`,
        rejectionReason: providerOutcome.rejectionReason,
        acceptedTermsAt: Number(acceptedTermsAt),
        acceptedPrivacyAt: Number(acceptedPrivacyAt),
        submittedAtMs: nowMs,
        reviewedAtMs: providerOutcome.status === 'PENDING_REVIEW' ? null : nowMs
      };
      this.ageVerifications.set(uid, submission);
      return { ...submission };
    });
  }

  async listAgeVerifications(status = 'PENDING_REVIEW') {
    return this._withLock(() => {
      const target = String(status || '').toUpperCase();
      return Array.from(this.ageVerifications.values())
        .filter((s) => !target || target === 'ALL' || s.status === target)
        .sort((a, b) => b.submittedAtMs - a.submittedAtMs)
        .map((s) => ({ ...s }));
    });
  }

  async reviewAgeVerification({ reviewerUid, targetUid, decision, reason = null }) {
    return this._withLock(() => {
      const normalized = String(decision || '').toUpperCase();
      if (normalized !== 'APPROVED' && normalized !== 'REJECTED') {
        throw new CallEngineError('INVALID_DECISION', 'القرار يجب أن يكون APPROVED أو REJECTED', 400);
      }
      const sub = this.ageVerifications.get(targetUid);
      if (!sub) {
        throw new CallEngineError('SUBMISSION_NOT_FOUND', 'طلب التحقق من العمر غير موجود', 404);
      }
      const { user } = this._ensureUserSync({ uid: targetUid });
      const nowMs = this.now();
      sub.status = normalized;
      sub.reviewerUid = reviewerUid;
      sub.rejectionReason = normalized === 'REJECTED' ? (reason || 'يرجى مراجعة تاريخ الميلاد المدخل') : null;
      sub.reviewedAtMs = nowMs;

      user.ageVerified = normalized === 'APPROVED';
      user.ageVerificationStatus = normalized;
      user.updatedAtMs = nowMs;

      this._auditSync({
        actorUid: reviewerUid,
        action: 'REVIEW_AGE_VERIFICATION',
        targetId: targetUid,
        details: { decision: normalized, reason: sub.rejectionReason }
      });

      return { submission: { ...sub }, user: { ...user } };
    });
  }

  async createPayoutRequest({ uid, amountCoins, payoutMethod = 'BANK_TRANSFER', bankAccountHolder, bankName, ibanOrAccount }) {
    return this._withLock(() => {
      const coins = Number(amountCoins);
      if (!Number.isInteger(coins) || coins < MIN_PAYOUT_COINS) {
        throw new CallEngineError('MIN_PAYOUT_NOT_MET', `الحد الأدنى لسحب الأرباح هو ${MIN_PAYOUT_COINS} نقطة`, 400);
      }
      if (!bankAccountHolder || !bankName || !ibanOrAccount) {
        throw new CallEngineError('MISSING_BANK_DETAILS', 'بيانات الحساب البنكي/التحويل مطلوبة بالكامل', 400);
      }

      const { user, wallet } = this._ensureUserSync({ uid });
      assertAdultVerified(user);
      if ((wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن طلب سحب الأرباح لوجود مديونية مستردات على الحساب.', 403, {
          debtCoins: wallet.debtCoins
        });
      }
      if (!user.isAgent && !user.isHost) {
        throw new CallEngineError('PAYOUT_ROLE_REQUIRED', 'سحب الأرباح متاح للمضيفين والوكلاء المعتمدين فقط', 403);
      }

      // A6 Velocity check: max 1 active payout request per user
      const hasActive = Array.from(this.payoutRequests.values()).some(
        (r) => r.uid === uid && (r.status === 'PENDING_REVIEW' || r.status === 'APPROVED')
      );
      if (hasActive) {
        throw new CallEngineError('ACTIVE_PAYOUT_EXISTS', 'لديك طلب سحب نشط قيد المراجعة بالفعل. انتظر البت فيه أولاً.', 409);
      }

      const availableEarnings = wallet.hostEarningsCoins + wallet.subAgentsEarningsCoins;
      if (availableEarnings < coins) {
        throw new CallEngineError('INSUFFICIENT_EARNINGS', 'رصيد الأرباح القابل للسحب غير كافٍ', 402, {
          availableEarnings,
          requested: coins
        });
      }

      let remaining = coins;
      const fromSubAgents = Math.min(wallet.subAgentsEarningsCoins, remaining);
      wallet.subAgentsEarningsCoins -= fromSubAgents;
      remaining -= fromSubAgents;
      wallet.hostEarningsCoins -= remaining;
      wallet.reservedPayoutCoins += coins;
      const nowMs = this.now();
      wallet.updatedAtMs = nowMs;

      const reqId = `payout_${crypto.randomUUID()}`;
      const usdCents = Math.floor((coins * 100) / COINS_PER_USD);
      const rawIban = String(ibanOrAccount).trim();
      const masked = maskAccountNumber(rawIban);
      const encryptedIban = encryptSensitiveField(rawIban, this.bankKey);

      const request = {
        id: reqId,
        uid,
        displayName: user.displayName,
        amountCoins: coins,
        usdEquivalentCents: usdCents,
        payoutMethod,
        bankAccountHolder: String(bankAccountHolder).trim(),
        bankName: String(bankName).trim(),
        ibanOrAccount: encryptedIban,
        ibanMasked: masked,
        fromSubAgentsCoins: fromSubAgents,
        fromHostCoins: remaining,
        earliestReviewAtMs: nowMs + this.payoutHoldMs,
        status: 'PENDING_REVIEW',
        reviewerUid: null,
        reviewerNote: null,
        paymentReference: null,
        createdAtMs: nowMs,
        reviewedAtMs: null
      };
      this.payoutRequests.set(reqId, request);

      this._appendLedgerSync({
        uid,
        entryType: 'WITHDRAWAL_RESERVE',
        bucket: 'RESERVED_PAYOUT',
        delta: coins,
        balanceAfter: wallet.reservedPayoutCoins,
        idempotencyKey: `payout_req_${reqId}`,
        referenceId: reqId,
        description: `طلب سحب أرباح قيد المراجعة (-${coins} نقطة أرباح)`
      });

      this._auditSync({
        actorUid: uid,
        action: 'CREATE_PAYOUT_REQUEST',
        targetId: reqId,
        details: { amountCoins: coins, usdEquivalentCents: usdCents, ibanMasked: masked }
      });

      return { request: this._publicPayoutView(request), wallet: { ...wallet } };
    });
  }

  async listPayoutRequests({ uid, isAdmin = false, status = null } = {}) {
    return this._withLock(() => {
      const targetStatus = status ? String(status).toUpperCase() : null;
      return Array.from(this.payoutRequests.values())
        .filter((r) => (isAdmin || r.uid === uid))
        .filter((r) => !targetStatus || targetStatus === 'ALL' || r.status === targetStatus)
        .sort((a, b) => b.createdAtMs - a.createdAtMs)
        .map((r) => this._publicPayoutView(r));
    });
  }

  async reviewPayoutRequest({ reviewerUid, requestId, decision, note = null, paymentReference = null }) {
    return this._withLock(() => {
      const normalized = String(decision || '').toUpperCase();
      if (!['APPROVED', 'PAID', 'REJECTED'].includes(normalized)) {
        throw new CallEngineError('INVALID_DECISION', 'القرار يجب أن يكون APPROVED أو PAID أو REJECTED', 400);
      }
      const reqItem = this.payoutRequests.get(requestId);
      if (!reqItem) {
        throw new CallEngineError('PAYOUT_NOT_FOUND', 'طلب السحب غير موجود', 404);
      }
      if (reviewerUid === reqItem.uid) {
        throw new CallEngineError('SELF_APPROVAL_FORBIDDEN', 'لا يمكن للمستخدم مراجعة أو اعتماد طلب السحب الخاص به', 403);
      }
      if (reqItem.status === 'PAID' || reqItem.status === 'REJECTED') {
        throw new CallEngineError('PAYOUT_ALREADY_FINALIZED', 'تم البت في طلب السحب هذا مسبقاً', 409);
      }

      const nowMs = this.now();
      if ((normalized === 'APPROVED' || normalized === 'PAID') && nowMs < reqItem.earliestReviewAtMs) {
        throw new CallEngineError('PAYOUT_HOLD_ACTIVE', 'طلب السحب لا يزال ضمن فترة الحجز الأمني (24 ساعة) قبل الاعتماد أو الصرف.', 409, {
          earliestReviewAtMs: reqItem.earliestReviewAtMs
        });
      }

      const { wallet } = this._ensureUserSync({ uid: reqItem.uid });

      if (normalized === 'REJECTED') {
        wallet.reservedPayoutCoins = Math.max(0, wallet.reservedPayoutCoins - reqItem.amountCoins);
        wallet.subAgentsEarningsCoins += reqItem.fromSubAgentsCoins;
        wallet.hostEarningsCoins += reqItem.fromHostCoins;
        wallet.updatedAtMs = nowMs;

        if (reqItem.fromSubAgentsCoins > 0) {
          this._appendLedgerSync({
            uid: reqItem.uid,
            entryType: 'WITHDRAWAL_REFUND',
            bucket: 'AGENCY_EARNINGS',
            delta: reqItem.fromSubAgentsCoins,
            balanceAfter: wallet.subAgentsEarningsCoins,
            idempotencyKey: `payout_refund_ag_${requestId}`,
            referenceId: requestId,
            description: `استرداد طلب سحب مرفوض إلى أرباح الوكالة (+${reqItem.fromSubAgentsCoins} نقطة)`
          });
        }
        if (reqItem.fromHostCoins > 0) {
          this._appendLedgerSync({
            uid: reqItem.uid,
            entryType: 'WITHDRAWAL_REFUND',
            bucket: 'HOST_EARNINGS',
            delta: reqItem.fromHostCoins,
            balanceAfter: wallet.hostEarningsCoins,
            idempotencyKey: `payout_refund_host_${requestId}`,
            referenceId: requestId,
            description: `استرداد طلب سحب مرفوض إلى أرباح المضيف (+${reqItem.fromHostCoins} نقطة)`
          });
        }
      } else if (normalized === 'PAID') {
        wallet.reservedPayoutCoins = Math.max(0, wallet.reservedPayoutCoins - reqItem.amountCoins);
        wallet.updatedAtMs = nowMs;

        this._appendLedgerSync({
          uid: reqItem.uid,
          entryType: 'WITHDRAWAL',
          bucket: 'RESERVED_PAYOUT',
          delta: -reqItem.amountCoins,
          balanceAfter: wallet.reservedPayoutCoins,
          idempotencyKey: `payout_paid_${requestId}`,
          referenceId: paymentReference || requestId,
          description: `صرف أرباح معتمد (${reqItem.amountCoins} نقطة = $${(reqItem.usdEquivalentCents / 100).toFixed(2)})`
        });
      }

      reqItem.status = normalized;
      reqItem.reviewerUid = reviewerUid;
      reqItem.reviewerNote = note;
      reqItem.paymentReference = paymentReference || reqItem.paymentReference;
      reqItem.reviewedAtMs = nowMs;

      this._auditSync({
        actorUid: reviewerUid,
        action: `PAYOUT_${normalized}`,
        targetId: requestId,
        details: { uid: reqItem.uid, amountCoins: reqItem.amountCoins, note, paymentReference }
      });

      return { request: this._publicPayoutView(reqItem), wallet: { ...wallet } };
    });
  }

  async setUserStatus({ reviewerUid, targetUid, status, reason = '' }) {
    return this._withLock(() => {
      const normalized = String(status || '').trim().toUpperCase();
      if (!VALID_USER_STATUSES.has(normalized)) {
        throw new CallEngineError('INVALID_STATUS', 'الحالة يجب أن تكون ACTIVE أو SUSPENDED أو BANNED', 400);
      }
      const { user } = this._ensureUserSync({ uid: targetUid });
      const nowMs = this.now();
      const prevStatus = user.status || 'ACTIVE';
      user.status = normalized;
      user.statusReason = normalized === 'ACTIVE' ? null : (String(reason || '').trim() || 'إجراء إداري');
      user.statusUpdatedAtMs = nowMs;
      user.statusUpdatedBy = reviewerUid;
      user.updatedAtMs = nowMs;

      this._auditSync({
        actorUid: reviewerUid,
        action: 'USER_STATUS_UPDATED',
        targetId: targetUid,
        details: { previousStatus: prevStatus, status: normalized, reason: user.statusReason }
      });

      return { user: { ...user } };
    });
  }

  async listHostApplications(status = 'PENDING') {
    return this._withLock(() => {
      const target = String(status || 'PENDING').toUpperCase();
      return Array.from(this.users.values())
        .filter((u) => !u.deletedAt)
        .filter((u) => (target === 'ALL' ? u.hostStatus !== 'NONE' : u.hostStatus === target))
        .sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0))
        .map((u) => ({ ...u }));
    });
  }

  async reviewHostApplication({ reviewerUid, targetUid, decision, hostCommissionPercent = null, reason = null }) {
    return this._withLock(() => {
      const normalized = String(decision || '').trim().toUpperCase();
      if (normalized !== 'APPROVED' && normalized !== 'REJECTED') {
        throw new CallEngineError('INVALID_DECISION', 'القرار يجب أن يكون APPROVED أو REJECTED', 400);
      }
      const { user } = this._ensureUserSync({ uid: targetUid });
      if (hostCommissionPercent !== null && hostCommissionPercent !== undefined) {
        validateCommissionPair(hostCommissionPercent, user.agentCommissionPercent);
        user.hostCommissionPercent = Number(hostCommissionPercent);
      }
      user.isHost = normalized === 'APPROVED';
      user.hostStatus = normalized;
      user.updatedAtMs = this.now();

      this._auditSync({
        actorUid: reviewerUid,
        action: 'REVIEW_HOST_APPLICATION',
        targetId: targetUid,
        details: {
          decision: normalized,
          hostCommissionPercent: user.hostCommissionPercent,
          agencyName: user.agencyName,
          reason: reason || null
        }
      });

      return { user: { ...user } };
    });
  }

  async adminSetUserRolesAndCommission({
    reviewerUid,
    reviewerAdminRole = 'SUPER_ADMIN',
    targetUid,
    isAgent,
    isHost,
    isAdmin,
    adminRole,
    agentCommissionPercent,
    hostCommissionPercent,
    agencyName
  }) {
    return this._withLock(() => {
      const isModifyingAdminPrivileges = typeof isAdmin === 'boolean' || adminRole !== undefined;
      if (isModifyingAdminPrivileges && reviewerUid !== 'system_auth_claims' && reviewerAdminRole !== 'SUPER_ADMIN') {
        throw new CallEngineError(
          'FORBIDDEN_SUPER_ADMIN_REQUIRED',
          'تعيين أو تعديل الأدوار الإدارية محصور بالمشرف الأعلى (SUPER_ADMIN) فقط.',
          403
        );
      }

      const { user } = this._ensureUserSync({ uid: targetUid });
      if (agentCommissionPercent !== undefined || hostCommissionPercent !== undefined) {
        const nextHost = hostCommissionPercent !== undefined ? hostCommissionPercent : user.hostCommissionPercent;
        const nextAgent = agentCommissionPercent !== undefined ? agentCommissionPercent : user.agentCommissionPercent;
        validateCommissionPair(nextHost, nextAgent);
        if (agentCommissionPercent !== undefined) user.agentCommissionPercent = Number(agentCommissionPercent);
        if (hostCommissionPercent !== undefined) user.hostCommissionPercent = Number(hostCommissionPercent);
      }

      if (typeof isAgent === 'boolean') user.isAgent = isAgent;
      if (typeof isHost === 'boolean') {
        user.isHost = isHost;
        user.hostStatus = isHost ? 'APPROVED' : 'NONE';
      }
      if (adminRole !== undefined) {
        const upperRole = String(adminRole || 'NONE').trim().toUpperCase();
        if (!VALID_ADMIN_ROLES.has(upperRole)) {
          throw new CallEngineError('INVALID_ADMIN_ROLE', 'الدور الإداري غير معروف', 400);
        }
        user.adminRole = upperRole;
        user.isAdmin = upperRole !== 'NONE';
      } else if (typeof isAdmin === 'boolean') {
        user.isAdmin = isAdmin;
        user.adminRole = isAdmin ? (user.adminRole && user.adminRole !== 'NONE' ? user.adminRole : 'SUPER_ADMIN') : 'NONE';
      }
      if (agencyName !== undefined) {
        user.agencyName = String(agencyName).trim();
      }
      user.updatedAtMs = this.now();

      if (user.isAgent && !user.agencyId) {
        const agencyId = `ag_${targetUid.slice(0, 8)}`;
        user.agencyId = agencyId;
        user.agencyName = user.agencyName || `وكالة ${user.displayName}`;
        this.agencies.set(agencyId, {
          id: agencyId,
          ownerUid: targetUid,
          name: user.agencyName,
          country: user.country,
          commissionPercent: user.agentCommissionPercent,
          status: 'ACTIVE',
          createdAtMs: this.now()
        });
      }

      this._auditSync({
        actorUid: reviewerUid,
        action: 'ADMIN_UPDATE_USER_ROLES',
        targetId: targetUid,
        details: {
          isAgent: user.isAgent,
          isHost: user.isHost,
          isAdmin: user.isAdmin,
          adminRole: user.adminRole,
          agentCommissionPercent: user.agentCommissionPercent,
          hostCommissionPercent: user.hostCommissionPercent,
          agencyName: user.agencyName
        }
      });

      return { ...user };
    });
  }

  async getAgencyDashboard(uid) {
    return this._withLock(() => {
      const { user, wallet } = this._ensureUserSync({ uid });
      const subAgents = Array.from(this.agencyMemberships.values())
        .filter((m) => m.parentAgentUid === uid)
        .map((m) => ({ ...m }));
      const payouts = Array.from(this.payoutRequests.values())
        .filter((r) => r.uid === uid)
        .sort((a, b) => b.createdAtMs - a.createdAtMs)
        .map((r) => this._publicPayoutView(r));
      return {
        user: { ...user },
        wallet: { ...wallet },
        subAgents,
        payouts
      };
    });
  }

  async recruitSubAgent({ agentUid, subAgentName, country = '', subAgentUid = null }) {
    return this._withLock(() => {
      const { user } = this._ensureUserSync({ uid: agentUid });
      if (!user.isAgent && !user.isAdmin) {
        throw new CallEngineError('AGENT_REQUIRED', 'هذه الميزة مخصصة للوكلاء المعتمدين فقط', 403);
      }
      if (!subAgentName || !String(subAgentName).trim()) {
        throw new CallEngineError('INVALID_NAME', 'اسم الوكيل الفرعي مطلوب', 400);
      }
      const targetUid = subAgentUid || `sub_${crypto.randomUUID().slice(0, 10)}`;
      const membership = {
        id: targetUid,
        uid: targetUid,
        name: String(subAgentName).trim(),
        country: String(country || '').trim(),
        agencyId: user.agencyId || `ag_${agentUid}`,
        parentAgentUid: agentUid,
        hostsCount: 0,
        monthlyRevenueCoins: 0,
        agentCommissionEarned: 0,
        status: 'قيد التفعيل'
      };
      this.agencyMemberships.set(targetUid, membership);
      user.subAgentsCount = Array.from(this.agencyMemberships.values()).filter((m) => m.parentAgentUid === agentUid).length;
      return { membership: { ...membership }, subAgentsCount: user.subAgentsCount };
    });
  }

  async createModerationReport({ reportType, targetUserId = null, streamId = null, reporterUid, reason, details = '' }) {
    return this._withLock(() => {
      if (!reason) {
        throw new CallEngineError('MISSING_REASON', 'سبب البلاغ مطلوب', 400);
      }
      const id = `rep_${crypto.randomUUID()}`;
      const report = {
        id,
        reportType: reportType || 'USER_PROFILE',
        targetUserId,
        streamId,
        reporterUid,
        reason,
        details: details || '',
        status: 'PENDING_REVIEW',
        actionTaken: null,
        reviewerUid: null,
        createdAtMs: this.now(),
        reviewedAtMs: null
      };
      this.moderationReports.set(id, report);
      return { ...report };
    });
  }

  async listModerationReports(status = 'PENDING_REVIEW') {
    return this._withLock(() => {
      const target = String(status || '').toUpperCase();
      return Array.from(this.moderationReports.values())
        .filter((r) => !target || target === 'ALL' || r.status === target)
        .sort((a, b) => b.createdAtMs - a.createdAtMs)
        .map((r) => ({ ...r }));
    });
  }

  async resolveModerationReport({ reviewerUid, reportId, status = 'RESOLVED', actionTaken = 'WARNING' }) {
    return this._withLock(() => {
      const rep = this.moderationReports.get(reportId);
      if (!rep) {
        throw new CallEngineError('REPORT_NOT_FOUND', 'البلاغ غير موجود', 404);
      }
      const nowMs = this.now();
      rep.status = status;
      rep.actionTaken = actionTaken;
      rep.reviewerUid = reviewerUid;
      rep.reviewedAtMs = nowMs;

      if (rep.targetUserId && (actionTaken === 'SUSPEND_USER' || actionTaken === 'BAN_USER')) {
        const targetStatus = actionTaken === 'BAN_USER' ? 'BANNED' : 'SUSPENDED';
        const { user } = this._ensureUserSync({ uid: rep.targetUserId });
        user.status = targetStatus;
        user.statusReason = `بلاغ رقابي: ${rep.reason}`;
        user.statusUpdatedAtMs = nowMs;
        user.statusUpdatedBy = reviewerUid;
        user.updatedAtMs = nowMs;
      }

      this._auditSync({
        actorUid: reviewerUid,
        action: 'RESOLVE_MODERATION_REPORT',
        targetId: reportId,
        details: { status, actionTaken, targetUserId: rep.targetUserId }
      });
      return { ...rep };
    });
  }

  async getModerationDailyUsage(dayKey) {
    return this._withLock(() => {
      const usage = this.moderationDailyUsage.get(dayKey);
      return usage ? { ...usage } : { dayKey, sampleCount: 0, estimatedCostUsd: 0 };
    });
  }

  async createModerationSample({
    id,
    streamId = null,
    userUid,
    sampleType = 'STREAM_FRAME',
    provider = 'heuristic_local',
    status = 'CLEAN',
    riskScore = 0,
    labels = [],
    estimatedCostUsd = 0,
    actionTaken = 'NONE',
    details = '',
    dayKey = null,
    incrementUsage = true
  }) {
    return this._withLock(() => {
      const nowMs = this.now();
      const sample = {
        id: id || `mod_${crypto.randomUUID()}`,
        streamId,
        userUid,
        sampleType,
        provider,
        status,
        riskScore: Number(riskScore || 0),
        labels: Array.isArray(labels) ? [...labels] : [],
        estimatedCostUsd: Number(estimatedCostUsd || 0),
        actionTaken,
        reviewerUid: null,
        details: String(details || ''),
        createdAtMs: nowMs,
        reviewedAtMs: null
      };
      this.moderationSamples.set(sample.id, sample);

      if (incrementUsage && dayKey) {
        const prev = this.moderationDailyUsage.get(dayKey) || { dayKey, sampleCount: 0, estimatedCostUsd: 0 };
        this.moderationDailyUsage.set(dayKey, {
          dayKey,
          sampleCount: prev.sampleCount + 1,
          estimatedCostUsd: Number((prev.estimatedCostUsd + sample.estimatedCostUsd).toFixed(6))
        });
      }

      return { ...sample, labels: [...sample.labels] };
    });
  }

  async listModerationSamples(status = 'QUEUE') {
    return this._withLock(() => {
      const target = String(status || 'QUEUE').toUpperCase();
      return Array.from(this.moderationSamples.values())
        .filter((s) => {
          if (target === 'ALL') return true;
          if (target === 'QUEUE') return s.status === 'PENDING_MANUAL_REVIEW' || s.status === 'FLAGGED' || s.status === 'BLOCKED';
          return s.status === target;
        })
        .sort((a, b) => b.createdAtMs - a.createdAtMs)
        .map((s) => ({ ...s, labels: [...(s.labels || [])] }));
    });
  }

  async reviewModerationSample({ reviewerUid, sampleId, decision, note = null }) {
    return this._withLock(() => {
      const norm = String(decision || '').trim().toUpperCase();
      if (norm !== 'APPROVED' && norm !== 'CONFIRMED') {
        throw new CallEngineError('INVALID_DECISION', 'القرار يجب أن يكون APPROVED أو CONFIRMED', 400);
      }
      const sample = this.moderationSamples.get(sampleId);
      if (!sample) {
        throw new CallEngineError('MODERATION_SAMPLE_NOT_FOUND', 'عينة الفحص غير موجودة', 404);
      }
      sample.status = norm === 'APPROVED' ? 'REVIEWED_APPROVED' : 'REVIEWED_CONFIRMED';
      sample.reviewerUid = reviewerUid;
      sample.reviewedAtMs = this.now();
      if (note) sample.details = `${sample.details} | ${note}`.trim();

      this._auditSync({
        actorUid: reviewerUid,
        action: 'REVIEW_MODERATION_SAMPLE',
        targetId: sampleId,
        details: { decision: norm, streamId: sample.streamId, userUid: sample.userUid, note }
      });
      return { ...sample, labels: [...(sample.labels || [])] };
    });
  }

  async blockUser({ blockerUid, blockedUid }) {
    return this._withLock(() => {
      if (!blockedUid || blockerUid === blockedUid) {
        throw new CallEngineError('INVALID_BLOCK', 'معرف المستخدم المحظور غير صالح', 400);
      }
      if (!this.blocks.has(blockerUid)) this.blocks.set(blockerUid, new Set());
      this.blocks.get(blockerUid).add(blockedUid);
      return { blockedUsers: Array.from(this.blocks.get(blockerUid)) };
    });
  }

  async unblockUser({ blockerUid, blockedUid }) {
    return this._withLock(() => {
      this.blocks.get(blockerUid)?.delete(blockedUid);
      return { blockedUsers: Array.from(this.blocks.get(blockerUid) || []) };
    });
  }

  async getBlockedUsers(uid) {
    return this._withLock(() => Array.from(this.blocks.get(uid) || []));
  }

  async deleteUserAccount({ uid }) {
    return this._withLock(() => {
      const user = this.users.get(uid);
      if (user) {
        user.deletedAt = this.now();
        user.displayName = 'حساب محذوف';
        user.email = '';
        user.dob = null;
        user.bio = '';
        user.photoUrl = null;
        user.updatedAtMs = this.now();
      }
      this.ageVerifications.delete(uid);
      this.blocks.delete(uid);
      for (const blockedSet of this.blocks.values()) {
        blockedSet.delete(uid);
      }
      this._auditSync({ actorUid: uid, action: 'ACCOUNT_DELETED', targetId: uid });
      return { deleted: true, uid };
    });
  }

  async getAuditLogs({ actorUid = null, action = null, targetId = null, limit = 100 } = {}) {
    return this._withLock(() => {
      const safeLimit = Math.max(1, Math.min(500, Number(limit) || 100));
      return this.auditLogs
        .filter((log) => (!actorUid || log.actorUid === actorUid))
        .filter((log) => (!action || log.action === action))
        .filter((log) => (!targetId || log.targetId === targetId))
        .slice(0, safeLimit)
        .map((log) => ({ ...log, details: { ...(log.details || {}) } }));
    });
  }

  async processVoidedPurchase({ purchaseToken, reason = 'PLAY_STORE_REFUND' }) {
    return this._withLock(() => {
      const tx = this.iapTokens.get(purchaseToken);
      if (!tx) {
        throw new CallEngineError('IAP_NOT_FOUND', 'عملية الشراء غير موجودة', 404);
      }
      if (tx.isVoided) {
        return {
          duplicate: true,
          alreadyVoided: true,
          purchaseToken,
          uid: tx.uid
        };
      }
      tx.isVoided = true;
      tx.voidedAtMs = this.now();
      tx.voidedReason = reason;

      const { wallet } = this._ensureUserSync({ uid: tx.uid });
      const coinsToClawback = tx.coinsCredited;
      const deductedFromBalance = Math.min(wallet.pointsBalance, coinsToClawback);
      const addedDebt = coinsToClawback - deductedFromBalance;

      wallet.pointsBalance -= deductedFromBalance;
      wallet.debtCoins = (wallet.debtCoins || 0) + addedDebt;
      wallet.updatedAtMs = this.now();

      const entry = this._appendLedgerSync({
        uid: tx.uid,
        entryType: 'REFUND_CLAWBACK',
        bucket: 'POINTS',
        delta: -deductedFromBalance,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: `clawback_${purchaseToken}`,
        referenceId: purchaseToken,
        description: `استرداد مشتريات Google Play (-${coinsToClawback} نقطة)`,
        metadata: {
          purchaseToken,
          reason,
          coinsToClawback,
          deductedFromBalance,
          addedDebt,
          totalDebtCoins: wallet.debtCoins
        }
      });

      this._auditSync({
        actorUid: tx.uid,
        action: 'IAP_PURCHASE_VOIDED',
        targetId: purchaseToken,
        details: {
          coinsClawedBack: coinsToClawback,
          deductedFromBalance,
          addedDebt,
          reason
        }
      });

      if (addedDebt > 0) {
        this._auditSync({
          actorUid: 'system',
          action: 'REFUND_CLAWBACK_DEBT',
          targetId: tx.uid,
          details: {
            debtCoins: wallet.debtCoins,
            addedDebt,
            purchaseToken
          }
        });
      }

      const giftEntries = this.ledger.filter(
        (e) => e.uid === tx.uid && e.entryType === 'GIFT_SENT'
      );
      for (const g of giftEntries) {
        const recipientUid = g.metadata?.recipientUid;
        if (recipientUid) {
          this._auditSync({
            actorUid: 'system',
            action: 'HOST_EARNINGS_REVIEW_FLAGGED',
            targetId: recipientUid,
            details: {
              buyerUid: tx.uid,
              giftEntryId: g.entryUuid,
              reason: 'PURCHASE_REFUNDED_BY_BUYER',
              purchaseToken
            }
          });
        }
      }

      return {
        ok: true,
        uid: tx.uid,
        purchaseToken,
        coinsClawedBack: coinsToClawback,
        deductedFromBalance,
        addedDebt,
        debtCoins: wallet.debtCoins,
        entry
      };
    });
  }

  async exportReconciliationSnapshot() {
    return this._withLock(() => ({
      wallets: Array.from(this.wallets.values()).map((w) => ({ ...w })),
      ledgerEntries: this.ledger.map((e) => ({ ...e })),
      holds: Array.from(this.holds.values()).map((h) => ({ ...h })),
      payoutRequests: Array.from(this.payoutRequests.values()).map((p) => ({ ...p }))
    }));
  }
}

export class PostgresLedgerStore {
  constructor({
    pool,
    nowProvider = () => Date.now(),
    env = process.env,
    payoutHoldMs = PAYOUT_HOLD_MS,
    ageVerificationProvider = null
  }) {
    this.mode = 'postgres';
    this.pool = pool;
    this.now = nowProvider;
    this.payoutHoldMs = payoutHoldMs;
    this.ageVerificationProvider = ageVerificationProvider;
    this.bankKey = resolveBankEncryptionKey(env, createEphemeralKey());
  }

  async init() {
    const migrations = await runMigrations(this.pool);
    return { ok: true, mode: this.mode, migrations };
  }

  async _withTx(fn) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await fn(client);
      await client.query('COMMIT');
      return result;
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      throw err;
    } finally {
      client.release();
    }
  }

  async _ensureUserTx(client, { uid, displayName = '', email = '' }) {
    const safeUid = String(uid || '').trim();
    if (!safeUid) throw new CallEngineError('INVALID_UID', 'معرف المستخدم مطلوب', 400);
    const isTreasury = safeUid === PLATFORM_TREASURY_UID;
    await client.query(
      `INSERT INTO users (uid, display_name, email, referral_code, is_admin, admin_role)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (uid) DO UPDATE SET
         display_name = CASE WHEN users.display_name = '' THEN EXCLUDED.display_name ELSE users.display_name END,
         email = CASE WHEN users.email = '' THEN EXCLUDED.email ELSE users.email END,
         updated_at = NOW()`,
      [
        safeUid,
        isTreasury ? 'خزينة المنصة' : (displayName || 'مستخدم Ayer'),
        email || '',
        generateReferralCode(safeUid),
        isTreasury,
        isTreasury ? 'SUPER_ADMIN' : 'NONE'
      ]
    );
    await client.query(
      `INSERT INTO wallet_accounts (uid) VALUES ($1) ON CONFLICT (uid) DO NOTHING`,
      [safeUid]
    );
    const userRes = await client.query(`SELECT * FROM users WHERE uid = $1 FOR UPDATE`, [safeUid]);
    const walletRes = await client.query(`SELECT * FROM wallet_accounts WHERE uid = $1 FOR UPDATE`, [safeUid]);
    return { user: this._mapUser(userRes.rows[0]), wallet: this._mapWallet(walletRes.rows[0]) };
  }

  async _getEconomyConfigTx(client) {
    const res = await client.query(`SELECT * FROM economy_config WHERE id = 1`);
    if (res.rowCount === 0) return { ...DEFAULT_ECONOMY_CONFIG };
    const r = res.rows[0];
    return {
      hostCommissionPercent: Number(r.host_commission_percent ?? DEFAULT_ECONOMY_CONFIG.hostCommissionPercent),
      agentCommissionPercent: Number(r.agent_commission_percent ?? DEFAULT_ECONOMY_CONFIG.agentCommissionPercent),
      platformCommissionPercent: Number(r.platform_commission_percent ?? DEFAULT_ECONOMY_CONFIG.platformCommissionPercent),
      minPlatformPercent: Number(r.min_platform_percent ?? MIN_PLATFORM_PERCENT),
      coinsPerUsd: Number(r.coins_per_usd ?? COINS_PER_USD),
      minPayoutCoins: Number(r.min_payout_coins ?? MIN_PAYOUT_COINS),
      payoutHoldHours: Number(r.payout_hold_hours ?? PAYOUT_HOLD_HOURS),
      dailyFreeRewardCapCoins: Number(r.daily_free_reward_cap_coins ?? DAILY_FREE_REWARD_CAP_COINS)
    };
  }

  async _creditPlatformRevenueTx(client, { amount, idempotencyKey, referenceId = null, description, metadata = {} }) {
    if (amount <= 0) return null;
    await this._ensureUserTx(client, { uid: PLATFORM_TREASURY_UID });
    const upd = await client.query(
      `UPDATE wallet_accounts
       SET platform_revenue_coins = platform_revenue_coins + $1,
           updated_at = NOW()
       WHERE uid = $2
       RETURNING *`,
      [amount, PLATFORM_TREASURY_UID]
    );
    const platBalance = Number(upd.rows[0].platform_revenue_coins);
    await client.query(
      `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
       VALUES ($1,$2,'PLATFORM_REVENUE','PLATFORM_REVENUE',$3,$4,$5,$6,$7,$8)
       ON CONFLICT (idempotency_key) DO NOTHING`,
      [crypto.randomUUID(), PLATFORM_TREASURY_UID, amount, platBalance, idempotencyKey, referenceId, description, JSON.stringify(metadata)]
    );
    return platBalance;
  }

  async _recordActivityTx(client, { uid, activityType, count = 1 }) {
    const dayKey = new Date(this.now()).toISOString().slice(0, 10);
    const res = await client.query(
      `INSERT INTO daily_activity_counters (uid, day_key, activity_type, activity_count)
       VALUES ($1,$2,$3,$4)
       ON CONFLICT (uid, day_key, activity_type) DO UPDATE SET
         activity_count = daily_activity_counters.activity_count + EXCLUDED.activity_count,
         updated_at = NOW()
       RETURNING activity_count`,
      [uid, dayKey, activityType, Number(count || 1)]
    );
    return Number(res.rows[0]?.activity_count || count);
  }

  _mapUser(r) {
    const adminRole = resolveAdminRoleSync(r.admin_role, Boolean(r.is_admin));
    return {
      uid: r.uid,
      displayName: r.display_name,
      email: r.email,
      photoUrl: r.photo_url,
      bio: r.bio,
      country: r.country,
      countryCode: r.country_code,
      gender: r.gender,
      dob: r.dob,
      ageYears: Number(r.age_years || 0),
      ageVerified: Boolean(r.age_verified),
      ageVerificationStatus: r.age_verification_status,
      isAdmin: Boolean(r.is_admin) || adminRole !== 'NONE',
      adminRole,
      status: r.status || 'ACTIVE',
      statusReason: r.status_reason || null,
      statusUpdatedAtMs: r.status_updated_at ? Number(r.status_updated_at) : null,
      statusUpdatedBy: r.status_updated_by || null,
      underageLocked: Boolean(r.underage_locked),
      underageLockedReason: r.underage_locked_reason || null,
      underageLockedAtMs: r.underage_locked_at ? Number(r.underage_locked_at) : null,
      isAgent: Boolean(r.is_agent),
      isHost: Boolean(r.is_host),
      hostStatus: r.host_status,
      agentCommissionPercent: Number(r.agent_commission_percent ?? 20),
      hostCommissionPercent: Number(r.host_commission_percent ?? 50),
      agencyId: r.agency_id || '',
      agencyName: r.agency_name || '',
      referralCode: r.referral_code || '',
      profileVisibility: r.profile_visibility || 'PUBLIC',
      directMessagePermission: r.direct_message_permission || 'EVERYONE',
      acceptedTermsAt: r.accepted_terms_at ? Number(r.accepted_terms_at) : null,
      acceptedPrivacyAt: r.accepted_privacy_at ? Number(r.accepted_privacy_at) : null,
      deletedAt: r.deleted_at ? Number(r.deleted_at) : null
    };
  }

  _mapWallet(r) {
    return {
      uid: r.uid,
      pointsBalance: Number(r.points_balance),
      debtCoins: Number(r.debt_coins || 0),
      heldCoins: Number(r.held_coins || 0),
      hostEarningsCoins: Number(r.host_earnings_coins),
      subAgentsEarningsCoins: Number(r.sub_agents_earnings_coins),
      reservedPayoutCoins: Number(r.reserved_payout_coins),
      platformRevenueCoins: Number(r.platform_revenue_coins || 0),
      totalAccumulatedPoints: Number(r.total_accumulated_points),
      totalPointsSpent: Number(r.total_points_spent),
      lastDailyClaimMs: Number(r.last_daily_claim_ms)
    };
  }

  _mapHold(r) {
    return {
      holdId: r.hold_id,
      uid: r.uid,
      purpose: r.purpose,
      referenceId: r.reference_id || null,
      amountCoins: Number(r.amount_coins),
      capturedCoins: Number(r.captured_coins || 0),
      releasedCoins: Number(r.released_coins || 0),
      status: r.status,
      idempotencyKey: r.idempotency_key,
      createdAtMs: r.created_at ? new Date(r.created_at).getTime() : this.now(),
      settledAtMs: r.settled_at ? new Date(r.settled_at).getTime() : null
    };
  }

  _mapAgeVerification(r) {
    return {
      uid: r.uid,
      displayName: r.display_name || '',
      email: r.email || '',
      dob: r.dob,
      ageYears: Number(r.age_years || 0),
      status: r.status,
      reviewerUid: r.reviewer_uid || null,
      rejectionReason: r.rejection_reason || null,
      acceptedTermsAt: r.accepted_terms_at ? Number(r.accepted_terms_at) : null,
      acceptedPrivacyAt: r.accepted_privacy_at ? Number(r.accepted_privacy_at) : null,
      submittedAtMs: r.submitted_at ? new Date(r.submitted_at).getTime() : this.now(),
      reviewedAtMs: r.reviewed_at ? new Date(r.reviewed_at).getTime() : null
    };
  }

  _mapPayoutRequest(r) {
    const masked = r.iban_masked || maskAccountNumber(r.iban_or_account);
    return {
      id: r.id,
      uid: r.uid,
      displayName: r.display_name || '',
      amountCoins: Number(r.amount_coins),
      usdEquivalentCents: Number(r.usd_equivalent_cents),
      payoutMethod: r.payout_method,
      bankAccountHolder: r.bank_account_holder,
      bankName: r.bank_name,
      ibanOrAccount: masked,
      ibanMasked: masked,
      fromSubAgentsCoins: Number(r.from_sub_agents_coins || 0),
      fromHostCoins: Number(r.from_host_coins || 0),
      earliestReviewAtMs: r.earliest_review_at ? new Date(r.earliest_review_at).getTime() : (this.now() + this.payoutHoldMs),
      status: r.status,
      reviewerUid: r.reviewer_uid || null,
      reviewerNote: r.reviewer_note || null,
      paymentReference: r.payment_reference || null,
      createdAtMs: r.created_at ? new Date(r.created_at).getTime() : this.now(),
      reviewedAtMs: r.reviewed_at ? new Date(r.reviewed_at).getTime() : null
    };
  }

  _mapModerationReport(r) {
    return {
      id: r.id,
      reportType: r.report_type,
      targetUserId: r.target_user_id || null,
      streamId: r.stream_id || null,
      reporterUid: r.reporter_uid,
      reason: r.reason,
      details: r.details || '',
      status: r.status,
      actionTaken: r.action_taken || null,
      reviewerUid: r.reviewer_uid || null,
      createdAtMs: r.created_at ? new Date(r.created_at).getTime() : this.now(),
      reviewedAtMs: r.reviewed_at ? new Date(r.reviewed_at).getTime() : null
    };
  }

  async recordServerActivity({ uid, activityType, count = 1 }) {
    return this._withTx(async (c) => {
      await this._ensureUserTx(c, { uid });
      const countToday = await this._recordActivityTx(c, { uid, activityType, count });
      return { uid, activityType, countToday };
    });
  }

  async getEconomyConfig() {
    return this._withTx((c) => this._getEconomyConfigTx(c));
  }

  async updateEconomyConfig({ reviewerUid, hostCommissionPercent, agentCommissionPercent }) {
    return this._withTx(async (c) => {
      const current = await this._getEconomyConfigTx(c);
      const nextHost = hostCommissionPercent !== undefined ? hostCommissionPercent : current.hostCommissionPercent;
      const nextAgent = agentCommissionPercent !== undefined ? agentCommissionPercent : current.agentCommissionPercent;
      const split = validateCommissionPair(nextHost, nextAgent);

      await c.query(
        `INSERT INTO economy_config (id, host_commission_percent, agent_commission_percent, platform_commission_percent, updated_by, updated_at)
         VALUES (1,$1,$2,$3,$4,NOW())
         ON CONFLICT (id) DO UPDATE SET
           host_commission_percent = EXCLUDED.host_commission_percent,
           agent_commission_percent = EXCLUDED.agent_commission_percent,
           platform_commission_percent = EXCLUDED.platform_commission_percent,
           updated_by = EXCLUDED.updated_by,
           updated_at = NOW()`,
        [split.hostCommissionPercent, split.agentCommissionPercent, split.platformCommissionPercent, reviewerUid]
      );

      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'UPDATE_ECONOMY_CONFIG','economy_config',$2)`,
        [reviewerUid, JSON.stringify(split)]
      );

      return { ...current, ...split };
    });
  }

  async ensureUser(input) {
    return this._withTx((c) => this._ensureUserTx(c, input));
  }

  async getUserGateState(uid) {
    return this._withTx((c) => this._ensureUserTx(c, { uid }));
  }

  async assertUserCanOperate(uid, { requireAgeVerified = true } = {}) {
    return this._withTx(async (c) => {
      const { user, wallet } = await this._ensureUserTx(c, { uid });
      if (requireAgeVerified) {
        assertAdultVerified(user);
      } else {
        assertAccountActive(user);
      }
      return { user, wallet };
    });
  }

  async getIapTransaction(purchaseToken) {
    return this._withTx(async (c) => {
      const res = await c.query(`SELECT * FROM iap_transactions WHERE purchase_token = $1`, [purchaseToken]);
      if (res.rowCount === 0) return null;
      const r = res.rows[0];
      return {
        purchaseToken: r.purchase_token,
        orderId: r.order_id,
        uid: r.uid,
        packageId: r.package_id,
        productId: r.product_id,
        coinsCredited: Number(r.coins_credited),
        amountMicros: Number(r.amount_micros),
        currencyCode: r.currency_code,
        purchaseState: r.purchase_state,
        acknowledged: r.acknowledged,
        obfuscatedExternalAccountId: r.obfuscated_external_account_id,
        isVoided: Boolean(r.is_voided),
        voidedAt: r.voided_at,
        voidedReason: r.voided_reason,
        verificationSource: r.verification_source,
        verifiedAt: r.verified_at
      };
    });
  }

  async markIapAcknowledged(purchaseToken) {
    const res = await this.pool.query(
      `UPDATE iap_transactions SET acknowledged = TRUE WHERE purchase_token = $1`,
      [purchaseToken]
    );
    return res.rowCount > 0;
  }

  async getWalletSummary(uid, limit = 50) {
    return this._withTx(async (c) => {
      const { user, wallet } = await this._ensureUserTx(c, { uid });
      const txRes = await c.query(
        `SELECT * FROM ledger_entries WHERE uid = $1 ORDER BY created_at DESC LIMIT $2`,
        [uid, Math.max(1, Math.min(200, Number(limit) || 50))]
      );
      return {
        user,
        wallet,
        transactions: txRes.rows.map((e) => ({
          id: Number(e.id),
          entryUuid: e.entry_uuid,
          uid: e.uid,
          type: e.entry_type,
          entryType: e.entry_type,
          bucket: e.bucket,
          amount: Number(e.delta),
          delta: Number(e.delta),
          balanceAfter: Number(e.balance_after),
          idempotencyKey: e.idempotency_key,
          referenceId: e.reference_id || null,
          description: e.description,
          metadata: typeof e.metadata === 'string' ? JSON.parse(e.metadata) : (e.metadata || {}),
          timestamp: e.created_at ? new Date(e.created_at).getTime() : this.now(),
          createdAtMs: e.created_at ? new Date(e.created_at).getTime() : this.now()
        }))
      };
    });
  }

  async creditPlayPurchase({
    uid,
    displayName = '',
    email = '',
    packageId,
    productId,
    coinsCredited,
    priceUsd = 0,
    purchaseToken,
    orderId = null,
    purchaseState = 0,
    acknowledged = true,
    obfuscatedExternalAccountId = null,
    verificationSource = 'google_play_androidpublisher_v3'
  }) {
    return this._withTx(async (c) => {
      const coins = Number(coinsCredited);
      if (!purchaseToken || !Number.isInteger(coins) || coins <= 0) {
        throw new CallEngineError('INVALID_IAP_PAYLOAD', 'بيانات شراء Google Play غير صالحة', 400);
      }
      const { user, wallet } = await this._ensureUserTx(c, { uid, displayName, email });
      assertAccountActive(user);

      const insTx = await c.query(
        `INSERT INTO iap_transactions
          (purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros, currency_code, purchase_state, acknowledged, obfuscated_external_account_id, verification_source)
         VALUES ($1,$2,$3,$4,$5,$6,$7,'USD',$8,$9,$10,$11)
         ON CONFLICT (purchase_token) DO NOTHING
         RETURNING *`,
        [purchaseToken, orderId, uid, packageId, productId, coins, Math.round(Number(priceUsd) * 1_000_000), purchaseState, acknowledged, obfuscatedExternalAccountId, verificationSource]
      );
      if (insTx.rowCount === 0) {
        const existing = await c.query(`SELECT * FROM iap_transactions WHERE purchase_token = $1`, [purchaseToken]);
        const exRow = existing.rows[0];
        if (exRow && exRow.uid === uid) {
          return { wallet, creditedCoins: Number(exRow.coins_credited), duplicate: true, alreadyCredited: true };
        }
        throw new CallEngineError('DUPLICATE_PURCHASE_TOKEN_OTHER_USER', 'رمز الشراء مسجل لحساب مستخدم آخر', 409);
      }

      const currentDebt = wallet.debtCoins || 0;
      const debtSettled = Math.min(currentDebt, coins);
      const coinsToCredit = coins - debtSettled;

      const upd = await c.query(
        `UPDATE wallet_accounts
         SET points_balance = points_balance + $1,
             debt_coins = debt_coins - $2,
             total_accumulated_points = total_accumulated_points + $3,
             updated_at = NOW()
         WHERE uid = $4
         RETURNING *`,
        [coinsToCredit, debtSettled, coins, uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);

      if (debtSettled > 0) {
        await c.query(
          `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
           VALUES ($1,$2,'PURCHASE_DEBT_SETTLEMENT','DEBT',$3,$4,$5,$6,$7,$8)`,
          [crypto.randomUUID(), uid, -debtSettled, updatedWallet.debtCoins, `debt_settle_${purchaseToken}`, orderId || purchaseToken, `سداد مديونية سابقة من شحن Google Play (-${debtSettled} نقطة دين)`, JSON.stringify({ purchaseToken, debtSettled })]
        );
      }

      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'PURCHASE','POINTS',$3,$4,$5,$6,$7,$8)`,
        [crypto.randomUUID(), uid, coinsToCredit, updatedWallet.pointsBalance, `iap_${purchaseToken}`, orderId || purchaseToken, debtSettled > 0 ? `شحن Google Play (+${coins} نقطة، سدد منها ${debtSettled} دين، والمضاف ${coinsToCredit})` : `شحن Google Play (+${coins} نقطة)`, JSON.stringify({ packageId, productId, verificationSource, debtSettled })]
      );

      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details)
         VALUES ($1, 'IAP_PURCHASE_CREDITED', $2, $3)`,
        [uid, packageId, JSON.stringify({ coinsCredited: coins, debtSettled, orderId })]
      );

      return { wallet: updatedWallet, creditedCoins: coins, debtSettled, duplicate: false, alreadyCredited: false };
    });
  }

  async holdCoins({ uid, amount, purpose = 'MATCH_HOLD', referenceId = null, idempotencyKey }) {
    return this._withTx(async (c) => {
      const holdAmount = Number(amount);
      if (!Number.isInteger(holdAmount) || holdAmount <= 0) {
        throw new CallEngineError('INVALID_AMOUNT', 'قيمة الحجز يجب أن تكون عدداً صحيحاً موجباً', 400);
      }
      const key = String(idempotencyKey || `hold_${uid}_${crypto.randomUUID()}`);
      const existingHold = await c.query(`SELECT * FROM ledger_holds WHERE idempotency_key = $1`, [key]);
      if (existingHold.rowCount > 0) {
        const h = this._mapHold(existingHold.rows[0]);
        const { wallet } = await this._ensureUserTx(c, { uid: h.uid });
        return { hold: h, wallet, duplicate: true };
      }

      const { user, wallet } = await this._ensureUserTx(c, { uid });
      assertAdultVerified(user);
      if ((wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن حجز النقاط لوجود مديونية مستردات على الحساب.', 403, {
          debtCoins: wallet.debtCoins
        });
      }
      if (wallet.pointsBalance < holdAmount) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لحجز العملية', 402, {
          balance: wallet.pointsBalance,
          required: holdAmount
        });
      }

      const upd = await c.query(
        `UPDATE wallet_accounts
         SET points_balance = points_balance - $1,
             held_coins = held_coins + $1,
             updated_at = NOW()
         WHERE uid = $2 AND points_balance >= $1
         RETURNING *`,
        [holdAmount, uid]
      );
      if (upd.rowCount === 0) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ', 402);
      }
      const updatedWallet = this._mapWallet(upd.rows[0]);
      const holdId = `hold_${crypto.randomUUID()}`;
      const ins = await c.query(
        `INSERT INTO ledger_holds (hold_id, uid, purpose, reference_id, amount_coins, idempotency_key)
         VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
        [holdId, uid, String(purpose || 'MATCH_HOLD'), referenceId, holdAmount, key]
      );

      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'HOLD_RESERVE','POINTS',$3,$4,$5,$6,$7,$8)`,
        [crypto.randomUUID(), uid, -holdAmount, updatedWallet.pointsBalance, `hold_res_${key}`, holdId, `حجز مؤقت (${purpose}) (-${holdAmount} نقطة)`, JSON.stringify({ holdId, purpose, referenceId })]
      );

      return { hold: this._mapHold(ins.rows[0]), wallet: updatedWallet, duplicate: false };
    });
  }

  async captureHold({
    holdId,
    captureAmount = null,
    entryType = 'MATCH_SPEND',
    description = null,
    creditPlatformRevenue = true,
    idempotencyKey = null
  }) {
    return this._withTx(async (c) => {
      const hRes = await c.query(`SELECT * FROM ledger_holds WHERE hold_id = $1 FOR UPDATE`, [holdId]);
      if (hRes.rowCount === 0) throw new CallEngineError('HOLD_NOT_FOUND', 'سجل الحجز غير موجود', 404);
      const hold = this._mapHold(hRes.rows[0]);
      const capKey = String(idempotencyKey || `capture_${holdId}`);

      if (hold.status !== 'ACTIVE') {
        const existingCap = await c.query(`SELECT 1 FROM ledger_entries WHERE idempotency_key IN ($1, $2)`, [`${capKey}_cap`, `${capKey}_rel`]);
        if (hold.status === 'CAPTURED' && existingCap.rowCount > 0) {
          const { wallet } = await this._ensureUserTx(c, { uid: hold.uid });
          return { hold, wallet, duplicate: true };
        }
        throw new CallEngineError('HOLD_ALREADY_SETTLED', 'تمت تسوية هذا الحجز مسبقاً', 409);
      }

      const remaining = hold.amountCoins - hold.capturedCoins - hold.releasedCoins;
      const toCapture = captureAmount === null || captureAmount === undefined ? remaining : Number(captureAmount);
      if (!Number.isInteger(toCapture) || toCapture < 0 || toCapture > remaining) {
        throw new CallEngineError('INVALID_CAPTURE_AMOUNT', 'مبلغ الالتقاط يتجاوز الرصيد المحجوز', 400);
      }
      const toRelease = remaining - toCapture;

      await this._ensureUserTx(c, { uid: hold.uid });
      const upd = await c.query(
        `UPDATE wallet_accounts
         SET held_coins = held_coins - $1,
             points_balance = points_balance + $2,
             total_points_spent = total_points_spent + $3,
             updated_at = NOW()
         WHERE uid = $4 AND held_coins >= $1
         RETURNING *`,
        [remaining, toRelease, toCapture, hold.uid]
      );
      if (upd.rowCount === 0) throw new CallEngineError('HELD_BALANCE_CORRUPT', 'رصيد الحجز غير متطابق', 409);
      const updatedWallet = this._mapWallet(upd.rows[0]);

      const hUpd = await c.query(
        `UPDATE ledger_holds
         SET captured_coins = captured_coins + $1,
             released_coins = released_coins + $2,
             status = 'CAPTURED',
             settled_at = NOW()
         WHERE hold_id = $3 RETURNING *`,
        [toCapture, toRelease, holdId]
      );

      if (toRelease > 0) {
        await c.query(
          `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
           VALUES ($1,$2,'HOLD_RELEASE','POINTS',$3,$4,$5,$6,$7,$8)`,
          [crypto.randomUUID(), hold.uid, toRelease, updatedWallet.pointsBalance, `${capKey}_rel`, holdId, `فك جزء غير مستخدم من الحجز (+${toRelease} نقطة)`, JSON.stringify({ holdId })]
        );
      }
      if (toCapture > 0) {
        await c.query(
          `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
           VALUES ($1,$2,$3,'HELD_POINTS',$4,$5,$6,$7,$8,$9)`,
          [crypto.randomUUID(), hold.uid, entryType, -toCapture, updatedWallet.heldCoins, `${capKey}_cap`, hold.referenceId || holdId, description || `تأكيد خصم محجوز (${toCapture} نقطة)`, JSON.stringify({ holdId, purpose: hold.purpose })]
        );
        if (creditPlatformRevenue) {
          await this._creditPlatformRevenueTx(c, {
            amount: toCapture,
            idempotencyKey: `${capKey}_plat`,
            referenceId: hold.referenceId || holdId,
            description: `إيراد منصة من التزام محجوز (${entryType}) (+${toCapture} نقطة)`,
            metadata: { uid: hold.uid, holdId, entryType }
          });
        }
      }

      return { hold: this._mapHold(hUpd.rows[0]), wallet: updatedWallet, duplicate: false };
    });
  }

  async releaseHold({ holdId, reason = 'HOLD_RELEASED', idempotencyKey = null }) {
    return this._withTx(async (c) => {
      const hRes = await c.query(`SELECT * FROM ledger_holds WHERE hold_id = $1 FOR UPDATE`, [holdId]);
      if (hRes.rowCount === 0) throw new CallEngineError('HOLD_NOT_FOUND', 'سجل الحجز غير موجود', 404);
      const hold = this._mapHold(hRes.rows[0]);
      if (hold.status === 'RELEASED') {
        const { wallet } = await this._ensureUserTx(c, { uid: hold.uid });
        return { hold, wallet, duplicate: true };
      }
      if (hold.status !== 'ACTIVE') {
        throw new CallEngineError('HOLD_ALREADY_SETTLED', 'لا يمكن فك حجز تم التقاطه مسبقاً', 409);
      }

      const remaining = hold.amountCoins - hold.capturedCoins - hold.releasedCoins;
      await this._ensureUserTx(c, { uid: hold.uid });
      const upd = await c.query(
        `UPDATE wallet_accounts
         SET held_coins = GREATEST(0, held_coins - $1),
             points_balance = points_balance + $1,
             updated_at = NOW()
         WHERE uid = $2 RETURNING *`,
        [remaining, hold.uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);
      const hUpd = await c.query(
        `UPDATE ledger_holds SET released_coins = released_coins + $1, status = 'RELEASED', settled_at = NOW() WHERE hold_id = $2 RETURNING *`,
        [remaining, holdId]
      );

      const relKey = String(idempotencyKey || `rel_${holdId}`);
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'HOLD_RELEASE','POINTS',$3,$4,$5,$6,$7,$8)
         ON CONFLICT (idempotency_key) DO NOTHING`,
        [crypto.randomUUID(), hold.uid, remaining, updatedWallet.pointsBalance, relKey, holdId, `استرجاع حجز غير مستخدم (${reason}) (+${remaining} نقطة)`, JSON.stringify({ holdId, reason })]
      );

      return { hold: this._mapHold(hUpd.rows[0]), wallet: updatedWallet, duplicate: false };
    });
  }

  async spendCoins({ uid, amount, entryType = 'MATCH_SPEND', description, idempotencyKey, referenceId = null, metadata = {} }) {
    return this._withTx(async (c) => {
      if (!ALLOWED_CLIENT_SPEND_ENTRY_TYPES.has(entryType)) {
        throw new CallEngineError('INVALID_ENTRY_TYPE', 'نوع عملية الخصم غير مسموح به', 400, { entryType });
      }
      const spendAmount = Number(amount);
      if (!Number.isInteger(spendAmount) || spendAmount <= 0) {
        throw new CallEngineError('INVALID_AMOUNT', 'قيمة الخصم غير صالحة', 400);
      }
      const key = String(idempotencyKey || `spend_${uid}_${crypto.randomUUID()}`);
      const existing = await c.query(`SELECT * FROM ledger_entries WHERE idempotency_key = $1`, [key]);
      if (existing.rowCount > 0) {
        const { wallet } = await this._ensureUserTx(c, { uid });
        return { wallet, duplicate: true };
      }

      const { user, wallet } = await this._ensureUserTx(c, { uid });
      assertAdultVerified(user);
      if ((wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن استخدام النقاط لوجود مديونية مستردات على الحساب. يرجى سداد المديونية أولاً.', 403, {
          debtCoins: wallet.debtCoins
        });
      }
      if (wallet.pointsBalance < spendAmount) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لإتمام العملية', 402);
      }

      const upd = await c.query(
        `UPDATE wallet_accounts
         SET points_balance = points_balance - $1,
             total_points_spent = total_points_spent + $1,
             updated_at = NOW()
         WHERE uid = $2 AND points_balance >= $1
         RETURNING *`,
        [spendAmount, uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);
      const safeDescription = sanitizeSpendDescription(entryType, description);
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,$3,'POINTS',$4,$5,$6,$7,$8,$9)`,
        [crypto.randomUUID(), uid, entryType, -spendAmount, updatedWallet.pointsBalance, key, referenceId, safeDescription, JSON.stringify(metadata)]
      );

      await this._creditPlatformRevenueTx(c, {
        amount: spendAmount,
        idempotencyKey: `${key}_plat`,
        referenceId,
        description: `إيراد منصة (${entryType}) (+${spendAmount} نقطة)`,
        metadata: { uid, entryType }
      });

      return { wallet: updatedWallet, duplicate: false };
    });
  }

  async claimDailyBonus({ uid, deviceId = null }) {
    return this._withTx(async (c) => {
      const { user, wallet } = await this._ensureUserTx(c, { uid });
      assertAccountActive(user);
      const nowMs = this.now();
      if (wallet.lastDailyClaimMs > 0 && nowMs - wallet.lastDailyClaimMs < DAILY_CLAIM_COOLDOWN_MS) {
        throw new CallEngineError('DAILY_BONUS_COOLDOWN', 'تم استلام المكافأة اليومية بالفعل. حاول بعد 24 ساعة.', 429);
      }

      const config = await this._getEconomyConfigTx(c);
      const dayKey = new Date(nowMs).toISOString().slice(0, 10);
      const deviceHash = hashDeviceId(deviceId);

      if (deviceHash) {
        const dRes = await c.query(
          `SELECT * FROM daily_device_claims WHERE device_hash = $1 AND day_key = $2 FOR UPDATE`,
          [deviceHash, dayKey]
        );
        const dRow = dRes.rows[0];
        if (dRow) {
          const lastDevMs = Number(dRow.last_daily_claim_ms || 0);
          if (lastDevMs > 0 && nowMs - lastDevMs < DAILY_CLAIM_COOLDOWN_MS) {
            throw new CallEngineError('DAILY_BONUS_DEVICE_COOLDOWN', 'تم استلام المكافأة اليومية على هذا الجهاز بالفعل اليوم.', 429);
          }
          if (Number(dRow.free_coins_claimed || 0) + DAILY_BONUS_COINS > config.dailyFreeRewardCapCoins) {
            throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية على هذا الجهاز.', 429);
          }
        }
      }

      const freeRes = await c.query(
        `SELECT COALESCE(SUM(delta), 0)::bigint AS total_free
         FROM ledger_entries
         WHERE uid = $1 AND entry_type IN ('DAILY_FREE', 'MISSION_REWARD')
           AND (
             metadata->>'dayKey' = $2
             OR (metadata->>'dayKey' IS NULL AND created_at >= ($2 || 'T00:00:00Z')::timestamptz AND created_at < (($2::date + INTERVAL '1 day')::timestamptz))
           )`,
        [uid, dayKey]
      );
      const userFreeToday = Number(freeRes.rows[0]?.total_free || 0);
      if (userFreeToday + DAILY_BONUS_COINS > config.dailyFreeRewardCapCoins) {
        throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية لهذا الحساب.', 429);
      }

      const upd = await c.query(
        `UPDATE wallet_accounts
         SET points_balance = points_balance + $1,
             total_accumulated_points = total_accumulated_points + $1,
             last_daily_claim_ms = $2,
             updated_at = NOW()
         WHERE uid = $3 RETURNING *`,
        [DAILY_BONUS_COINS, nowMs, uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);

      if (deviceHash) {
        await c.query(
          `INSERT INTO daily_device_claims (device_hash, day_key, last_daily_claim_ms, free_coins_claimed)
           VALUES ($1,$2,$3,$4)
           ON CONFLICT (device_hash, day_key) DO UPDATE SET
             last_daily_claim_ms = EXCLUDED.last_daily_claim_ms,
             free_coins_claimed = daily_device_claims.free_coins_claimed + $4,
             updated_at = NOW()`,
          [deviceHash, dayKey, nowMs, DAILY_BONUS_COINS]
        );
      }

      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, description, metadata)
         VALUES ($1,$2,'DAILY_FREE','POINTS',$3,$4,$5,$6,$7)`,
        [crypto.randomUUID(), uid, DAILY_BONUS_COINS, updatedWallet.pointsBalance, `daily_${uid}_${dayKey}_${nowMs}`, `مكافأة الدخول اليومي (+${DAILY_BONUS_COINS} نقطة)`, JSON.stringify({ dayKey })]
      );
      return { wallet: updatedWallet, creditedCoins: DAILY_BONUS_COINS };
    });
  }

  async claimMissionReward({ uid, missionId, deviceId = null }) {
    return this._withTx(async (c) => {
      const cleanId = String(missionId || '').trim();
      const mission = SERVER_MISSION_CATALOG[cleanId];
      if (!mission) {
        throw new CallEngineError('INVALID_MISSION', 'معرف المهمة غير صالح أو غير موثق من الخادم', 400);
      }
      const { user } = await this._ensureUserTx(c, { uid });
      assertAccountActive(user);

      const nowMs = this.now();
      const dayKey = new Date(nowMs).toISOString().slice(0, 10);
      const idempotencyKey = `mission_${uid}_${dayKey}_${cleanId}`;
      const existing = await c.query(`SELECT * FROM ledger_entries WHERE idempotency_key = $1`, [idempotencyKey]);
      if (existing.rowCount > 0) {
        throw new CallEngineError('MISSION_ALREADY_CLAIMED', 'تم استلام مكافأة هذه المهمة اليوم بالفعل.', 409);
      }

      const actRes = await c.query(
        `SELECT activity_count FROM daily_activity_counters WHERE uid = $1 AND day_key = $2 AND activity_type = $3`,
        [uid, dayKey, mission.requiredActivity]
      );
      const activityCount = Number(actRes.rows[0]?.activity_count || 0);
      if (activityCount < mission.minCount) {
        throw new CallEngineError(
          'MISSION_NOT_VERIFIED',
          `لم يتم تسجيل نشاط فعلي كافٍ على الخادم لإكمال مهمة (${mission.description}).`,
          403
        );
      }

      const config = await this._getEconomyConfigTx(c);
      const rewardCoins = mission.rewardCoins;
      const freeRes = await c.query(
        `SELECT COALESCE(SUM(delta), 0)::bigint AS total_free
         FROM ledger_entries
         WHERE uid = $1 AND entry_type IN ('DAILY_FREE', 'MISSION_REWARD')
           AND (
             metadata->>'dayKey' = $2
             OR (metadata->>'dayKey' IS NULL AND created_at >= ($2 || 'T00:00:00Z')::timestamptz AND created_at < (($2::date + INTERVAL '1 day')::timestamptz))
           )`,
        [uid, dayKey]
      );
      const userFreeToday = Number(freeRes.rows[0]?.total_free || 0);
      if (userFreeToday + rewardCoins > config.dailyFreeRewardCapCoins) {
        throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية لهذا الحساب.', 429);
      }

      const deviceHash = hashDeviceId(deviceId);
      if (deviceHash) {
        const dRes = await c.query(
          `SELECT free_coins_claimed FROM daily_device_claims WHERE device_hash = $1 AND day_key = $2 FOR UPDATE`,
          [deviceHash, dayKey]
        );
        const claimedOnDevice = Number(dRes.rows[0]?.free_coins_claimed || 0);
        if (claimedOnDevice + rewardCoins > config.dailyFreeRewardCapCoins) {
          throw new CallEngineError('DAILY_REWARD_CAP_REACHED', 'تم بلوغ الحد الأقصى اليومي للنقاط المجانية على هذا الجهاز.', 429);
        }
        await c.query(
          `INSERT INTO daily_device_claims (device_hash, day_key, last_daily_claim_ms, free_coins_claimed)
           VALUES ($1,$2,0,$3)
           ON CONFLICT (device_hash, day_key) DO UPDATE SET
             free_coins_claimed = daily_device_claims.free_coins_claimed + $3,
             updated_at = NOW()`,
          [deviceHash, dayKey, rewardCoins]
        );
      }

      const upd = await c.query(
        `UPDATE wallet_accounts
         SET points_balance = points_balance + $1,
             total_accumulated_points = total_accumulated_points + $1,
             updated_at = NOW()
         WHERE uid = $2 RETURNING *`,
        [rewardCoins, uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'MISSION_REWARD','POINTS',$3,$4,$5,$6,$7,$8)`,
        [crypto.randomUUID(), uid, rewardCoins, updatedWallet.pointsBalance, idempotencyKey, cleanId, `مكافأة مهمة يومية موثقة (${cleanId}) (+${rewardCoins} نقطة)`, JSON.stringify({ dayKey, missionId: cleanId })]
      );
      return { wallet: updatedWallet, creditedCoins: rewardCoins };
    });
  }

  async applyForHost({ uid, agencyName = '' }) {
    return this._withTx(async (c) => {
      const { user } = await this._ensureUserTx(c, { uid });
      assertAdultVerified(user);
      const cleanAgency = String(agencyName || '').trim();
      const res = await c.query(
        `UPDATE users
         SET host_status = CASE WHEN is_host = FALSE AND host_status <> 'APPROVED' THEN 'PENDING' ELSE host_status END,
             agency_name = CASE WHEN $1 <> '' THEN $1 ELSE agency_name END,
             updated_at = NOW()
         WHERE uid = $2 RETURNING *`,
        [cleanAgency, uid]
      );
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'HOST_APPLICATION_SUBMITTED',$2,$3)`,
        [uid, uid, JSON.stringify({ agencyName: cleanAgency })]
      );
      return { user: this._mapUser(res.rows[0]) };
    });
  }

  async sendGiftAtomic({ senderUid, recipientUid, giftId, giftName = null, coinsCost = null, idempotencyKey, streamId = null }) {
    return this._withTx(async (c) => {
      if (!recipientUid || senderUid === recipientUid) {
        throw new CallEngineError('INVALID_RECIPIENT', 'لا يمكن إرسال الهدية لهذا المستلم', 400);
      }

      const sender = await this._ensureUserTx(c, { uid: senderUid });
      assertAdultVerified(sender.user);
      if ((sender.wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن إرسال الهدايا لوجود مديونية مستردات على الحساب.', 403, {
          debtCoins: sender.wallet.debtCoins
        });
      }

      const catalogGift = validateAndResolveGift({ giftId, coinsCost });
      const cost = catalogGift.coinsCost;
      const authoritativeGiftName = catalogGift.name;

      const key = String(idempotencyKey || `gift_${senderUid}_${crypto.randomUUID()}`);
      const existing = await c.query(`SELECT * FROM ledger_entries WHERE idempotency_key = $1`, [key]);
      if (existing.rowCount > 0) {
        const { wallet } = await this._ensureUserTx(c, { uid: senderUid });
        return { senderWallet: wallet, duplicate: true, coinsCost: cost };
      }

      const recipient = await this._ensureUserTx(c, { uid: recipientUid });
      if (sender.wallet.pointsBalance < cost) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لإرسال هذه الهدية', 402);
      }

      const config = await this._getEconomyConfigTx(c);
      const agMemRes = await c.query(`SELECT parent_agent_uid FROM agency_memberships WHERE uid = $1`, [recipientUid]);
      const parentUid = agMemRes.rows[0]?.parent_agent_uid || null;
      const agent = parentUid ? await this._ensureUserTx(c, { uid: parentUid }) : null;

      const { hostShare, agentShare, platformShare } = computeRevenueSplit({
        cost,
        hostCommissionPercent: recipient.user.hostCommissionPercent || config.hostCommissionPercent,
        agentCommissionPercent: agent ? (agent.user.agentCommissionPercent || config.agentCommissionPercent) : 0,
        hasAgent: Boolean(agent)
      });

      const sUpd = await c.query(
        `UPDATE wallet_accounts SET points_balance = points_balance - $1, total_points_spent = total_points_spent + $1, updated_at = NOW() WHERE uid = $2 AND points_balance >= $1 RETURNING *`,
        [cost, senderUid]
      );
      const rUpd = await c.query(
        `UPDATE wallet_accounts SET host_earnings_coins = host_earnings_coins + $1, updated_at = NOW() WHERE uid = $2 RETURNING *`,
        [hostShare, recipientUid]
      );

      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'GIFT_SENT','POINTS',$3,$4,$5,$6,$7,$8)`,
        [crypto.randomUUID(), senderUid, -cost, Number(sUpd.rows[0].points_balance), key, streamId || recipientUid, `إرسال هدية ${authoritativeGiftName} (-${cost} نقطة)`, JSON.stringify({ recipientUid, giftId: catalogGift.giftId, streamId })]
      );
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'GIFT_RECEIVE','HOST_EARNINGS',$3,$4,$5,$6,$7,$8)`,
        [crypto.randomUUID(), recipientUid, hostShare, Number(rUpd.rows[0].host_earnings_coins), `${key}_rx`, streamId || senderUid, `أرباح هدية ${authoritativeGiftName} (+${hostShare} نقطة أرباح)`, JSON.stringify({ senderUid, giftId: catalogGift.giftId, streamId })]
      );

      if (agent && agentShare > 0) {
        const aUpd = await c.query(
          `UPDATE wallet_accounts SET sub_agents_earnings_coins = sub_agents_earnings_coins + $1, updated_at = NOW() WHERE uid = $2 RETURNING *`,
          [agentShare, parentUid]
        );
        await c.query(
          `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
           VALUES ($1,$2,'AGENT_COMMISSION','AGENCY_EARNINGS',$3,$4,$5,$6,$7,$8)`,
          [crypto.randomUUID(), parentUid, agentShare, Number(aUpd.rows[0].sub_agents_earnings_coins), `${key}_ag`, recipientUid, `عمولة وكالة على هدية مضيف (+${agentShare} نقطة)`, JSON.stringify({ hostUid: recipientUid, giftId: catalogGift.giftId })]
        );
      }

      if (platformShare > 0) {
        await this._creditPlatformRevenueTx(c, {
          amount: platformShare,
          idempotencyKey: `${key}_plat`,
          referenceId: streamId || recipientUid,
          description: `حصة المنصة من هدية ${authoritativeGiftName} (+${platformShare} نقطة)`,
          metadata: { senderUid, recipientUid, giftId: catalogGift.giftId }
        });
      }

      await this._recordActivityTx(c, { uid: senderUid, activityType: 'GIFT_SENT', count: 1 });

      return {
        senderWallet: this._mapWallet(sUpd.rows[0]),
        recipientEarningsAdded: hostShare,
        agentCommissionAdded: agentShare,
        platformRevenueAdded: platformShare,
        coinsCost: cost,
        giftName: authoritativeGiftName,
        duplicate: false
      };
    });
  }

  async submitAgeVerification({ uid, displayName = '', email = '', dob, acceptedTermsAt, acceptedPrivacyAt, deviceId = null }) {
    const nowMs = this.now();
    const deviceHash = hashDeviceId(deviceId);

    const preCheck = await this._withTx(async (c) => {
      const { user } = await this._ensureUserTx(c, { uid, displayName, email });
      assertAccountActive(user);
      let deviceLocked = false;
      if (deviceHash) {
        const lockRes = await c.query(`SELECT 1 FROM underage_device_locks WHERE device_hash = $1`, [deviceHash]);
        deviceLocked = lockRes.rowCount > 0;
      }
      if (user.underageLocked || deviceLocked) {
        throw new CallEngineError(
          'UNDERAGE_LOCKED',
          'عذراً، هذا الحساب أو الجهاز مقيد نهائياً لعدم استيفاء شرط العمر (+18 سنة).',
          403
        );
      }

      const ageCheck = computeExactAgeFromDob(dob, nowMs);
      if (!ageCheck.isAdult) {
        await c.query(
          `UPDATE users
           SET underage_locked = TRUE,
               underage_locked_reason = 'UNDERAGE_SUBMISSION',
               underage_locked_at = $1,
               age_verified = FALSE,
               age_verification_status = 'REJECTED_UNDERAGE',
               dob = $2,
               age_years = $3,
               updated_at = NOW()
           WHERE uid = $4`,
          [nowMs, ageCheck.normalizedDob, ageCheck.ageYears, uid]
        );
        if (deviceHash) {
          await c.query(
            `INSERT INTO underage_device_locks (device_hash, uid, dob_submitted, reason)
             VALUES ($1,$2,$3,'UNDERAGE_SUBMISSION')
             ON CONFLICT (device_hash) DO NOTHING`,
            [deviceHash, uid, ageCheck.normalizedDob]
          );
        }
        await c.query(
          `INSERT INTO age_verifications (uid, display_name, email, dob, age_years, status, reviewer_uid, rejection_reason, accepted_terms_at, accepted_privacy_at, reviewed_at)
           VALUES ($1,$2,$3,$4,$5,'REJECTED_UNDERAGE','system_age_gate','العمر المحسوب أقل من 18 عاماً',$6,$7,NOW())
           ON CONFLICT (uid) DO UPDATE SET
             dob = EXCLUDED.dob,
             age_years = EXCLUDED.age_years,
             status = 'REJECTED_UNDERAGE',
             reviewer_uid = 'system_age_gate',
             rejection_reason = 'العمر المحسوب أقل من 18 عاماً',
             reviewed_at = NOW()`,
          [uid, user.displayName, user.email, ageCheck.normalizedDob, ageCheck.ageYears, Number(acceptedTermsAt || nowMs), Number(acceptedPrivacyAt || nowMs)]
        );
        await c.query(
          `INSERT INTO audit_logs (actor_uid, action, target_id, details)
           VALUES ($1,'UNDERAGE_ATTEMPT_LOCKED',$2,$3)`,
          [uid, uid, JSON.stringify({ dob: ageCheck.normalizedDob, ageYears: ageCheck.ageYears, deviceLocked: Boolean(deviceHash) })]
        );
        return { underageLockedNow: true, ageYears: ageCheck.ageYears };
      }

      if (!acceptedTermsAt || !acceptedPrivacyAt) {
        throw new CallEngineError('LEGAL_CONSENT_REQUIRED', 'الموافقة على الشروط وسياسة الخصوصية مطلوبة', 400);
      }
      return { underageLockedNow: false, ageCheck };
    });

    if (preCheck.underageLockedNow) {
      throw new CallEngineError(
        'UNDERAGE_FORBIDDEN',
        'التطبيق مخصص للبالغين (+18) فقط. تم تقييد الحساب والجهاز.',
        403,
        { underageLocked: true, ageYears: preCheck.ageYears }
      );
    }

    const { ageCheck } = preCheck;
    let providerOutcome = { status: 'PENDING_REVIEW', provider: 'manual_admin_review', rejectionReason: null };
    if (this.ageVerificationProvider && typeof this.ageVerificationProvider.verify === 'function') {
      const ext = await this.ageVerificationProvider.verify({
        uid,
        dob: ageCheck.normalizedDob,
        ageYears: ageCheck.ageYears
      });
      if (ext?.status) {
        providerOutcome = {
          status: String(ext.status).toUpperCase(),
          provider: ext.provider || 'external_idv',
          rejectionReason: ext.rejectionReason || null
        };
      }
    }

    return this._withTx(async (c) => {
      const { user } = await this._ensureUserTx(c, { uid, displayName, email });
      const isApproved = providerOutcome.status === 'APPROVED';
      const reviewerUid = providerOutcome.status === 'PENDING_REVIEW' ? null : `provider:${providerOutcome.provider}`;
      await c.query(
        `UPDATE users
         SET dob = $1,
             age_years = $2,
             accepted_terms_at = $3,
             accepted_privacy_at = $4,
             age_verified = $5,
             age_verification_status = $6,
             updated_at = NOW()
         WHERE uid = $7`,
        [ageCheck.normalizedDob, ageCheck.ageYears, Number(acceptedTermsAt), Number(acceptedPrivacyAt), isApproved, providerOutcome.status, uid]
      );
      const res = await c.query(
        `INSERT INTO age_verifications (uid, display_name, email, dob, age_years, status, reviewer_uid, rejection_reason, accepted_terms_at, accepted_privacy_at, reviewed_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
         ON CONFLICT (uid) DO UPDATE SET
           dob = EXCLUDED.dob,
           age_years = EXCLUDED.age_years,
           status = EXCLUDED.status,
           reviewer_uid = EXCLUDED.reviewer_uid,
           rejection_reason = EXCLUDED.rejection_reason,
           accepted_terms_at = EXCLUDED.accepted_terms_at,
           accepted_privacy_at = EXCLUDED.accepted_privacy_at,
           submitted_at = NOW(),
           reviewed_at = EXCLUDED.reviewed_at
         RETURNING *`,
        [
          uid,
          user.displayName,
          user.email,
          ageCheck.normalizedDob,
          ageCheck.ageYears,
          providerOutcome.status,
          reviewerUid,
          providerOutcome.rejectionReason,
          Number(acceptedTermsAt),
          Number(acceptedPrivacyAt),
          providerOutcome.status === 'PENDING_REVIEW' ? null : new Date(nowMs).toISOString()
        ]
      );
      return { ...this._mapAgeVerification(res.rows[0]), provider: providerOutcome.provider };
    });
  }

  async listAgeVerifications(status = 'PENDING_REVIEW') {
    const target = String(status || '').toUpperCase();
    const res = !target || target === 'ALL'
      ? await this.pool.query(`SELECT * FROM age_verifications ORDER BY submitted_at DESC LIMIT 200`)
      : await this.pool.query(`SELECT * FROM age_verifications WHERE status = $1 ORDER BY submitted_at DESC LIMIT 200`, [target]);
    return res.rows.map((r) => this._mapAgeVerification(r));
  }

  async reviewAgeVerification({ reviewerUid, targetUid, decision, reason = null }) {
    return this._withTx(async (c) => {
      const norm = String(decision || '').toUpperCase();
      if (norm !== 'APPROVED' && norm !== 'REJECTED') throw new CallEngineError('INVALID_DECISION', 'قرار غير صالح', 400);
      const rejReason = norm === 'REJECTED' ? (reason || 'يرجى مراجعة البيانات') : null;
      const res = await c.query(
        `UPDATE age_verifications SET status=$1, reviewer_uid=$2, rejection_reason=$3, reviewed_at=NOW() WHERE uid=$4 RETURNING *`,
        [norm, reviewerUid, rejReason, targetUid]
      );
      if (res.rowCount === 0) throw new CallEngineError('SUBMISSION_NOT_FOUND', 'طلب غير موجود', 404);
      const uRes = await c.query(
        `UPDATE users SET age_verified=$1, age_verification_status=$2, updated_at=NOW() WHERE uid=$3 RETURNING *`,
        [norm === 'APPROVED', norm, targetUid]
      );
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'REVIEW_AGE_VERIFICATION',$2,$3)`,
        [reviewerUid, targetUid, JSON.stringify({ decision: norm, reason: rejReason })]
      );
      return { submission: this._mapAgeVerification(res.rows[0]), user: this._mapUser(uRes.rows[0]) };
    });
  }

  async createPayoutRequest({ uid, amountCoins, payoutMethod = 'BANK_TRANSFER', bankAccountHolder, bankName, ibanOrAccount }) {
    return this._withTx(async (c) => {
      const coins = Number(amountCoins);
      if (!Number.isInteger(coins) || coins < MIN_PAYOUT_COINS) {
        throw new CallEngineError('MIN_PAYOUT_NOT_MET', `الحد الأدنى لسحب الأرباح هو ${MIN_PAYOUT_COINS} نقطة`, 400);
      }
      if (!bankAccountHolder || !bankName || !ibanOrAccount) {
        throw new CallEngineError('MISSING_BANK_DETAILS', 'بيانات الحساب البنكي/التحويل مطلوبة بالكامل', 400);
      }
      const { user, wallet } = await this._ensureUserTx(c, { uid });
      assertAdultVerified(user);
      if ((wallet.debtCoins || 0) > 0) {
        throw new CallEngineError('ACCOUNT_HAS_DEBT', 'لا يمكن طلب سحب الأرباح لوجود مديونية مستردات على الحساب.', 403, {
          debtCoins: wallet.debtCoins
        });
      }
      if (!user.isAgent && !user.isHost) throw new CallEngineError('PAYOUT_ROLE_REQUIRED', 'متاح للمضيفين والوكلاء فقط', 403);

      const activeRes = await c.query(
        `SELECT id FROM payout_requests WHERE uid = $1 AND status IN ('PENDING_REVIEW', 'APPROVED') LIMIT 1`,
        [uid]
      );
      if (activeRes.rowCount > 0) {
        throw new CallEngineError('ACTIVE_PAYOUT_EXISTS', 'لديك طلب سحب نشط قيد المراجعة بالفعل. انتظر البت فيه أولاً.', 409);
      }

      const avail = wallet.hostEarningsCoins + wallet.subAgentsEarningsCoins;
      if (avail < coins) throw new CallEngineError('INSUFFICIENT_EARNINGS', 'رصيد الأرباح غير كافٍ', 402);

      let rem = coins;
      const fromSub = Math.min(wallet.subAgentsEarningsCoins, rem);
      rem -= fromSub;
      const upd = await c.query(
        `UPDATE wallet_accounts
         SET sub_agents_earnings_coins = sub_agents_earnings_coins - $1,
             host_earnings_coins = host_earnings_coins - $2,
             reserved_payout_coins = reserved_payout_coins + $3,
             updated_at = NOW()
         WHERE uid = $4 RETURNING *`,
        [fromSub, rem, coins, uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);

      const reqId = `payout_${crypto.randomUUID()}`;
      const usdCents = Math.floor((coins * 100) / COINS_PER_USD);
      const rawIban = String(ibanOrAccount).trim();
      const masked = maskAccountNumber(rawIban);
      const encryptedIban = encryptSensitiveField(rawIban, this.bankKey);
      const nowMs = this.now();
      const earliestIso = new Date(nowMs + this.payoutHoldMs).toISOString();

      const ins = await c.query(
        `INSERT INTO payout_requests (id, uid, amount_coins, usd_equivalent_cents, payout_method, bank_account_holder, bank_name, iban_or_account, iban_masked, from_sub_agents_coins, from_host_coins, earliest_review_at, status)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,'PENDING_REVIEW') RETURNING *`,
        [reqId, uid, coins, usdCents, payoutMethod, String(bankAccountHolder).trim(), String(bankName).trim(), encryptedIban, masked, fromSub, rem, earliestIso]
      );

      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'WITHDRAWAL_RESERVE','RESERVED_PAYOUT',$3,$4,$5,$6,$7,$8)`,
        [crypto.randomUUID(), uid, coins, updatedWallet.reservedPayoutCoins, `payout_req_${reqId}`, reqId, `طلب سحب أرباح قيد المراجعة (-${coins} نقطة أرباح)`, JSON.stringify({ fromSubAgentsCoins: fromSub, fromHostCoins: rem })]
      );

      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'CREATE_PAYOUT_REQUEST',$2,$3)`,
        [uid, reqId, JSON.stringify({ amountCoins: coins, usdEquivalentCents: usdCents, ibanMasked: masked })]
      );

      return { request: this._mapPayoutRequest(ins.rows[0]), wallet: updatedWallet };
    });
  }

  async listPayoutRequests({ uid, isAdmin = false, status = null } = {}) {
    const res = isAdmin
      ? await this.pool.query(`SELECT p.*, u.display_name FROM payout_requests p LEFT JOIN users u ON u.uid = p.uid ORDER BY p.created_at DESC LIMIT 200`)
      : await this.pool.query(`SELECT p.*, u.display_name FROM payout_requests p LEFT JOIN users u ON u.uid = p.uid WHERE p.uid = $1 ORDER BY p.created_at DESC LIMIT 200`, [uid]);
    const mapped = res.rows.map((r) => this._mapPayoutRequest(r));
    return status ? mapped.filter((r) => r.status === String(status).toUpperCase()) : mapped;
  }

  async reviewPayoutRequest({ reviewerUid, requestId, decision, note = null, paymentReference = null }) {
    return this._withTx(async (c) => {
      const norm = String(decision || '').toUpperCase();
      if (!['APPROVED', 'PAID', 'REJECTED'].includes(norm)) throw new CallEngineError('INVALID_DECISION', 'قرار غير صالح', 400);
      const rRes = await c.query(`SELECT * FROM payout_requests WHERE id = $1 FOR UPDATE`, [requestId]);
      if (rRes.rowCount === 0) throw new CallEngineError('PAYOUT_NOT_FOUND', 'طلب السحب غير موجود', 404);
      const reqRow = rRes.rows[0];
      if (reviewerUid === reqRow.uid) {
        throw new CallEngineError('SELF_APPROVAL_FORBIDDEN', 'لا يمكن للمستخدم مراجعة أو اعتماد طلب السحب الخاص به', 403);
      }
      if (reqRow.status === 'PAID' || reqRow.status === 'REJECTED') {
        throw new CallEngineError('PAYOUT_ALREADY_FINALIZED', 'تم البت في هذا الطلب مسبقاً', 409);
      }

      const earliestMs = reqRow.earliest_review_at ? new Date(reqRow.earliest_review_at).getTime() : 0;
      if ((norm === 'APPROVED' || norm === 'PAID') && this.now() < earliestMs) {
        throw new CallEngineError('PAYOUT_HOLD_ACTIVE', 'طلب السحب لا يزال ضمن فترة الحجز الأمني (24 ساعة) قبل الاعتماد أو الصرف.', 409, {
          earliestReviewAtMs: earliestMs
        });
      }

      const amountCoins = Number(reqRow.amount_coins);
      const fromSub = Number(reqRow.from_sub_agents_coins || 0);
      const fromHost = reqRow.from_host_coins !== undefined && reqRow.from_host_coins !== null
        ? Number(reqRow.from_host_coins)
        : Math.max(0, amountCoins - fromSub);

      if (norm === 'REJECTED') {
        const wUpd = await c.query(
          `UPDATE wallet_accounts
           SET reserved_payout_coins = GREATEST(0, reserved_payout_coins - $1),
               sub_agents_earnings_coins = sub_agents_earnings_coins + $2,
               host_earnings_coins = host_earnings_coins + $3,
               updated_at = NOW()
           WHERE uid = $4 RETURNING *`,
          [amountCoins, fromSub, fromHost, reqRow.uid]
        );
        const wAfter = this._mapWallet(wUpd.rows[0]);
        if (fromSub > 0) {
          await c.query(
            `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description)
             VALUES ($1,$2,'WITHDRAWAL_REFUND','AGENCY_EARNINGS',$3,$4,$5,$6,$7)`,
            [crypto.randomUUID(), reqRow.uid, fromSub, wAfter.subAgentsEarningsCoins, `payout_refund_ag_${requestId}`, requestId, `استرداد طلب سحب مرفوض إلى أرباح الوكالة (+${fromSub} نقطة)`]
          );
        }
        if (fromHost > 0) {
          await c.query(
            `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description)
             VALUES ($1,$2,'WITHDRAWAL_REFUND','HOST_EARNINGS',$3,$4,$5,$6,$7)`,
            [crypto.randomUUID(), reqRow.uid, fromHost, wAfter.hostEarningsCoins, `payout_refund_host_${requestId}`, requestId, `استرداد طلب سحب مرفوض إلى أرباح المضيف (+${fromHost} نقطة)`]
          );
        }
      } else if (norm === 'PAID') {
        const wUpd = await c.query(
          `UPDATE wallet_accounts
           SET reserved_payout_coins = GREATEST(0, reserved_payout_coins - $1),
               updated_at = NOW()
           WHERE uid = $2 RETURNING *`,
          [amountCoins, reqRow.uid]
        );
        const wAfter = this._mapWallet(wUpd.rows[0]);
        await c.query(
          `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description)
           VALUES ($1,$2,'WITHDRAWAL','RESERVED_PAYOUT',$3,$4,$5,$6,$7)`,
          [crypto.randomUUID(), reqRow.uid, -amountCoins, wAfter.reservedPayoutCoins, `payout_paid_${requestId}`, paymentReference || requestId, `صرف أرباح معتمد (${amountCoins} نقطة)`]
        );
      }

      const upd = await c.query(
        `UPDATE payout_requests SET status=$1, reviewer_uid=$2, reviewer_note=$3, payment_reference=COALESCE($4, payment_reference), reviewed_at=NOW() WHERE id=$5 RETURNING *`,
        [norm, reviewerUid, note, paymentReference, requestId]
      );
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,$2,$3,$4)`,
        [reviewerUid, `PAYOUT_${norm}`, requestId, JSON.stringify({ uid: reqRow.uid, amountCoins, note, paymentReference })]
      );
      const wRes = await c.query(`SELECT * FROM wallet_accounts WHERE uid = $1`, [reqRow.uid]);
      return { request: this._mapPayoutRequest(upd.rows[0]), wallet: this._mapWallet(wRes.rows[0]) };
    });
  }

  async setUserStatus({ reviewerUid, targetUid, status, reason = '' }) {
    return this._withTx(async (c) => {
      const normalized = String(status || '').trim().toUpperCase();
      if (!VALID_USER_STATUSES.has(normalized)) {
        throw new CallEngineError('INVALID_STATUS', 'الحالة يجب أن تكون ACTIVE أو SUSPENDED أو BANNED', 400);
      }
      const { user } = await this._ensureUserTx(c, { uid: targetUid });
      const nowMs = this.now();
      const prevStatus = user.status || 'ACTIVE';
      const nextReason = normalized === 'ACTIVE' ? null : (String(reason || '').trim() || 'إجراء إداري');

      const res = await c.query(
        `UPDATE users
         SET status = $1,
             status_reason = $2,
             status_updated_at = $3,
             status_updated_by = $4,
             updated_at = NOW()
         WHERE uid = $5
         RETURNING *`,
        [normalized, nextReason, nowMs, reviewerUid, targetUid]
      );
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'USER_STATUS_UPDATED',$2,$3)`,
        [reviewerUid, targetUid, JSON.stringify({ previousStatus: prevStatus, status: normalized, reason: nextReason })]
      );
      return { user: this._mapUser(res.rows[0]) };
    });
  }

  async listHostApplications(status = 'PENDING') {
    const target = String(status || 'PENDING').toUpperCase();
    const res = target === 'ALL'
      ? await this.pool.query(`SELECT * FROM users WHERE deleted_at IS NULL AND host_status <> 'NONE' ORDER BY updated_at DESC LIMIT 200`)
      : await this.pool.query(`SELECT * FROM users WHERE deleted_at IS NULL AND host_status = $1 ORDER BY updated_at DESC LIMIT 200`, [target]);
    return res.rows.map((r) => this._mapUser(r));
  }

  async reviewHostApplication({ reviewerUid, targetUid, decision, hostCommissionPercent = null, reason = null }) {
    return this._withTx(async (c) => {
      const normalized = String(decision || '').trim().toUpperCase();
      if (normalized !== 'APPROVED' && normalized !== 'REJECTED') {
        throw new CallEngineError('INVALID_DECISION', 'القرار يجب أن يكون APPROVED أو REJECTED', 400);
      }
      const { user } = await this._ensureUserTx(c, { uid: targetUid });
      if (hostCommissionPercent !== null && hostCommissionPercent !== undefined) {
        validateCommissionPair(hostCommissionPercent, user.agentCommissionPercent);
      }
      const nextIsHost = normalized === 'APPROVED';
      const res = await c.query(
        `UPDATE users
         SET is_host = $1,
             host_status = $2,
             host_commission_percent = COALESCE($3, host_commission_percent),
             updated_at = NOW()
         WHERE uid = $4
         RETURNING *`,
        [nextIsHost, normalized, hostCommissionPercent ?? null, targetUid]
      );
      const updatedUser = this._mapUser(res.rows[0]);
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'REVIEW_HOST_APPLICATION',$2,$3)`,
        [
          reviewerUid,
          targetUid,
          JSON.stringify({
            decision: normalized,
            hostCommissionPercent: updatedUser.hostCommissionPercent,
            agencyName: updatedUser.agencyName,
            reason: reason || null
          })
        ]
      );
      return { user: updatedUser };
    });
  }

  async adminSetUserRolesAndCommission({
    reviewerUid,
    reviewerAdminRole = 'SUPER_ADMIN',
    targetUid,
    isAgent,
    isHost,
    isAdmin,
    adminRole,
    agentCommissionPercent,
    hostCommissionPercent,
    agencyName
  }) {
    return this._withTx(async (c) => {
      const isModifyingAdminPrivileges = typeof isAdmin === 'boolean' || adminRole !== undefined;
      if (isModifyingAdminPrivileges && reviewerUid !== 'system_auth_claims' && reviewerAdminRole !== 'SUPER_ADMIN') {
        throw new CallEngineError(
          'FORBIDDEN_SUPER_ADMIN_REQUIRED',
          'تعيين أو تعديل الأدوار الإدارية محصور بالمشرف الأعلى (SUPER_ADMIN) فقط.',
          403
        );
      }

      const { user } = await this._ensureUserTx(c, { uid: targetUid });
      if (agentCommissionPercent !== undefined || hostCommissionPercent !== undefined) {
        const nextHost = hostCommissionPercent !== undefined ? hostCommissionPercent : user.hostCommissionPercent;
        const nextAgent = agentCommissionPercent !== undefined ? agentCommissionPercent : user.agentCommissionPercent;
        validateCommissionPair(nextHost, nextAgent);
      }

      let nextIsAdmin = null;
      let nextAdminRole = null;
      if (adminRole !== undefined) {
        const upperRole = String(adminRole || 'NONE').trim().toUpperCase();
        if (!VALID_ADMIN_ROLES.has(upperRole)) {
          throw new CallEngineError('INVALID_ADMIN_ROLE', 'الدور الإداري غير معروف', 400);
        }
        nextAdminRole = upperRole;
        nextIsAdmin = upperRole !== 'NONE';
      } else if (typeof isAdmin === 'boolean') {
        nextIsAdmin = isAdmin;
        nextAdminRole = isAdmin ? (user.adminRole && user.adminRole !== 'NONE' ? user.adminRole : 'SUPER_ADMIN') : 'NONE';
      }

      const nextIsAgent = typeof isAgent === 'boolean' ? isAgent : user.isAgent;
      const resolvedAgencyId = nextIsAgent && !user.agencyId
        ? `ag_${targetUid.slice(0, 8)}`
        : user.agencyId;
      let resolvedAgencyName = agencyName !== undefined ? String(agencyName).trim() : null;
      if (nextIsAgent && !user.agencyId && !resolvedAgencyName) {
        resolvedAgencyName = user.agencyName || `وكالة ${user.displayName}`;
      }

      const res = await c.query(
        `UPDATE users SET
           is_agent = COALESCE($1, is_agent),
           is_host = COALESCE($2, is_host),
           host_status = CASE WHEN $2 = TRUE THEN 'APPROVED' WHEN $2 = FALSE THEN 'NONE' ELSE host_status END,
           is_admin = COALESCE($3, is_admin),
           admin_role = COALESCE($9, admin_role),
           agent_commission_percent = COALESCE($4, agent_commission_percent),
           host_commission_percent = COALESCE($5, host_commission_percent),
           agency_id = CASE WHEN $6 <> '' THEN $6 ELSE agency_id END,
           agency_name = COALESCE($8, agency_name),
           updated_at = NOW()
         WHERE uid = $7 RETURNING *`,
        [
          typeof isAgent === 'boolean' ? isAgent : null,
          typeof isHost === 'boolean' ? isHost : null,
          nextIsAdmin,
          agentCommissionPercent ?? null,
          hostCommissionPercent ?? null,
          resolvedAgencyId,
          targetUid,
          resolvedAgencyName,
          nextAdminRole
        ]
      );
      const updatedUser = this._mapUser(res.rows[0]);
      if (updatedUser.isAgent && resolvedAgencyId) {
        await c.query(
          `INSERT INTO agencies (id, owner_uid, name, country, commission_percent)
           VALUES ($1,$2,$3,$4,$5)
           ON CONFLICT (id) DO UPDATE SET commission_percent=EXCLUDED.commission_percent, name=CASE WHEN $3 <> '' THEN $3 ELSE agencies.name END`,
          [resolvedAgencyId, targetUid, updatedUser.agencyName || `وكالة ${updatedUser.displayName}`, updatedUser.country || '', updatedUser.agentCommissionPercent]
        );
      }
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'ADMIN_UPDATE_USER_ROLES',$2,$3)`,
        [
          reviewerUid,
          targetUid,
          JSON.stringify({
            isAgent: updatedUser.isAgent,
            isHost: updatedUser.isHost,
            isAdmin: updatedUser.isAdmin,
            adminRole: updatedUser.adminRole,
            agentCommissionPercent: updatedUser.agentCommissionPercent,
            hostCommissionPercent: updatedUser.hostCommissionPercent,
            agencyName: updatedUser.agencyName
          })
        ]
      );
      return updatedUser;
    });
  }

  async getAgencyDashboard(uid) {
    return this._withTx(async (c) => {
      const { user, wallet } = await this._ensureUserTx(c, { uid });
      const subs = await c.query(
        `SELECT m.*, u.display_name, u.country FROM agency_memberships m LEFT JOIN users u ON u.uid = m.uid WHERE m.parent_agent_uid = $1`,
        [uid]
      );
      const payouts = await c.query(`SELECT * FROM payout_requests WHERE uid = $1 ORDER BY created_at DESC LIMIT 50`, [uid]);
      return {
        user,
        wallet,
        subAgents: subs.rows.map((r) => ({
          id: r.uid,
          uid: r.uid,
          name: r.display_name || r.uid,
          country: r.country || '',
          agencyId: r.agency_id,
          parentAgentUid: r.parent_agent_uid,
          hostsCount: 0,
          monthlyRevenueCoins: 0,
          agentCommissionEarned: 0,
          status: 'نشط'
        })),
        payouts: payouts.rows.map((r) => this._mapPayoutRequest(r))
      };
    });
  }

  async recruitSubAgent({ agentUid, subAgentName, country = '', subAgentUid = null }) {
    return this._withTx(async (c) => {
      const { user } = await this._ensureUserTx(c, { uid: agentUid });
      if (!user.isAgent && !user.isAdmin) {
        throw new CallEngineError('AGENT_REQUIRED', 'هذه الميزة مخصصة للوكلاء المعتمدين فقط', 403);
      }
      if (!subAgentName || !String(subAgentName).trim()) {
        throw new CallEngineError('INVALID_NAME', 'اسم الوكيل الفرعي مطلوب', 400);
      }
      const agencyId = user.agencyId || `ag_${agentUid.slice(0, 8)}`;
      await c.query(
        `INSERT INTO agencies (id, owner_uid, name, country, commission_percent)
         VALUES ($1,$2,$3,$4,$5) ON CONFLICT (id) DO NOTHING`,
        [agencyId, agentUid, `وكالة ${user.displayName}`, user.country || '', user.agentCommissionPercent || 20]
      );
      const targetUid = subAgentUid || `sub_${crypto.randomUUID().slice(0, 10)}`;
      await c.query(
        `INSERT INTO users (uid, display_name, country, referral_code)
         VALUES ($1,$2,$3,$4)
         ON CONFLICT (uid) DO UPDATE SET display_name=EXCLUDED.display_name, country=EXCLUDED.country`,
        [targetUid, String(subAgentName).trim(), String(country || '').trim(), generateReferralCode(targetUid)]
      );
      await c.query(
        `INSERT INTO agency_memberships (uid, agency_id, role, parent_agent_uid, commission_percent)
         VALUES ($1,$2,'SUB_AGENT',$3,$4)
         ON CONFLICT (uid) DO UPDATE SET agency_id=EXCLUDED.agency_id, parent_agent_uid=EXCLUDED.parent_agent_uid`,
        [targetUid, agencyId, agentUid, user.agentCommissionPercent || 20]
      );
      const countRes = await c.query(`SELECT COUNT(*)::int AS cnt FROM agency_memberships WHERE parent_agent_uid=$1`, [agentUid]);
      return {
        membership: {
          id: targetUid,
          uid: targetUid,
          name: String(subAgentName).trim(),
          country: String(country || '').trim(),
          agencyId,
          parentAgentUid: agentUid,
          hostsCount: 0,
          monthlyRevenueCoins: 0,
          agentCommissionEarned: 0,
          status: 'قيد التفعيل'
        },
        subAgentsCount: countRes.rows[0].cnt
      };
    });
  }

  async createModerationReport({ reportType, targetUserId = null, streamId = null, reporterUid, reason, details = '' }) {
    const id = `rep_${crypto.randomUUID()}`;
    const res = await this.pool.query(
      `INSERT INTO moderation_reports (id, report_type, target_user_id, stream_id, reporter_uid, reason, details) VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`,
      [id, reportType || 'USER_PROFILE', targetUserId, streamId, reporterUid, reason, details]
    );
    return this._mapModerationReport(res.rows[0]);
  }

  async listModerationReports(status = 'PENDING_REVIEW') {
    const res = String(status || '').toUpperCase() === 'ALL'
      ? await this.pool.query(`SELECT * FROM moderation_reports ORDER BY created_at DESC LIMIT 200`)
      : await this.pool.query(`SELECT * FROM moderation_reports WHERE status = $1 ORDER BY created_at DESC LIMIT 200`, [String(status || 'PENDING_REVIEW').toUpperCase()]);
    return res.rows.map((r) => this._mapModerationReport(r));
  }

  async resolveModerationReport({ reviewerUid, reportId, status = 'RESOLVED', actionTaken = 'WARNING' }) {
    return this._withTx(async (c) => {
      const res = await c.query(
        `UPDATE moderation_reports SET status=$1, action_taken=$2, reviewer_uid=$3, reviewed_at=NOW() WHERE id=$4 RETURNING *`,
        [status, actionTaken, reviewerUid, reportId]
      );
      if (res.rowCount === 0) throw new CallEngineError('REPORT_NOT_FOUND', 'البلاغ غير موجود', 404);
      const mapped = this._mapModerationReport(res.rows[0]);
      if (mapped.targetUserId && (actionTaken === 'SUSPEND_USER' || actionTaken === 'BAN_USER')) {
        const targetStatus = actionTaken === 'BAN_USER' ? 'BANNED' : 'SUSPENDED';
        await this._ensureUserTx(c, { uid: mapped.targetUserId });
        await c.query(
          `UPDATE users
           SET status = $1,
               status_reason = $2,
               status_updated_at = $3,
               status_updated_by = $4,
               updated_at = NOW()
           WHERE uid = $5`,
          [targetStatus, `بلاغ رقابي: ${mapped.reason}`, this.now(), reviewerUid, mapped.targetUserId]
        );
      }
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'RESOLVE_MODERATION_REPORT',$2,$3)`,
        [reviewerUid, reportId, JSON.stringify({ status, actionTaken, targetUserId: mapped.targetUserId })]
      );
      return mapped;
    });
  }

  _mapModerationSample(r) {
    return {
      id: r.id,
      streamId: r.stream_id || null,
      userUid: r.user_uid,
      sampleType: r.sample_type,
      provider: r.provider,
      status: r.status,
      riskScore: Number(r.risk_score || 0),
      labels: typeof r.labels_json === 'string' ? JSON.parse(r.labels_json) : (r.labels_json || []),
      estimatedCostUsd: Number(r.estimated_cost_usd || 0),
      actionTaken: r.action_taken || 'NONE',
      reviewerUid: r.reviewer_uid || null,
      details: r.details || '',
      createdAtMs: r.created_at ? new Date(r.created_at).getTime() : this.now(),
      reviewedAtMs: r.reviewed_at ? new Date(r.reviewed_at).getTime() : null
    };
  }

  async getModerationDailyUsage(dayKey) {
    const res = await this.pool.query(
      `SELECT * FROM moderation_daily_usage WHERE day_key = $1`,
      [dayKey]
    );
    if (res.rowCount === 0) {
      return { dayKey, sampleCount: 0, estimatedCostUsd: 0 };
    }
    const r = res.rows[0];
    return {
      dayKey: r.day_key,
      sampleCount: Number(r.sample_count || 0),
      estimatedCostUsd: Number(r.estimated_cost_usd || 0)
    };
  }

  async createModerationSample({
    id,
    streamId = null,
    userUid,
    sampleType = 'STREAM_FRAME',
    provider = 'heuristic_local',
    status = 'CLEAN',
    riskScore = 0,
    labels = [],
    estimatedCostUsd = 0,
    actionTaken = 'NONE',
    details = '',
    dayKey = null,
    incrementUsage = true
  }) {
    return this._withTx(async (c) => {
      const sampleId = id || `mod_${crypto.randomUUID()}`;
      const res = await c.query(
        `INSERT INTO moderation_samples
          (id, stream_id, user_uid, sample_type, provider, status, risk_score, labels_json, estimated_cost_usd, action_taken, details)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
         RETURNING *`,
        [
          sampleId,
          streamId,
          userUid,
          sampleType,
          provider,
          status,
          Number(riskScore || 0),
          JSON.stringify(Array.isArray(labels) ? labels : []),
          Number(estimatedCostUsd || 0),
          actionTaken,
          String(details || '')
        ]
      );
      if (incrementUsage && dayKey) {
        await c.query(
          `INSERT INTO moderation_daily_usage (day_key, sample_count, estimated_cost_usd)
           VALUES ($1, 1, $2)
           ON CONFLICT (day_key) DO UPDATE SET
             sample_count = moderation_daily_usage.sample_count + 1,
             estimated_cost_usd = moderation_daily_usage.estimated_cost_usd + EXCLUDED.estimated_cost_usd,
             updated_at = NOW()`,
          [dayKey, Number(estimatedCostUsd || 0)]
        );
      }
      return this._mapModerationSample(res.rows[0]);
    });
  }

  async listModerationSamples(status = 'QUEUE') {
    const target = String(status || 'QUEUE').toUpperCase();
    let res;
    if (target === 'ALL') {
      res = await this.pool.query(`SELECT * FROM moderation_samples ORDER BY created_at DESC LIMIT 200`);
    } else if (target === 'QUEUE') {
      res = await this.pool.query(
        `SELECT * FROM moderation_samples WHERE status IN ('PENDING_MANUAL_REVIEW', 'FLAGGED', 'BLOCKED') ORDER BY created_at DESC LIMIT 200`
      );
    } else {
      res = await this.pool.query(
        `SELECT * FROM moderation_samples WHERE status = $1 ORDER BY created_at DESC LIMIT 200`,
        [target]
      );
    }
    return res.rows.map((r) => this._mapModerationSample(r));
  }

  async reviewModerationSample({ reviewerUid, sampleId, decision, note = null }) {
    return this._withTx(async (c) => {
      const norm = String(decision || '').trim().toUpperCase();
      if (norm !== 'APPROVED' && norm !== 'CONFIRMED') {
        throw new CallEngineError('INVALID_DECISION', 'القرار يجب أن يكون APPROVED أو CONFIRMED', 400);
      }
      const nextStatus = norm === 'APPROVED' ? 'REVIEWED_APPROVED' : 'REVIEWED_CONFIRMED';
      const noteSuffix = note ? ` | ${note}` : '';
      const res = await c.query(
        `UPDATE moderation_samples
         SET status = $1,
             reviewer_uid = $2,
             details = TRIM(BOTH FROM (details || $3)),
             reviewed_at = NOW()
         WHERE id = $4
         RETURNING *`,
        [nextStatus, reviewerUid, noteSuffix, sampleId]
      );
      if (res.rowCount === 0) {
        throw new CallEngineError('MODERATION_SAMPLE_NOT_FOUND', 'عينة الفحص غير موجودة', 404);
      }
      const mapped = this._mapModerationSample(res.rows[0]);
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'REVIEW_MODERATION_SAMPLE',$2,$3)`,
        [reviewerUid, sampleId, JSON.stringify({ decision: norm, streamId: mapped.streamId, userUid: mapped.userUid, note })]
      );
      return mapped;
    });
  }

  async blockUser({ blockerUid, blockedUid }) {
    await this.pool.query(`INSERT INTO user_blocks (blocker_uid, blocked_uid) VALUES ($1,$2) ON CONFLICT DO NOTHING`, [blockerUid, blockedUid]);
    return { blockedUsers: await this.getBlockedUsers(blockerUid) };
  }

  async unblockUser({ blockerUid, blockedUid }) {
    await this.pool.query(`DELETE FROM user_blocks WHERE blocker_uid=$1 AND blocked_uid=$2`, [blockerUid, blockedUid]);
    return { blockedUsers: await this.getBlockedUsers(blockerUid) };
  }

  async getBlockedUsers(uid) {
    const res = await this.pool.query(`SELECT blocked_uid FROM user_blocks WHERE blocker_uid=$1`, [uid]);
    return res.rows.map((r) => r.blocked_uid);
  }

  async deleteUserAccount({ uid }) {
    return this._withTx(async (c) => {
      await c.query(
        `UPDATE users SET deleted_at=$1, display_name='حساب محذوف', email='', dob=NULL, bio='', photo_url=NULL, updated_at=NOW() WHERE uid=$2`,
        [this.now(), uid]
      );
      await c.query(`DELETE FROM age_verifications WHERE uid=$1`, [uid]);
      await c.query(`DELETE FROM user_blocks WHERE blocker_uid=$1 OR blocked_uid=$1`, [uid]);
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'ACCOUNT_DELETED',$1,'{}')`,
        [uid]
      );
      return { deleted: true, uid };
    });
  }

  async getAuditLogs({ actorUid = null, action = null, targetId = null, limit = 100 } = {}) {
    const safeLimit = Math.max(1, Math.min(500, Number(limit) || 100));
    const clauses = [];
    const params = [];
    if (actorUid) {
      params.push(actorUid);
      clauses.push(`actor_uid = $${params.length}`);
    }
    if (action) {
      params.push(action);
      clauses.push(`action = $${params.length}`);
    }
    if (targetId) {
      params.push(targetId);
      clauses.push(`target_id = $${params.length}`);
    }
    params.push(safeLimit);
    const whereSql = clauses.length > 0 ? `WHERE ${clauses.join(' AND ')}` : '';
    const res = await this.pool.query(
      `SELECT * FROM audit_logs ${whereSql} ORDER BY id DESC LIMIT $${params.length}`,
      params
    );
    return res.rows.map((r) => ({
      id: Number(r.id),
      actorUid: r.actor_uid,
      action: r.action,
      targetId: r.target_id || null,
      details: typeof r.details === 'string' ? JSON.parse(r.details) : (r.details || {}),
      createdAtMs: r.created_at ? new Date(r.created_at).getTime() : this.now()
    }));
  }

  async processVoidedPurchase({ purchaseToken, reason = 'PLAY_STORE_REFUND' }) {
    return this._withTx(async (c) => {
      const res = await c.query(`SELECT * FROM iap_transactions WHERE purchase_token = $1 FOR UPDATE`, [purchaseToken]);
      if (res.rowCount === 0) {
        throw new CallEngineError('IAP_NOT_FOUND', 'عملية الشراء غير موجودة', 404);
      }
      const tx = res.rows[0];
      if (tx.is_voided) {
        return { duplicate: true, alreadyVoided: true, purchaseToken, uid: tx.uid };
      }

      await c.query(
        `UPDATE iap_transactions SET is_voided = TRUE, voided_at = NOW(), voided_reason = $1 WHERE purchase_token = $2`,
        [reason, purchaseToken]
      );

      const { wallet } = await this._ensureUserTx(c, { uid: tx.uid });
      const coinsToClawback = Number(tx.coins_credited);
      const deductedFromBalance = Math.min(wallet.pointsBalance, coinsToClawback);
      const addedDebt = coinsToClawback - deductedFromBalance;

      const upd = await c.query(
        `UPDATE wallet_accounts
         SET points_balance = points_balance - $1,
             debt_coins = debt_coins + $2,
             updated_at = NOW()
         WHERE uid = $3
         RETURNING *`,
        [deductedFromBalance, addedDebt, tx.uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);

      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'REFUND_CLAWBACK','POINTS',$3,$4,$5,$6,$7,$8)`,
        [
          crypto.randomUUID(),
          tx.uid,
          -deductedFromBalance,
          updatedWallet.pointsBalance,
          `clawback_${purchaseToken}`,
          purchaseToken,
          `استرداد مشتريات Google Play (-${coinsToClawback} نقطة)`,
          JSON.stringify({ purchaseToken, reason, coinsToClawback, deductedFromBalance, addedDebt, totalDebtCoins: updatedWallet.debtCoins })
        ]
      );

      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details)
         VALUES ($1, 'IAP_PURCHASE_VOIDED', $2, $3)`,
        [tx.uid, purchaseToken, JSON.stringify({ coinsClawedBack: coinsToClawback, deductedFromBalance, addedDebt, reason })]
      );

      if (addedDebt > 0) {
        await c.query(
          `INSERT INTO audit_logs (actor_uid, action, target_id, details)
           VALUES ('system', 'REFUND_CLAWBACK_DEBT', $1, $2)`,
          [tx.uid, JSON.stringify({ debtCoins: updatedWallet.debtCoins, addedDebt, purchaseToken })]
        );
      }

      const gifts = await c.query(
        `SELECT DISTINCT COALESCE(metadata->>'recipientUid', reference_id) AS recipient_uid FROM ledger_entries WHERE uid = $1 AND entry_type = 'GIFT_SENT'`,
        [tx.uid]
      );
      for (const row of gifts.rows) {
        if (row.recipient_uid) {
          await c.query(
            `INSERT INTO audit_logs (actor_uid, action, target_id, details)
             VALUES ('system', 'HOST_EARNINGS_REVIEW_FLAGGED', $1, $2)`,
            [row.recipient_uid, JSON.stringify({ buyerUid: tx.uid, reason: 'PURCHASE_REFUNDED_BY_BUYER', purchaseToken })]
          );
        }
      }

      return {
        ok: true,
        uid: tx.uid,
        purchaseToken,
        coinsClawedBack: coinsToClawback,
        deductedFromBalance,
        addedDebt,
        debtCoins: updatedWallet.debtCoins
      };
    });
  }

  async exportReconciliationSnapshot() {
    return this._withTx(async (c) => {
      const wRes = await c.query(`SELECT * FROM wallet_accounts`);
      const lRes = await c.query(`SELECT * FROM ledger_entries`);
      const hRes = await c.query(`SELECT * FROM ledger_holds`);
      const pRes = await c.query(`SELECT * FROM payout_requests`);
      return {
        wallets: wRes.rows.map((w) => this._mapWallet(w)),
        ledgerEntries: lRes.rows.map((e) => ({
          entryUuid: e.entry_uuid,
          uid: e.uid,
          entryType: e.entry_type,
          bucket: e.bucket,
          delta: Number(e.delta),
          balanceAfter: Number(e.balance_after)
        })),
        holds: hRes.rows.map((h) => this._mapHold(h)),
        payoutRequests: pRes.rows.map((p) => this._mapPayoutRequest(p))
      };
    });
  }
}

export async function createLedgerStore(env = process.env) {
  if (env.DATABASE_URL) {
    const { Pool } = await import('pg');
    const pool = new Pool({
      connectionString: env.DATABASE_URL,
      ssl: env.PG_SSL === 'true' ? { rejectUnauthorized: false } : undefined
    });
    const store = new PostgresLedgerStore({ pool, env });
    await store.init();
    return store;
  }
  const store = new MemoryLedgerStore({ env });
  await store.init();
  return store;
}
