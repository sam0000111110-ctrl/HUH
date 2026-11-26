import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CallEngineError } from './callEngine.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const MIN_PAYOUT_COINS = 500;
const DAILY_BONUS_COINS = 10;
const DAILY_CLAIM_COOLDOWN_MS = 24 * 60 * 60 * 1000;

function generateReferralCode(uid) {
  const hash = crypto.createHash('sha256').update(String(uid)).digest('hex').slice(0, 6).toUpperCase();
  return `AYER-${hash}`;
}

export class MemoryLedgerStore {
  constructor({ nowProvider = () => Date.now() } = {}) {
    this.mode = 'memory';
    this.now = nowProvider;
    this.users = new Map();
    this.wallets = new Map();
    this.ledger = [];
    this.idempotencyKeys = new Set();
    this.iapTokens = new Map();
    this.agencies = new Map();
    this.agencyMemberships = new Map();
    this.payoutRequests = new Map();
    this.ageVerifications = new Map();
    this.moderationReports = new Map();
    this.blocks = new Map(); // blockerUid -> Set<blockedUid>
    this.auditLogs = [];
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
      user = {
        uid: safeUid,
        displayName: displayName || 'مستخدم Ayer',
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
        isAdmin: false,
        isAgent: false,
        isHost: false,
        hostStatus: 'NONE',
        agentCommissionPercent: 20,
        hostCommissionPercent: 50,
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
        hostEarningsCoins: 0,
        subAgentsEarningsCoins: 0,
        reservedPayoutCoins: 0,
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

  async ensureUser(input) {
    return this._withLock(() => {
      const { user, wallet } = this._ensureUserSync(input);
      return { user: { ...user }, wallet: { ...wallet } };
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
    verificationSource = 'google_play_androidpublisher_v3'
  }) {
    return this._withLock(() => {
      if (!purchaseToken) {
        throw new CallEngineError('MISSING_PURCHASE_TOKEN', 'رمز شراء Google Play مطلوب', 400);
      }
      if (this.iapTokens.has(purchaseToken)) {
        throw new CallEngineError('DUPLICATE_PURCHASE_TOKEN', 'تم صرف عملية الشراء هذه مسبقاً', 409);
      }
      const coins = Number(coinsCredited);
      if (!Number.isInteger(coins) || coins <= 0) {
        throw new CallEngineError('INVALID_COINS_AMOUNT', 'قيمة النقاط غير صالحة', 400);
      }

      const { user, wallet } = this._ensureUserSync({ uid, displayName, email });
      if (user.deletedAt) {
        throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);
      }

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
        verificationSource,
        verifiedAtMs: nowMs
      });

      wallet.pointsBalance += coins;
      wallet.totalAccumulatedPoints += coins;
      wallet.updatedAtMs = nowMs;

      const entry = this._appendLedgerSync({
        uid,
        entryType: 'PURCHASE',
        bucket: 'POINTS',
        delta: coins,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: `iap_${purchaseToken}`,
        referenceId: orderId || purchaseToken,
        description: `شحن Google Play (+${coins} نقطة)`,
        metadata: { packageId, productId, verificationSource }
      });

      this._auditSync({
        actorUid: uid,
        action: 'IAP_PURCHASE_CREDITED',
        targetId: packageId,
        details: { coinsCredited: coins, orderId }
      });

      return {
        wallet: { ...wallet },
        entry,
        creditedCoins: coins
      };
    });
  }

  async spendCoins({ uid, amount, entryType = 'MATCH_SPEND', description, idempotencyKey, referenceId = null, metadata = {} }) {
    return this._withLock(() => {
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
      if (user.deletedAt) {
        throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);
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

      const entry = this._appendLedgerSync({
        uid,
        entryType,
        bucket: 'POINTS',
        delta: -spendAmount,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: key,
        referenceId,
        description: description || `خصم ${spendAmount} نقطة`,
        metadata
      });

      return { wallet: { ...wallet }, entry, duplicate: false };
    });
  }

  async claimDailyBonus({ uid }) {
    return this._withLock(() => {
      const { wallet } = this._ensureUserSync({ uid });
      const nowMs = this.now();
      if (wallet.lastDailyClaimMs > 0 && nowMs - wallet.lastDailyClaimMs < DAILY_CLAIM_COOLDOWN_MS) {
        throw new CallEngineError('DAILY_BONUS_COOLDOWN', 'تم استلام المكافأة اليومية بالفعل. حاول بعد 24 ساعة.', 429, {
          nextAvailableAtMs: wallet.lastDailyClaimMs + DAILY_CLAIM_COOLDOWN_MS
        });
      }
      wallet.pointsBalance += DAILY_BONUS_COINS;
      wallet.totalAccumulatedPoints += DAILY_BONUS_COINS;
      wallet.lastDailyClaimMs = nowMs;
      wallet.updatedAtMs = nowMs;

      const dayKey = new Date(nowMs).toISOString().slice(0, 10);
      const entry = this._appendLedgerSync({
        uid,
        entryType: 'DAILY_FREE',
        bucket: 'POINTS',
        delta: DAILY_BONUS_COINS,
        balanceAfter: wallet.pointsBalance,
        idempotencyKey: `daily_${uid}_${dayKey}_${nowMs}`,
        description: `مكافأة الدخول اليومي (+${DAILY_BONUS_COINS} نقطة)`
      });

      return { wallet: { ...wallet }, entry, creditedCoins: DAILY_BONUS_COINS };
    });
  }

  async claimMissionReward({ uid, missionId }) {
    const MISSION_REWARD_CATALOG = {
      mission_stream: 35,
      mission_chat: 20,
      mission_matches: 25,
      mission_gifts: 30,
      mission_daily_login: 15,
      daily_milestone: 50
    };
    return this._withLock(() => {
      const cleanId = String(missionId || '').trim();
      const rewardCoins = MISSION_REWARD_CATALOG[cleanId];
      if (!rewardCoins) {
        throw new CallEngineError('INVALID_MISSION', 'معرف المهمة غير صالح', 400);
      }
      const { user, wallet } = this._ensureUserSync({ uid });
      if (user.deletedAt) {
        throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);
      }
      const nowMs = this.now();
      const dayKey = new Date(nowMs).toISOString().slice(0, 10);
      const idempotencyKey = `mission_${uid}_${dayKey}_${cleanId}`;
      if (this.idempotencyKeys.has(idempotencyKey)) {
        throw new CallEngineError('MISSION_ALREADY_CLAIMED', 'تم استلام مكافأة هذه المهمة اليوم بالفعل.', 409);
      }

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
        description: `مكافأة مهمة يومية (${cleanId}) (+${rewardCoins} نقطة)`
      });

      return { wallet: { ...wallet }, entry, creditedCoins: rewardCoins };
    });
  }

  async applyForHost({ uid, agencyName = '' }) {
    return this._withLock(() => {
      const { user } = this._ensureUserSync({ uid });
      if (user.deletedAt) {
        throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);
      }
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

  async sendGiftAtomic({ senderUid, recipientUid, giftId, giftName, coinsCost, idempotencyKey, streamId = null }) {
    return this._withLock(() => {
      const cost = Number(coinsCost);
      if (!Number.isInteger(cost) || cost <= 0) {
        throw new CallEngineError('INVALID_GIFT_COST', 'تكلفة الهدية غير صالحة', 400);
      }
      if (!recipientUid || senderUid === recipientUid) {
        throw new CallEngineError('INVALID_RECIPIENT', 'لا يمكن إرسال الهدية لهذا المستلم', 400);
      }
      const key = String(idempotencyKey || `gift_${senderUid}_${crypto.randomUUID()}`);
      if (this.idempotencyKeys.has(key)) {
        const { wallet } = this._ensureUserSync({ uid: senderUid });
        return { senderWallet: { ...wallet }, duplicate: true };
      }

      const sender = this._ensureUserSync({ uid: senderUid });
      const recipient = this._ensureUserSync({ uid: recipientUid });

      if (sender.wallet.pointsBalance < cost) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لإرسال هذه الهدية', 402);
      }

      sender.wallet.pointsBalance -= cost;
      sender.wallet.totalPointsSpent += cost;
      sender.wallet.updatedAtMs = this.now();

      const hostSharePct = Math.max(10, Math.min(80, recipient.user.hostCommissionPercent || 50));
      const hostShare = Math.floor((cost * hostSharePct) / 100);
      recipient.wallet.hostEarningsCoins += hostShare;
      recipient.wallet.updatedAtMs = this.now();

      const spendEntry = this._appendLedgerSync({
        uid: senderUid,
        entryType: 'GIFT_SENT',
        bucket: 'POINTS',
        delta: -cost,
        balanceAfter: sender.wallet.pointsBalance,
        idempotencyKey: key,
        referenceId: streamId || recipientUid,
        description: `إرسال هدية ${giftName || giftId} (-${cost} نقطة)`,
        metadata: { recipientUid, giftId, streamId }
      });

      this._appendLedgerSync({
        uid: recipientUid,
        entryType: 'GIFT_RECEIVE',
        bucket: 'HOST_EARNINGS',
        delta: hostShare,
        balanceAfter: recipient.wallet.hostEarningsCoins,
        idempotencyKey: `${key}_rx`,
        referenceId: streamId || senderUid,
        description: `أرباح هدية ${giftName || giftId} (+${hostShare} نقطة أرباح)`,
        metadata: { senderUid, giftId, streamId }
      });

      const membership = this.agencyMemberships.get(recipientUid);
      let agentShare = 0;
      if (membership?.parentAgentUid && this.users.has(membership.parentAgentUid)) {
        const agent = this._ensureUserSync({ uid: membership.parentAgentUid });
        const agentPct = Math.max(1, Math.min(50, agent.user.agentCommissionPercent || 20));
        agentShare = Math.floor((cost * agentPct) / 100);
        if (agentShare > 0) {
          agent.wallet.subAgentsEarningsCoins += agentShare;
          agent.wallet.updatedAtMs = this.now();
          this._appendLedgerSync({
            uid: agent.user.uid,
            entryType: 'AGENT_COMMISSION',
            bucket: 'AGENCY_EARNINGS',
            delta: agentShare,
            balanceAfter: agent.wallet.subAgentsEarningsCoins,
            idempotencyKey: `${key}_ag`,
            referenceId: recipientUid,
            description: `عمولة وكالة على هدية مضيف (+${agentShare} نقطة)`,
            metadata: { hostUid: recipientUid, giftId }
          });
        }
      }

      return {
        senderWallet: { ...sender.wallet },
        recipientEarningsAdded: hostShare,
        agentCommissionAdded: agentShare,
        entry: spendEntry,
        duplicate: false
      };
    });
  }

  async submitAgeVerification({ uid, displayName = '', email = '', dob, ageYears, acceptedTermsAt, acceptedPrivacyAt }) {
    return this._withLock(() => {
      if (!dob || Number(ageYears) < 18) {
        throw new CallEngineError('UNDERAGE_FORBIDDEN', 'التطبيق مخصص للبالغين (+18) فقط', 403);
      }
      if (!acceptedTermsAt || !acceptedPrivacyAt) {
        throw new CallEngineError('LEGAL_CONSENT_REQUIRED', 'الموافقة على الشروط وسياسة الخصوصية مطلوبة', 400);
      }
      const { user } = this._ensureUserSync({ uid, displayName, email });
      const nowMs = this.now();
      user.dob = dob;
      user.ageYears = Number(ageYears);
      user.acceptedTermsAt = Number(acceptedTermsAt);
      user.acceptedPrivacyAt = Number(acceptedPrivacyAt);
      user.ageVerified = false;
      user.ageVerificationStatus = 'PENDING_REVIEW';
      user.updatedAtMs = nowMs;

      const submission = {
        uid,
        displayName: user.displayName,
        email: user.email,
        dob,
        ageYears: Number(ageYears),
        status: 'PENDING_REVIEW',
        reviewerUid: null,
        rejectionReason: null,
        acceptedTermsAt: Number(acceptedTermsAt),
        acceptedPrivacyAt: Number(acceptedPrivacyAt),
        submittedAtMs: nowMs,
        reviewedAtMs: null
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
      if (!user.ageVerified) {
        throw new CallEngineError('AGE_VERIFICATION_REQUIRED', 'يجب توثيق العمر (+18) قبل تقديم طلب سحب', 403);
      }
      if (!user.isAgent && !user.isHost) {
        throw new CallEngineError('PAYOUT_ROLE_REQUIRED', 'سحب الأرباح متاح للمضيفين والوكلاء المعتمدين فقط', 403);
      }

      const availableEarnings = wallet.hostEarningsCoins + wallet.subAgentsEarningsCoins;
      if (availableEarnings < coins) {
        throw new CallEngineError('INSUFFICIENT_EARNINGS', 'رصيد الأرباح القابل للسحب غير كافٍ', 402, {
          availableEarnings,
          requested: coins
        });
      }

      // Deduct from sub-agent earnings first, then host earnings, and reserve atomically
      let remaining = coins;
      const fromSubAgents = Math.min(wallet.subAgentsEarningsCoins, remaining);
      wallet.subAgentsEarningsCoins -= fromSubAgents;
      remaining -= fromSubAgents;
      wallet.hostEarningsCoins -= remaining;
      wallet.reservedPayoutCoins += coins;
      wallet.updatedAtMs = this.now();

      const reqId = `payout_${crypto.randomUUID()}`;
      const usdCents = Math.round((coins / 10) * 100);
      const request = {
        id: reqId,
        uid,
        displayName: user.displayName,
        amountCoins: coins,
        usdEquivalentCents: usdCents,
        payoutMethod,
        bankAccountHolder: String(bankAccountHolder).trim(),
        bankName: String(bankName).trim(),
        ibanOrAccount: String(ibanOrAccount).trim(),
        fromSubAgentsCoins: fromSubAgents,
        fromHostCoins: remaining,
        status: 'PENDING_REVIEW',
        reviewerUid: null,
        reviewerNote: null,
        paymentReference: null,
        createdAtMs: this.now(),
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
        details: { amountCoins: coins, usdEquivalentCents: usdCents }
      });

      return { request: { ...request }, wallet: { ...wallet } };
    });
  }

  async listPayoutRequests({ uid, isAdmin = false, status = null } = {}) {
    return this._withLock(() => {
      const targetStatus = status ? String(status).toUpperCase() : null;
      return Array.from(this.payoutRequests.values())
        .filter((r) => (isAdmin || r.uid === uid))
        .filter((r) => !targetStatus || targetStatus === 'ALL' || r.status === targetStatus)
        .sort((a, b) => b.createdAtMs - a.createdAtMs)
        .map((r) => ({ ...r }));
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
      if (reqItem.status === 'PAID' || reqItem.status === 'REJECTED') {
        throw new CallEngineError('PAYOUT_ALREADY_FINALIZED', 'تم البت في طلب السحب هذا مسبقاً', 409);
      }

      const { wallet } = this._ensureUserSync({ uid: reqItem.uid });
      const nowMs = this.now();

      if (normalized === 'REJECTED') {
        wallet.reservedPayoutCoins = Math.max(0, wallet.reservedPayoutCoins - reqItem.amountCoins);
        wallet.subAgentsEarningsCoins += reqItem.fromSubAgentsCoins;
        wallet.hostEarningsCoins += reqItem.fromHostCoins;
        wallet.updatedAtMs = nowMs;

        this._appendLedgerSync({
          uid: reqItem.uid,
          entryType: 'WITHDRAWAL_REFUND',
          bucket: 'HOST_EARNINGS',
          delta: reqItem.amountCoins,
          balanceAfter: wallet.hostEarningsCoins + wallet.subAgentsEarningsCoins,
          idempotencyKey: `payout_refund_${requestId}`,
          referenceId: requestId,
          description: `استرداد طلب سحب مرفوض (+${reqItem.amountCoins} نقطة أرباح)`
        });
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

      return { request: { ...reqItem }, wallet: { ...wallet } };
    });
  }

  async adminSetUserRolesAndCommission({
    reviewerUid,
    targetUid,
    isAgent,
    isHost,
    isAdmin,
    agentCommissionPercent,
    hostCommissionPercent,
    agencyName
  }) {
    return this._withLock(() => {
      const { user } = this._ensureUserSync({ uid: targetUid });
      if (typeof isAgent === 'boolean') user.isAgent = isAgent;
      if (typeof isHost === 'boolean') {
        user.isHost = isHost;
        user.hostStatus = isHost ? 'APPROVED' : 'NONE';
      }
      if (typeof isAdmin === 'boolean') user.isAdmin = isAdmin;
      if (agentCommissionPercent !== undefined) {
        const pct = Number(agentCommissionPercent);
        if (!Number.isInteger(pct) || pct < 5 || pct > 50) {
          throw new CallEngineError('INVALID_COMMISSION', 'نسبة عمولة الوكيل يجب أن تكون بين 5% و 50%', 400);
        }
        user.agentCommissionPercent = pct;
      }
      if (hostCommissionPercent !== undefined) {
        const pct = Number(hostCommissionPercent);
        if (!Number.isInteger(pct) || pct < 10 || pct > 80) {
          throw new CallEngineError('INVALID_COMMISSION', 'نسبة عمولة المضيف يجب أن تكون بين 10% و 80%', 400);
        }
        user.hostCommissionPercent = pct;
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
          agentCommissionPercent: user.agentCommissionPercent
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
        .map((r) => ({ ...r }));
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
      rep.status = status;
      rep.actionTaken = actionTaken;
      rep.reviewerUid = reviewerUid;
      rep.reviewedAtMs = this.now();
      this._auditSync({
        actorUid: reviewerUid,
        action: 'RESOLVE_MODERATION_REPORT',
        targetId: reportId,
        details: { status, actionTaken, targetUserId: rep.targetUserId }
      });
      return { ...rep };
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
      }
      this.ageVerifications.delete(uid);
      this.blocks.delete(uid);
      this._auditSync({ actorUid: uid, action: 'ACCOUNT_DELETED', targetId: uid });
      return { deleted: true, uid };
    });
  }
}

export class PostgresLedgerStore {
  constructor({ pool, nowProvider = () => Date.now() }) {
    this.mode = 'postgres';
    this.pool = pool;
    this.now = nowProvider;
  }

  async init() {
    const schemaPath = path.join(__dirname, '..', 'sql', 'schema.sql');
    const sql = fs.readFileSync(schemaPath, 'utf8');
    await this.pool.query(sql);
    return { ok: true, mode: this.mode };
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
    await client.query(
      `INSERT INTO users (uid, display_name, email, referral_code)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (uid) DO UPDATE SET
         display_name = CASE WHEN users.display_name = '' THEN EXCLUDED.display_name ELSE users.display_name END,
         email = CASE WHEN users.email = '' THEN EXCLUDED.email ELSE users.email END,
         updated_at = NOW()`,
      [safeUid, displayName || 'مستخدم Ayer', email || '', generateReferralCode(safeUid)]
    );
    await client.query(
      `INSERT INTO wallet_accounts (uid) VALUES ($1) ON CONFLICT (uid) DO NOTHING`,
      [safeUid]
    );
    const userRes = await client.query(`SELECT * FROM users WHERE uid = $1 FOR UPDATE`, [safeUid]);
    const walletRes = await client.query(`SELECT * FROM wallet_accounts WHERE uid = $1 FOR UPDATE`, [safeUid]);
    return { user: this._mapUser(userRes.rows[0]), wallet: this._mapWallet(walletRes.rows[0]) };
  }

  _mapUser(r) {
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
      ageYears: r.age_years,
      ageVerified: r.age_verified,
      ageVerificationStatus: r.age_verification_status,
      isAdmin: r.is_admin,
      isAgent: r.is_agent,
      isHost: r.is_host,
      hostStatus: r.host_status,
      agentCommissionPercent: r.agent_commission_percent,
      hostCommissionPercent: r.host_commission_percent,
      agencyId: r.agency_id,
      referralCode: r.referral_code,
      profileVisibility: r.profile_visibility,
      directMessagePermission: r.direct_message_permission,
      acceptedTermsAt: r.accepted_terms_at ? Number(r.accepted_terms_at) : null,
      acceptedPrivacyAt: r.accepted_privacy_at ? Number(r.accepted_privacy_at) : null,
      deletedAt: r.deleted_at ? Number(r.deleted_at) : null
    };
  }

  _mapWallet(r) {
    return {
      uid: r.uid,
      pointsBalance: Number(r.points_balance),
      hostEarningsCoins: Number(r.host_earnings_coins),
      subAgentsEarningsCoins: Number(r.sub_agents_earnings_coins),
      reservedPayoutCoins: Number(r.reserved_payout_coins),
      totalAccumulatedPoints: Number(r.total_accumulated_points),
      totalPointsSpent: Number(r.total_points_spent),
      lastDailyClaimMs: Number(r.last_daily_claim_ms)
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
    return {
      id: r.id,
      uid: r.uid,
      displayName: r.display_name || '',
      amountCoins: Number(r.amount_coins),
      usdEquivalentCents: Number(r.usd_equivalent_cents),
      payoutMethod: r.payout_method,
      bankAccountHolder: r.bank_account_holder,
      bankName: r.bank_name,
      ibanOrAccount: r.iban_or_account,
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

  async ensureUser(input) {
    return this._withTx((c) => this._ensureUserTx(c, input));
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
          description: e.description,
          timestamp: new Date(e.created_at).getTime()
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
    verificationSource = 'google_play_androidpublisher_v3'
  }) {
    return this._withTx(async (c) => {
      const coins = Number(coinsCredited);
      if (!purchaseToken || !Number.isInteger(coins) || coins <= 0) {
        throw new CallEngineError('INVALID_IAP_PAYLOAD', 'بيانات شراء Google Play غير صالحة', 400);
      }
      const { user, wallet } = await this._ensureUserTx(c, { uid, displayName, email });
      if (user.deletedAt) throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);

      const ins = await c.query(
        `INSERT INTO iap_transactions
          (purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros, currency_code, purchase_state, acknowledged, verification_source)
         VALUES ($1,$2,$3,$4,$5,$6,$7,'USD',$8,$9,$10)
         ON CONFLICT (purchase_token) DO NOTHING
         RETURNING id`,
        [purchaseToken, orderId, uid, packageId, productId, coins, Math.round(Number(priceUsd) * 1_000_000), purchaseState, acknowledged, verificationSource]
      );
      if (ins.rowCount === 0) {
        throw new CallEngineError('DUPLICATE_PURCHASE_TOKEN', 'تم صرف عملية الشراء هذه مسبقاً', 409);
      }

      const upd = await c.query(
        `UPDATE wallet_accounts
         SET points_balance = points_balance + $1,
             total_accumulated_points = total_accumulated_points + $1,
             updated_at = NOW()
         WHERE uid = $2
         RETURNING *`,
        [coins, uid]
      );
      const updatedWallet = this._mapWallet(upd.rows[0]);
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,'PURCHASE','POINTS',$3,$4,$5,$6,$7,$8)`,
        [crypto.randomUUID(), uid, coins, updatedWallet.pointsBalance, `iap_${purchaseToken}`, orderId || purchaseToken, `شحن Google Play (+${coins} نقطة)`, JSON.stringify({ packageId, productId, verificationSource })]
      );
      return { wallet: updatedWallet, creditedCoins: coins };
    });
  }

  async spendCoins({ uid, amount, entryType = 'MATCH_SPEND', description, idempotencyKey, referenceId = null, metadata = {} }) {
    return this._withTx(async (c) => {
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

      const { wallet } = await this._ensureUserTx(c, { uid });
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
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description, metadata)
         VALUES ($1,$2,$3,'POINTS',$4,$5,$6,$7,$8,$9)`,
        [crypto.randomUUID(), uid, entryType, -spendAmount, updatedWallet.pointsBalance, key, referenceId, description || `خصم ${spendAmount} نقطة`, JSON.stringify(metadata)]
      );
      return { wallet: updatedWallet, duplicate: false };
    });
  }

  async claimDailyBonus({ uid }) {
    return this._withTx(async (c) => {
      const { wallet } = await this._ensureUserTx(c, { uid });
      const nowMs = this.now();
      if (wallet.lastDailyClaimMs > 0 && nowMs - wallet.lastDailyClaimMs < DAILY_CLAIM_COOLDOWN_MS) {
        throw new CallEngineError('DAILY_BONUS_COOLDOWN', 'تم استلام المكافأة اليومية بالفعل. حاول بعد 24 ساعة.', 429);
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
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, description)
         VALUES ($1,$2,'DAILY_FREE','POINTS',$3,$4,$5,$6)`,
        [crypto.randomUUID(), uid, DAILY_BONUS_COINS, updatedWallet.pointsBalance, `daily_${uid}_${nowMs}`, `مكافأة الدخول اليومي (+${DAILY_BONUS_COINS} نقطة)`]
      );
      return { wallet: updatedWallet, creditedCoins: DAILY_BONUS_COINS };
    });
  }

  async claimMissionReward({ uid, missionId }) {
    const MISSION_REWARD_CATALOG = {
      mission_stream: 35,
      mission_chat: 20,
      mission_matches: 25,
      mission_gifts: 30,
      mission_daily_login: 15,
      daily_milestone: 50
    };
    return this._withTx(async (c) => {
      const cleanId = String(missionId || '').trim();
      const rewardCoins = MISSION_REWARD_CATALOG[cleanId];
      if (!rewardCoins) {
        throw new CallEngineError('INVALID_MISSION', 'معرف المهمة غير صالح', 400);
      }
      const { user } = await this._ensureUserTx(c, { uid });
      if (user.deletedAt) {
        throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);
      }
      const nowMs = this.now();
      const dayKey = new Date(nowMs).toISOString().slice(0, 10);
      const idempotencyKey = `mission_${uid}_${dayKey}_${cleanId}`;
      const existing = await c.query(`SELECT * FROM ledger_entries WHERE idempotency_key = $1`, [idempotencyKey]);
      if (existing.rowCount > 0) {
        throw new CallEngineError('MISSION_ALREADY_CLAIMED', 'تم استلام مكافأة هذه المهمة اليوم بالفعل.', 409);
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
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description)
         VALUES ($1,$2,'MISSION_REWARD','POINTS',$3,$4,$5,$6,$7)`,
        [crypto.randomUUID(), uid, rewardCoins, updatedWallet.pointsBalance, idempotencyKey, cleanId, `مكافأة مهمة يومية (${cleanId}) (+${rewardCoins} نقطة)`]
      );
      return { wallet: updatedWallet, creditedCoins: rewardCoins };
    });
  }

  async applyForHost({ uid, agencyName = '' }) {
    return this._withTx(async (c) => {
      const { user } = await this._ensureUserTx(c, { uid });
      if (user.deletedAt) {
        throw new CallEngineError('ACCOUNT_DELETED', 'هذا الحساب محذوف', 403);
      }
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

  async sendGiftAtomic({ senderUid, recipientUid, giftId, giftName, coinsCost, idempotencyKey, streamId = null }) {
    return this._withTx(async (c) => {
      const cost = Number(coinsCost);
      if (!Number.isInteger(cost) || cost <= 0 || !recipientUid || senderUid === recipientUid) {
        throw new CallEngineError('INVALID_GIFT_REQUEST', 'بيانات الهدية غير صالحة', 400);
      }
      const key = String(idempotencyKey || `gift_${senderUid}_${crypto.randomUUID()}`);
      const existing = await c.query(`SELECT * FROM ledger_entries WHERE idempotency_key = $1`, [key]);
      if (existing.rowCount > 0) {
        const { wallet } = await this._ensureUserTx(c, { uid: senderUid });
        return { senderWallet: wallet, duplicate: true };
      }

      const sender = await this._ensureUserTx(c, { uid: senderUid });
      const recipient = await this._ensureUserTx(c, { uid: recipientUid });
      if (sender.wallet.pointsBalance < cost) {
        throw new CallEngineError('INSUFFICIENT_COINS', 'رصيد النقاط غير كافٍ لإرسال هذه الهدية', 402);
      }
      const sUpd = await c.query(
        `UPDATE wallet_accounts SET points_balance = points_balance - $1, total_points_spent = total_points_spent + $1, updated_at = NOW() WHERE uid = $2 RETURNING *`,
        [cost, senderUid]
      );
      const hostShare = Math.floor((cost * Math.max(10, Math.min(80, recipient.user.hostCommissionPercent || 50))) / 100);
      const rUpd = await c.query(
        `UPDATE wallet_accounts SET host_earnings_coins = host_earnings_coins + $1, updated_at = NOW() WHERE uid = $2 RETURNING *`,
        [hostShare, recipientUid]
      );
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description)
         VALUES ($1,$2,'GIFT_SENT','POINTS',$3,$4,$5,$6,$7)`,
        [crypto.randomUUID(), senderUid, -cost, Number(sUpd.rows[0].points_balance), key, streamId || recipientUid, `إرسال هدية ${giftName || giftId}`]
      );
      await c.query(
        `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description)
         VALUES ($1,$2,'GIFT_RECEIVE','HOST_EARNINGS',$3,$4,$5,$6,$7)`,
        [crypto.randomUUID(), recipientUid, hostShare, Number(rUpd.rows[0].host_earnings_coins), `${key}_rx`, streamId || senderUid, `أرباح هدية ${giftName || giftId}`]
      );

      let agentShare = 0;
      const agMemRes = await c.query(`SELECT parent_agent_uid FROM agency_memberships WHERE uid = $1`, [recipientUid]);
      const parentUid = agMemRes.rows[0]?.parent_agent_uid;
      if (parentUid) {
        const agent = await this._ensureUserTx(c, { uid: parentUid });
        const agentPct = Math.max(1, Math.min(50, agent.user.agentCommissionPercent || 20));
        agentShare = Math.floor((cost * agentPct) / 100);
        if (agentShare > 0) {
          const aUpd = await c.query(
            `UPDATE wallet_accounts SET sub_agents_earnings_coins = sub_agents_earnings_coins + $1, updated_at = NOW() WHERE uid = $2 RETURNING *`,
            [agentShare, parentUid]
          );
          await c.query(
            `INSERT INTO ledger_entries (entry_uuid, uid, entry_type, bucket, delta, balance_after, idempotency_key, reference_id, description)
             VALUES ($1,$2,'AGENT_COMMISSION','AGENCY_EARNINGS',$3,$4,$5,$6,$7)`,
            [crypto.randomUUID(), parentUid, agentShare, Number(aUpd.rows[0].sub_agents_earnings_coins), `${key}_ag`, recipientUid, `عمولة وكالة على هدية مضيف (+${agentShare} نقطة)`]
          );
        }
      }

      return { senderWallet: this._mapWallet(sUpd.rows[0]), recipientEarningsAdded: hostShare, agentCommissionAdded: agentShare, duplicate: false };
    });
  }

  async submitAgeVerification({ uid, displayName = '', email = '', dob, ageYears, acceptedTermsAt, acceptedPrivacyAt }) {
    return this._withTx(async (c) => {
      if (!dob || Number(ageYears) < 18) throw new CallEngineError('UNDERAGE_FORBIDDEN', 'التطبيق مخصص للبالغين (+18) فقط', 403);
      await this._ensureUserTx(c, { uid, displayName, email });
      await c.query(
        `UPDATE users SET dob=$1, age_years=$2, accepted_terms_at=$3, accepted_privacy_at=$4, age_verified=FALSE, age_verification_status='PENDING_REVIEW', updated_at=NOW() WHERE uid=$5`,
        [dob, Number(ageYears), Number(acceptedTermsAt), Number(acceptedPrivacyAt), uid]
      );
      const res = await c.query(
        `INSERT INTO age_verifications (uid, display_name, email, dob, age_years, status, accepted_terms_at, accepted_privacy_at)
         VALUES ($1,$2,$3,$4,$5,'PENDING_REVIEW',$6,$7)
         ON CONFLICT (uid) DO UPDATE SET dob=EXCLUDED.dob, age_years=EXCLUDED.age_years, status='PENDING_REVIEW', rejection_reason=NULL, submitted_at=NOW()
         RETURNING *`,
        [uid, displayName, email, dob, Number(ageYears), Number(acceptedTermsAt), Number(acceptedPrivacyAt)]
      );
      return this._mapAgeVerification(res.rows[0]);
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
      return { submission: this._mapAgeVerification(res.rows[0]), user: this._mapUser(uRes.rows[0]) };
    });
  }

  async createPayoutRequest({ uid, amountCoins, payoutMethod = 'BANK_TRANSFER', bankAccountHolder, bankName, ibanOrAccount }) {
    return this._withTx(async (c) => {
      const coins = Number(amountCoins);
      if (!Number.isInteger(coins) || coins < MIN_PAYOUT_COINS) {
        throw new CallEngineError('MIN_PAYOUT_NOT_MET', `الحد الأدنى لسحب الأرباح هو ${MIN_PAYOUT_COINS} نقطة`, 400);
      }
      const { user, wallet } = await this._ensureUserTx(c, { uid });
      if (!user.ageVerified) throw new CallEngineError('AGE_VERIFICATION_REQUIRED', 'توثيق العمر مطلوب', 403);
      if (!user.isAgent && !user.isHost) throw new CallEngineError('PAYOUT_ROLE_REQUIRED', 'متاح للمضيفين والوكلاء فقط', 403);
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
      const reqId = `payout_${crypto.randomUUID()}`;
      const usdCents = Math.round((coins / 10) * 100);
      const ins = await c.query(
        `INSERT INTO payout_requests (id, uid, amount_coins, usd_equivalent_cents, payout_method, bank_account_holder, bank_name, iban_or_account, status)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,'PENDING_REVIEW') RETURNING *`,
        [reqId, uid, coins, usdCents, payoutMethod, bankAccountHolder, bankName, ibanOrAccount]
      );
      return { request: this._mapPayoutRequest(ins.rows[0]), wallet: this._mapWallet(upd.rows[0]) };
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
      if (reqRow.status === 'PAID' || reqRow.status === 'REJECTED') {
        throw new CallEngineError('PAYOUT_ALREADY_FINALIZED', 'تم البت في هذا الطلب مسبقاً', 409);
      }
      if (norm === 'REJECTED') {
        await c.query(
          `UPDATE wallet_accounts SET reserved_payout_coins = GREATEST(0, reserved_payout_coins - $1), host_earnings_coins = host_earnings_coins + $1, updated_at = NOW() WHERE uid = $2`,
          [reqRow.amount_coins, reqRow.uid]
        );
      } else if (norm === 'PAID') {
        await c.query(
          `UPDATE wallet_accounts SET reserved_payout_coins = GREATEST(0, reserved_payout_coins - $1), updated_at = NOW() WHERE uid = $2`,
          [reqRow.amount_coins, reqRow.uid]
        );
      }
      const upd = await c.query(
        `UPDATE payout_requests SET status=$1, reviewer_uid=$2, reviewer_note=$3, payment_reference=COALESCE($4, payment_reference), reviewed_at=NOW() WHERE id=$5 RETURNING *`,
        [norm, reviewerUid, note, paymentReference, requestId]
      );
      const wRes = await c.query(`SELECT * FROM wallet_accounts WHERE uid = $1`, [reqRow.uid]);
      return { request: this._mapPayoutRequest(upd.rows[0]), wallet: this._mapWallet(wRes.rows[0]) };
    });
  }

  async adminSetUserRolesAndCommission({ reviewerUid, targetUid, isAgent, isHost, isAdmin, agentCommissionPercent, hostCommissionPercent, agencyName }) {
    return this._withTx(async (c) => {
      const { user } = await this._ensureUserTx(c, { uid: targetUid });
      const resolvedAgencyId = (typeof isAgent === 'boolean' ? isAgent : user.isAgent) && !user.agencyId
        ? `ag_${targetUid.slice(0, 8)}`
        : user.agencyId;
      const res = await c.query(
        `UPDATE users SET
           is_agent = COALESCE($1, is_agent),
           is_host = COALESCE($2, is_host),
           host_status = CASE WHEN $2 = TRUE THEN 'APPROVED' WHEN $2 = FALSE THEN 'NONE' ELSE host_status END,
           is_admin = COALESCE($3, is_admin),
           agent_commission_percent = COALESCE($4, agent_commission_percent),
           host_commission_percent = COALESCE($5, host_commission_percent),
           agency_id = CASE WHEN $6 <> '' THEN $6 ELSE agency_id END,
           updated_at = NOW()
         WHERE uid = $7 RETURNING *`,
        [
          typeof isAgent === 'boolean' ? isAgent : null,
          typeof isHost === 'boolean' ? isHost : null,
          typeof isAdmin === 'boolean' ? isAdmin : null,
          agentCommissionPercent ?? null,
          hostCommissionPercent ?? null,
          resolvedAgencyId,
          targetUid
        ]
      );
      const updatedUser = this._mapUser(res.rows[0]);
      if (updatedUser.isAgent && resolvedAgencyId) {
        await c.query(
          `INSERT INTO agencies (id, owner_uid, name, country, commission_percent)
           VALUES ($1,$2,$3,$4,$5)
           ON CONFLICT (id) DO UPDATE SET commission_percent=EXCLUDED.commission_percent, name=CASE WHEN $3 <> '' THEN $3 ELSE agencies.name END`,
          [resolvedAgencyId, targetUid, agencyName || `وكالة ${updatedUser.displayName}`, updatedUser.country || '', updatedUser.agentCommissionPercent]
        );
      }
      await c.query(
        `INSERT INTO audit_logs (actor_uid, action, target_id, details) VALUES ($1,'ADMIN_UPDATE_USER_ROLES',$2,$3)`,
        [reviewerUid, targetUid, JSON.stringify({ isAgent, isHost, isAdmin, agentCommissionPercent, agencyName })]
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
    const res = await this.pool.query(
      `UPDATE moderation_reports SET status=$1, action_taken=$2, reviewer_uid=$3, reviewed_at=NOW() WHERE id=$4 RETURNING *`,
      [status, actionTaken, reviewerUid, reportId]
    );
    if (res.rowCount === 0) throw new CallEngineError('REPORT_NOT_FOUND', 'البلاغ غير موجود', 404);
    return this._mapModerationReport(res.rows[0]);
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
    await this.pool.query(`UPDATE users SET deleted_at=$1, display_name='حساب محذوف', email='', dob=NULL, bio='', photo_url=NULL WHERE uid=$2`, [this.now(), uid]);
    return { deleted: true, uid };
  }
}

export async function createLedgerStore(env = process.env) {
  if (env.DATABASE_URL) {
    const { Pool } = await import('pg');
    const pool = new Pool({
      connectionString: env.DATABASE_URL,
      ssl: env.PG_SSL === 'true' ? { rejectUnauthorized: false } : undefined
    });
    const store = new PostgresLedgerStore({ pool });
    await store.init();
    return store;
  }
  const store = new MemoryLedgerStore();
  await store.init();
  return store;
}
