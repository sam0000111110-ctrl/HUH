import crypto from 'node:crypto';
import http from 'node:http';
import { AuthoritativeCallEngine, CallEngineError } from './callEngine.js';
import { LiveKitRoomClient, verifyHs256Jwt } from './livekitJwt.js';
import { authenticateRequest, assertAdminPermission, ADMIN_PERMISSIONS } from './authMiddleware.js';
import { assertAccountActive, assertAdultVerified, createLedgerStore, MemoryLedgerStore, PostgresLedgerStore, SERVER_GIFT_CATALOG } from './ledgerStore.js';
import { MemoryCallStore, PostgresCallStore } from './callStore.js';
import { verifyGooglePlayProductPurchase, acknowledgeAndConsumeGooglePlayPurchase } from './playBillingVerifier.js';
import { RealtimeBroadcastAndMatchEngine, calculateMatchCost } from './realtimeEngine.js';
import { FirebaseAdminBridge } from './firebaseAdminBridge.js';
import { ModerationService, MODERATION_STATUS } from './moderationService.js';

async function releaseHoldSafely(activeLedger, holdId, reason) {
  if (!holdId) return null;
  try {
    return await activeLedger.releaseHold({ holdId, reason });
  } catch (err) {
    if (err instanceof CallEngineError && (err.code === 'HOLD_ALREADY_SETTLED' || err.code === 'HOLD_NOT_FOUND')) {
      return null;
    }
    throw err;
  }
}

async function releaseExpiredQueueHolds(activeLedger, expiredEntries) {
  if (!Array.isArray(expiredEntries) || expiredEntries.length === 0) return;
  for (const entry of expiredEntries) {
    if (entry?.holdId) {
      await releaseHoldSafely(activeLedger, entry.holdId, 'MATCH_QUEUE_EXPIRED');
    }
  }
}

function readJsonBody(req, maxBytes = 65_536) {
  return new Promise((resolve, reject) => {
    let raw = '';
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > maxBytes) {
        reject(new CallEngineError('PAYLOAD_TOO_LARGE', 'حجم الطلب يتجاوز الحد المسموح', 413));
        req.destroy();
        return;
      }
      raw += chunk.toString('utf8');
    });
    req.on('end', () => {
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new CallEngineError('INVALID_JSON', 'صيغة JSON غير صالحة', 400));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function parseBoolFlag(value, defaultValue = false) {
  if (value === undefined || value === null || String(value).trim() === '') return defaultValue;
  return String(value).trim().toLowerCase() === 'true';
}

export function resolveFeatureFlags(env = process.env) {
  return {
    payoutsEnabled: parseBoolFlag(env.PAYOUTS_ENABLED, false),
    missionRewardsEnabled: parseBoolFlag(env.MISSION_REWARDS_ENABLED, false),
    paidMediaEnabled: parseBoolFlag(env.PAID_MEDIA_ENABLED, false),
    agenciesEnabled: parseBoolFlag(env.AGENCIES_ENABLED, false)
  };
}

function assertFeatureEnabled(enabled, featureCode, arabicLabel) {
  if (!enabled) {
    throw new CallEngineError(
      'FEATURE_DISABLED',
      `ميزة ${arabicLabel} متوقفة مؤقتاً (${featureCode}) حتى استكمال مراجعة الأمان والمالية.`,
      503,
      { feature: featureCode }
    );
  }
}

export function createCallServer({
  env = process.env,
  engine = null,
  ledger = null,
  callStore = null,
  pool = null,
  tokenVerifier = null,
  playBillingVerifier = null,
  playBillingConsumer = null,
  firebaseAdminBridge = null,
  moderationService = null,
  moderationProvider = null,
  ageVerificationProvider = null,
  fetchImpl = globalThis.fetch
} = {}) {
  const livekitUrl = env.LIVEKIT_URL || '';
  const apiKey = env.LIVEKIT_API_KEY || '';
  const apiSecret = env.LIVEKIT_API_SECRET || '';
  const dataFile = env.DATA_FILE || '';

  const roomClient = new LiveKitRoomClient({ livekitUrl, apiKey, apiSecret });
  const adminBridge = firebaseAdminBridge || new FirebaseAdminBridge({ env, fetchImpl });
  const modService = moderationService || new ModerationService({ env, fetchImpl, provider: moderationProvider });

  const callEngine = engine || new AuthoritativeCallEngine({
    livekitUrl,
    apiKey,
    apiSecret,
    roomCloser: (roomName) => roomClient.deleteRoom(roomName)
  });

  const realtimeEngine = new RealtimeBroadcastAndMatchEngine({
    livekitUrl,
    apiKey,
    apiSecret,
    roomCloser: (roomName) => roomClient.deleteRoom(roomName)
  });

  let sharedPool = pool || null;
  let poolPromise = sharedPool ? Promise.resolve(sharedPool) : null;
  const getPool = async () => {
    if (sharedPool) return sharedPool;
    if (!env.DATABASE_URL) return null;
    if (!poolPromise) {
      poolPromise = import('pg').then(({ Pool }) => {
        sharedPool = new Pool({
          connectionString: env.DATABASE_URL,
          ssl: env.PG_SSL === 'true' ? { rejectUnauthorized: false } : undefined
        });
        return sharedPool;
      });
    }
    return poolPromise;
  };

  const immediateLedger = ledger || (!env.DATABASE_URL && !pool ? new MemoryLedgerStore({ env, ageVerificationProvider }) : null);
  let resolvedLedger = immediateLedger;
  let ledgerPromise = immediateLedger ? Promise.resolve(immediateLedger) : null;
  const getLedger = async () => {
    if (resolvedLedger) return resolvedLedger;
    if (!ledgerPromise) {
      ledgerPromise = (async () => {
        const pgPool = await getPool();
        const store = pgPool
          ? new PostgresLedgerStore({ pool: pgPool, env, ageVerificationProvider })
          : await createLedgerStore(env);
        await store.init();
        resolvedLedger = store;
        return store;
      })();
    }
    return ledgerPromise;
  };

  const immediateCallStore = callStore || (!env.DATABASE_URL && !pool ? new MemoryCallStore({ dataFile }) : null);
  let resolvedCallStore = immediateCallStore;
  let callStoreInitPromise = null;
  const getCallStoreAndHydrate = async () => {
    if (!callStoreInitPromise) {
      callStoreInitPromise = (async () => {
        if (!resolvedCallStore) {
          const pgPool = await getPool();
          resolvedCallStore = pgPool
            ? new PostgresCallStore({ pool: pgPool })
            : new MemoryCallStore({ dataFile });
        }
        await resolvedCallStore.init();
        if (!engine) {
          const loaded = await resolvedCallStore.loadState();
          if (loaded) {
            callEngine.hydrateState(loaded);
          }
        }
        return resolvedCallStore;
      })();
    }
    return callStoreInitPromise;
  };

  const persistCallMutation = async ({ calls = [], config = null, alerts = [] } = {}) => {
    const store = await getCallStoreAndHydrate();
    if (config) {
      await store.saveConfig(config, callEngine);
    }
    for (const c of calls) {
      if (c) await store.upsertCall(c, callEngine);
    }
    const newLogs = callEngine.drainPendingLogs();
    for (const entry of newLogs) {
      await store.appendCallLog(entry, callEngine);
    }
    const newAlerts = [...alerts, ...callEngine.drainPendingAlerts()];
    for (const alert of newAlerts) {
      if (alert) await store.upsertAlert(alert, callEngine);
    }
  };

  const save = () => persistCallMutation({ calls: Array.from(callEngine.calls.values()), config: callEngine.getConfig() });
  const terminateAllUserRealtimeSessions = async ({ uid, activeLedger, reason = 'ACCOUNT_SUSPENDED' }) => {
    const rtResult = await realtimeEngine.terminateUserSessions({ uid, reason });
    if (rtResult?.waitingHoldId) {
      await releaseHoldSafely(activeLedger, rtResult.waitingHoldId, reason).catch(() => {});
    }
    await getCallStoreAndHydrate();
    const endedCalls = [];
    for (const call of callEngine.calls.values()) {
      if ((call.callerId === uid || call.calleeId === uid) && !['ENDED', 'REJECTED', 'MISSED', 'FAILED'].includes(call.status)) {
        const ended = await callEngine.endCall({
          callId: call.callId,
          actorId: 'system',
          reason,
          systemOverride: true
        }).catch(() => null);
        if (ended?.call) endedCalls.push(ended.call);
      }
    }
    if (endedCalls.length > 0) {
      await persistCallMutation({ calls: endedCalls }).catch(() => {});
    }
    return { ...rtResult, endedCallIds: endedCalls.map((c) => c.callId) };
  };
  const featureFlags = resolveFeatureFlags(env);

  const server = http.createServer(async (req, res) => {
    try {
      const parsedUrl = new URL(req.url || '/', 'http://localhost');
      const pathname = parsedUrl.pathname;
      const method = req.method || 'GET';

      if (method === 'GET' && pathname === '/health') {
        const [activeLedger, activeCallStore] = await Promise.all([
          getLedger(),
          getCallStoreAndHydrate()
        ]);
        return sendJson(res, 200, {
          status: 'ok',
          provider: 'livekit',
          livekitConfigured: Boolean(livekitUrl && apiKey && apiSecret),
          storageMode: activeLedger.mode,
          callPersistenceMode: activeCallStore.mode,
          realtimeArchitecture: env.REDIS_URL ? 'multi-instance-redis-configured' : 'single-instance',
          playBillingConfigured: Boolean(env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || env.GOOGLE_PLAY_ACCESS_TOKEN),
          featureFlags
        });
      }

      if (method === 'GET' && pathname === '/api/config/flags') {
        return sendJson(res, 200, featureFlags);
      }

      // LiveKit Server Webhook handler (no user bearer token; uses LiveKit signed Authorization JWT)
      if (method === 'POST' && pathname === '/api/webhooks/livekit') {
        const body = await readJsonBody(req);
        const authJwt = String(req.headers['authorization'] || '').replace(/^Bearer\s+/i, '').trim();
        if (apiSecret) {
          try {
            verifyHs256Jwt(authJwt, apiSecret);
          } catch {
            return sendJson(res, 401, { error: 'UNAUTHORIZED_WEBHOOK', message: 'Invalid LiveKit webhook signature' });
          }
        }
        const eventName = body?.event;
        const roomName = body?.room?.name;
        const participantIdentity = body?.participant?.identity || null;
        if (roomName) {
          await getCallStoreAndHydrate();
          const matchingCall = Array.from(callEngine.calls.values()).find((c) => c.roomName === roomName);
          if (matchingCall && (eventName === 'room_finished' || eventName === 'participant_left')) {
            const ended = await callEngine.endCall({
              callId: matchingCall.callId,
              actorId: 'livekit_webhook',
              reason: `WEBHOOK_${eventName.toUpperCase()}`,
              systemOverride: true
            });
            await persistCallMutation({ calls: [ended.call] });
          }
          await realtimeEngine.handleLiveKitWebhook({ eventName, roomName, participantIdentity });
        }
        return sendJson(res, 200, { received: true });
      }

      // Google Play Real-Time Developer Notifications (RTDN) Webhook
      if (method === 'POST' && pathname === '/api/billing/webhook/play-rtdn') {
        const secret = env.PLAY_RTDN_SECRET;
        if (env.NODE_ENV === 'production' && !secret) {
          return sendJson(res, 503, { error: 'RTDN_NOT_CONFIGURED', message: 'رمز أمان RTDN غير مهيأ على خادم الإنتاج' });
        }
        if (secret && req.headers['x-rtdn-secret'] !== secret && parsedUrl.searchParams.get('secret') !== secret) {
          return sendJson(res, 401, { error: 'UNAUTHORIZED_RTDN', message: 'رمز أمان RTDN غير صالح' });
        }
        const body = await readJsonBody(req);
        let rtdnData = body;
        if (body?.message?.data) {
          try {
            const raw = Buffer.from(body.message.data, 'base64').toString('utf8');
            rtdnData = JSON.parse(raw);
          } catch {
            // keep as-is
          }
        }
        const activeLedger = await getLedger();
        let targetPurchaseToken = null;
        let voidReason = null;

        if (rtdnData.voidedPurchaseNotification?.purchaseToken) {
          targetPurchaseToken = rtdnData.voidedPurchaseNotification.purchaseToken;
          voidReason = `RTDN_VOIDED_REFUND_TYPE_${rtdnData.voidedPurchaseNotification.refundType || 1}`;
        } else if (rtdnData.oneTimeProductNotification) {
          const { notificationType, purchaseToken } = rtdnData.oneTimeProductNotification;
          // notificationType: 2 = ONE_TIME_PRODUCT_CANCELED, 12 = ONE_TIME_PRODUCT_REVOKED
          if ((notificationType === 2 || notificationType === 12) && purchaseToken) {
            targetPurchaseToken = purchaseToken;
            voidReason = notificationType === 2 ? 'RTDN_ONE_TIME_PRODUCT_CANCELED' : 'RTDN_ONE_TIME_PRODUCT_REVOKED';
          }
        }

        if (targetPurchaseToken) {
          try {
            const clawback = await activeLedger.processVoidedPurchase({
              purchaseToken: targetPurchaseToken,
              reason: voidReason
            });
            return sendJson(res, 200, { ok: true, clawback });
          } catch (err) {
            if (err?.code === 'IAP_NOT_FOUND') {
              return sendJson(res, 200, { ok: true, ignored: true, reason: 'PURCHASE_NOT_CREDITED' });
            }
            throw err;
          }
        }
        return sendJson(res, 200, { ok: true, received: true });
      }

      // Authenticate caller/admin
      const user = await authenticateRequest(req, env, tokenVerifier);
      if (!user) {
        return sendJson(res, 401, { error: 'UNAUTHORIZED', message: 'مطلوب تسجيل دخول صالح' });
      }
      if (user.claimStatus === 'BANNED' || user.claimStatus === 'SUSPENDED') {
        throw new CallEngineError(
          user.claimStatus === 'BANNED' ? 'ACCOUNT_BANNED' : 'ACCOUNT_SUSPENDED',
          user.claimStatus === 'BANNED'
            ? 'تم حظر هذا الحساب نهائياً لمخالفة سياسات المنصة.'
            : 'تم إيقاف هذا الحساب مؤقتاً من قِبل الإدارة.',
          403,
          { status: user.claimStatus }
        );
      }

      const activeLedger = await getLedger();
      const gateState = await activeLedger.getUserGateState(user.uid);
      assertAccountActive(gateState.user);

      const resolvedAdminRole = user.authMethod === 'admin_api_key'
        ? user.adminRole
        : (user.adminRole && user.adminRole !== 'NONE' ? user.adminRole : gateState.user.adminRole);
      const effectiveUser = {
        ...user,
        adminRole: resolvedAdminRole,
        isAdmin: resolvedAdminRole !== 'NONE'
      };

      // ==========================================================
      // Auth Sync, Authoritative Wallet & Google Play Billing
      // ==========================================================
      if (method === 'POST' && pathname === '/api/auth/sync') {
        const body = await readJsonBody(req);
        await activeLedger.ensureUser({
          uid: user.uid,
          displayName: body.displayName || user.name || '',
          email: body.email || user.email || ''
        });
        const summary = await activeLedger.getWalletSummary(user.uid, 50);
        if (user.isAdmin && (!summary.user.isAdmin || (user.adminRole !== 'NONE' && summary.user.adminRole !== user.adminRole))) {
          summary.user = await activeLedger.adminSetUserRolesAndCommission({
            reviewerUid: 'system_auth_claims',
            targetUid: user.uid,
            isAdmin: true,
            adminRole: user.adminRole || 'SUPER_ADMIN'
          });
        }
        return sendJson(res, 200, { ...summary, featureFlags });
      }

      if (method === 'GET' && pathname === '/api/wallet/summary') {
        const limit = Number(parsedUrl.searchParams.get('limit') || '50');
        const summary = await activeLedger.getWalletSummary(user.uid, limit);
        return sendJson(res, 200, { ...summary, featureFlags });
      }

      if (method === 'GET' && pathname === '/api/wallet/gifts') {
        return sendJson(res, 200, { gifts: Object.values(SERVER_GIFT_CATALOG) });
      }

      if (method === 'POST' && pathname === '/api/wallet/daily-bonus') {
        const deviceId = req.headers['x-device-id'] || null;
        const result = await activeLedger.claimDailyBonus({ uid: user.uid, deviceId });
        return sendJson(res, 200, result);
      }

      if (method === 'POST' && pathname === '/api/wallet/mission-reward') {
        assertFeatureEnabled(featureFlags.missionRewardsEnabled, 'MISSION_REWARDS_ENABLED', 'مكافآت المهام');
        const body = await readJsonBody(req);
        const deviceId = req.headers['x-device-id'] || body.deviceId || null;
        const result = await activeLedger.claimMissionReward({
          uid: user.uid,
          missionId: body.missionId,
          deviceId
        });
        return sendJson(res, 200, result);
      }

      if (method === 'POST' && pathname === '/api/wallet/spend') {
        const body = await readJsonBody(req);
        const result = await activeLedger.spendCoins({
          uid: user.uid,
          amount: body.amount,
          entryType: body.entryType || 'MATCH_SPEND',
          description: body.description,
          idempotencyKey: body.idempotencyKey,
          referenceId: body.referenceId || null,
          metadata: body.metadata || {}
        });
        return sendJson(res, 200, result);
      }

      if (method === 'POST' && pathname === '/api/wallet/gift') {
        const body = await readJsonBody(req);
        const isPaidMedia = String(body.giftId || '').startsWith('media_') || body.isPaidMedia === true;
        if (isPaidMedia) {
          assertFeatureEnabled(featureFlags.paidMediaEnabled, 'PAID_MEDIA_ENABLED', 'فتح الوسائط المدفوعة');
        }
        const activeStream = body.streamId ? realtimeEngine.getStream(body.streamId) : null;
        const resolvedRecipient = body.recipientUid || activeStream?.hostUid || '';
        const result = await activeLedger.sendGiftAtomic({
          senderUid: user.uid,
          recipientUid: resolvedRecipient,
          giftId: body.giftId,
          giftName: body.giftName,
          coinsCost: body.coinsCost,
          idempotencyKey: body.idempotencyKey,
          streamId: body.streamId || null
        });
        if (activeStream && !result.duplicate) {
          realtimeEngine.recordStreamGift({
            streamId: activeStream.id,
            coinsCost: result.coinsCost
          });
        }
        return sendJson(res, 200, result);
      }

      if (method === 'POST' && pathname === '/api/billing/verify-play-purchase') {
        const body = await readJsonBody(req);
        if (!body.purchaseToken) {
          throw new CallEngineError('MISSING_PURCHASE_TOKEN', 'رمز شراء Google Play مطلوب', 400);
        }

        // 1. Idempotency check: If purchaseToken was already credited, return idempotent success
        const existingTx = await activeLedger.getIapTransaction(body.purchaseToken);
        if (existingTx) {
          if (existingTx.uid === user.uid) {
            if (!existingTx.acknowledged) {
              const ackRetry = await acknowledgeAndConsumeGooglePlayPurchase({
                packageName: body.packageName || env.ANDROID_PACKAGE_NAME || 'com.aistudio.ayer.live',
                productId: existingTx.productId,
                purchaseToken: body.purchaseToken,
                env,
                fetchImpl,
                customConsumer: playBillingConsumer
              }).catch(() => ({ acknowledged: false }));
              if (ackRetry?.acknowledged) {
                await activeLedger.markIapAcknowledged(body.purchaseToken).catch(() => {});
              }
            }
            const { wallet } = await activeLedger.ensureUser({ uid: user.uid });
            return sendJson(res, 200, {
              wallet,
              creditedCoins: existingTx.coinsCredited,
              alreadyCredited: true,
              duplicate: true
            });
          } else {
            throw new CallEngineError('DUPLICATE_PURCHASE_TOKEN_OTHER_USER', 'رمز الشراء مسجل لحساب مستخدم آخر', 409);
          }
        }

        // 2. Compute expected obfuscated external account id = SHA-256(uid) hex (64 chars)
        const expectedObfuscatedAccountId = crypto.createHash('sha256').update(String(user.uid)).digest('hex');

        // 3. Verify purchase with Google Play Developer API (validates SKU, state, and obfuscatedExternalAccountId)
        const verified = await verifyGooglePlayProductPurchase({
          packageName: body.packageName,
          productId: body.productId,
          purchaseToken: body.purchaseToken,
          expectedObfuscatedAccountId,
          env,
          fetchImpl,
          customVerifier: playBillingVerifier
        });

        // 4. Atomically credit purchase in server ledger BEFORE consuming on Google Play
        const credited = await activeLedger.creditPlayPurchase({
          uid: user.uid,
          displayName: user.name || '',
          email: user.email || '',
          packageId: verified.catalogEntry.packageId,
          productId: verified.catalogEntry.productId,
          coinsCredited: verified.catalogEntry.totalCoins,
          priceUsd: verified.catalogEntry.priceUsd,
          purchaseToken: body.purchaseToken,
          orderId: verified.orderId || body.orderId || null,
          purchaseState: verified.purchaseState ?? 0,
          acknowledged: verified.acknowledged ?? false,
          obfuscatedExternalAccountId: verified.obfuscatedExternalAccountId || expectedObfuscatedAccountId,
          verificationSource: verified.verificationSource || 'google_play_androidpublisher_v3'
        });

        // 5. Acknowledge & consume on Google Play Developer API strictly AFTER atomic ledger credit
        const ackResult = await acknowledgeAndConsumeGooglePlayPurchase({
          packageName: body.packageName,
          productId: verified.catalogEntry.productId,
          purchaseToken: body.purchaseToken,
          env,
          fetchImpl,
          customConsumer: playBillingConsumer
        }).catch(() => ({ acknowledged: false, consumed: false }));

        if (ackResult?.acknowledged && !verified.acknowledged) {
          await activeLedger.markIapAcknowledged(body.purchaseToken).catch(() => {});
        }

        return sendJson(res, 200, credited);
      }

      // ==========================================================
      // +18 Age Verification Submission
      // ==========================================================
      if (method === 'POST' && pathname === '/api/age-verification/submit') {
        const body = await readJsonBody(req);
        const deviceId = req.headers['x-device-id'] || body.deviceId || null;
        const sub = await activeLedger.submitAgeVerification({
          uid: user.uid,
          displayName: body.displayName || user.name || '',
          email: body.email || user.email || '',
          dob: body.dob,
          acceptedTermsAt: body.acceptedTermsAt,
          acceptedPrivacyAt: body.acceptedPrivacyAt,
          deviceId
        });
        if (sub.status === 'APPROVED') {
          await adminBridge.syncUserClaims({ uid: user.uid, ageVerified: true }).catch(() => {});
        }
        return sendJson(res, 201, { submission: sub });
      }

      // ==========================================================
      // Agency Dashboard & Bank Payout Requests
      // ==========================================================
      if (method === 'GET' && pathname === '/api/agency/dashboard') {
        assertFeatureEnabled(featureFlags.agenciesEnabled, 'AGENCIES_ENABLED', 'لوحة الوكالات');
        return sendJson(res, 200, await activeLedger.getAgencyDashboard(user.uid));
      }

      if (method === 'POST' && pathname === '/api/host/apply') {
        assertFeatureEnabled(featureFlags.agenciesEnabled, 'AGENCIES_ENABLED', 'التقديم كمضيف');
        const body = await readJsonBody(req);
        return sendJson(res, 200, await activeLedger.applyForHost({
          uid: user.uid,
          agencyName: body.agencyName || ''
        }));
      }

      if (method === 'POST' && pathname === '/api/agency/sub-agents') {
        assertFeatureEnabled(featureFlags.agenciesEnabled, 'AGENCIES_ENABLED', 'إدارة الوكلاء');
        const body = await readJsonBody(req);
        return sendJson(res, 201, await activeLedger.recruitSubAgent({
          agentUid: user.uid,
          subAgentName: body.subAgentName || body.name,
          country: body.country || '',
          subAgentUid: body.subAgentUid || null
        }));
      }

      if (method === 'POST' && pathname === '/api/payouts/request') {
        assertFeatureEnabled(featureFlags.payoutsEnabled, 'PAYOUTS_ENABLED', 'سحب الأرباح');
        const body = await readJsonBody(req);
        const created = await activeLedger.createPayoutRequest({
          uid: user.uid,
          amountCoins: body.amountCoins,
          payoutMethod: body.payoutMethod || 'BANK_TRANSFER',
          bankAccountHolder: body.bankAccountHolder,
          bankName: body.bankName,
          ibanOrAccount: body.ibanOrAccount
        });
        return sendJson(res, 201, created);
      }

      if (method === 'GET' && pathname === '/api/payouts') {
        assertFeatureEnabled(featureFlags.payoutsEnabled, 'PAYOUTS_ENABLED', 'سحب الأرباح');
        const status = parsedUrl.searchParams.get('status') || null;
        const items = await activeLedger.listPayoutRequests({ uid: user.uid, isAdmin: false, status });
        return sendJson(res, 200, { payouts: items });
      }

      // ==========================================================
      // Moderation Reports, User Blocking & Account Deletion
      // ==========================================================
      if (method === 'POST' && pathname === '/api/reports') {
        const body = await readJsonBody(req);
        const report = await activeLedger.createModerationReport({
          reportType: body.reportType || 'USER_PROFILE',
          targetUserId: body.targetUserId || null,
          streamId: body.streamId || null,
          reporterUid: user.uid,
          reason: body.reason,
          details: body.details || ''
        });
        return sendJson(res, 201, { report });
      }

      if (method === 'GET' && pathname === '/api/blocks') {
        const blockedUsers = await activeLedger.getBlockedUsers(user.uid);
        return sendJson(res, 200, { blockedUsers });
      }

      if (method === 'POST' && pathname === '/api/blocks') {
        const body = await readJsonBody(req);
        return sendJson(res, 200, await activeLedger.blockUser({
          blockerUid: user.uid,
          blockedUid: body.blockedUid
        }));
      }

      const unblockMatch = pathname.match(/^\/api\/blocks\/([^/]+)$/);
      if (method === 'DELETE' && unblockMatch) {
        return sendJson(res, 200, await activeLedger.unblockUser({
          blockerUid: user.uid,
          blockedUid: decodeURIComponent(unblockMatch[1])
        }));
      }

      if (method === 'DELETE' && pathname === '/api/account') {
        await terminateAllUserRealtimeSessions({ uid: user.uid, activeLedger, reason: 'ACCOUNT_DELETED' });
        const deletedRes = await activeLedger.deleteUserAccount({ uid: user.uid });
        await adminBridge.disableAndRevokeUser({ uid: user.uid, disabled: true, revokeTokens: true }).catch(() => {});
        return sendJson(res, 200, deletedRes);
      }

      // ==========================================================
      // Automated Moderation Sampling (Streams / Calls / Text)
      // ==========================================================
      if (method === 'POST' && pathname === '/api/moderation/samples') {
        const body = await readJsonBody(req, 262_144);
        const sampleResult = await modService.evaluateAndRecordSample({
          ledger: activeLedger,
          userUid: user.uid,
          streamId: body.streamId || null,
          sampleType: body.sampleType || 'STREAM_FRAME',
          imageBase64: body.imageBase64 || null,
          imageUrl: body.imageUrl || null,
          textContent: body.textContent || '',
          clientSignalHint: body.clientSignalHint || null
        });
        let streamEnded = false;
        if (sampleResult.shouldTerminateStream && body.streamId) {
          const targetStream = realtimeEngine.getStream(body.streamId);
          await activeLedger.createModerationReport({
            reportType: 'AUTO_MODERATION_BLOCK',
            targetUserId: targetStream?.hostUid || user.uid,
            streamId: body.streamId,
            reporterUid: 'system_moderation',
            reason: `رصد آلي لمخالفة (${(sampleResult.sample.labels || []).join(', ') || 'CONTENT_VIOLATION'})`,
            details: `sampleId=${sampleResult.sample.id}; provider=${sampleResult.sample.provider}`
          }).catch(() => {});
          await realtimeEngine.endBroadcast({
            streamId: body.streamId,
            actorUid: 'system_moderation',
            systemOverride: true,
            reason: 'AUTOMATED_MODERATION_BLOCKED'
          }).catch(() => {});
          streamEnded = true;
        }
        return sendJson(res, 201, { ...sampleResult, streamEnded });
      }

      // ==========================================================
      // Real Matchmaking Queue (Hold -> Capture / Release) & LiveKit Broadcast Rooms
      // ==========================================================
      if (method === 'POST' && pathname === '/api/match/join') {
        const body = await readJsonBody(req);
        await activeLedger.assertUserCanOperate(user.uid, { requireAgeVerified: true });
        const activeExisting = realtimeEngine.getActiveMatch(user.uid);
        if (activeExisting) {
          const summary = await activeLedger.getWalletSummary(user.uid, 5);
          return sendJson(res, 200, { ...activeExisting, wallet: summary.wallet });
        }

        const prevHoldId = realtimeEngine.takeWaitingHoldId(user.uid);
        if (prevHoldId) {
          await releaseHoldSafely(activeLedger, prevHoldId, 'MATCH_QUEUE_REJOIN');
        }

        const summary = await activeLedger.getWalletSummary(user.uid, 5);
        const requiredCost = calculateMatchCost({
          chatType: body.chatType || 'VIDEO',
          genderFilter: body.genderFilter || 'ANY',
          countryFilter: body.countryFilter || 'ANY'
        });

        const holdRes = await activeLedger.holdCoins({
          uid: user.uid,
          amount: requiredCost,
          purpose: 'MATCH_HOLD',
          idempotencyKey: body.idempotencyKey || `match_hold_${user.uid}_${crypto.randomUUID()}`
        });

        const blockedUids = await activeLedger.getBlockedUsers(user.uid);
        let matchResult;
        try {
          matchResult = realtimeEngine.joinMatchQueue({
            uid: user.uid,
            displayName: body.displayName || summary.user.displayName || user.name || '',
            country: body.country || '',
            countryCode: body.countryCode || 'SA',
            userGender: body.userGender || 'ANY',
            age: summary.user.ageYears,
            allowUnverifiedAdmin: Boolean(summary.user.isAdmin || effectiveUser.isAdmin),
            bio: body.bio || '',
            chatType: body.chatType || 'VIDEO',
            genderFilter: body.genderFilter || 'ANY',
            countryFilter: body.countryFilter || 'ANY',
            blockedUids,
            holdId: holdRes.hold.holdId
          });
        } catch (err) {
          await releaseHoldSafely(activeLedger, holdRes.hold.holdId, 'MATCH_JOIN_FAILED');
          throw err;
        }

        await releaseExpiredQueueHolds(activeLedger, matchResult.expiredEntries);

        if (matchResult.status === 'MATCHED' && matchResult.newlyMatched) {
          if (matchResult.partnerHoldId) {
            try {
              await activeLedger.captureHold({
                holdId: matchResult.partnerHoldId,
                captureAmount: matchResult.partnerCostCoins,
                entryType: 'MATCH_SPEND',
                description: `مطابقة عشوائية (${matchResult.chatType})`,
                creditPlatformRevenue: true,
                idempotencyKey: `match_${matchResult.matchId}_${matchResult.partnerUid}`
              });
            } catch (partnerErr) {
              realtimeEngine.abortMatch(matchResult.matchId);
              await releaseHoldSafely(activeLedger, matchResult.callerHoldId, 'PARTNER_HOLD_CAPTURE_FAILED');
              throw partnerErr;
            }
          }

          let callerCapture;
          try {
            callerCapture = await activeLedger.captureHold({
              holdId: matchResult.callerHoldId,
              captureAmount: matchResult.costCoins,
              entryType: 'MATCH_SPEND',
              description: `مطابقة عشوائية (${matchResult.chatType})`,
              creditPlatformRevenue: true,
              idempotencyKey: `match_${matchResult.matchId}_${user.uid}`
            });
          } catch (callerErr) {
            realtimeEngine.abortMatch(matchResult.matchId);
            await releaseHoldSafely(activeLedger, matchResult.callerHoldId, 'CALLER_HOLD_CAPTURE_FAILED');
            throw callerErr;
          }

          matchResult.wallet = callerCapture.wallet;
          await activeLedger.recordServerActivity({ uid: user.uid, activityType: 'MATCH_COMPLETED', count: 1 });
          if (matchResult.partnerUid) {
            await activeLedger.recordServerActivity({ uid: matchResult.partnerUid, activityType: 'MATCH_COMPLETED', count: 1 });
          }
        } else if (matchResult.status === 'WAITING') {
          matchResult.wallet = holdRes.wallet;
        }
        return sendJson(res, 200, matchResult);
      }

      if (method === 'GET' && pathname === '/api/match/poll') {
        const pollRes = realtimeEngine.pollMatchQueue(user.uid);
        await releaseExpiredQueueHolds(activeLedger, pollRes.expiredEntries);
        if (pollRes.status === 'MATCHED') {
          const summary = await activeLedger.getWalletSummary(user.uid, 5);
          pollRes.wallet = summary.wallet;
        }
        return sendJson(res, 200, pollRes);
      }

      if (method === 'POST' && pathname === '/api/match/leave') {
        const body = await readJsonBody(req);
        const leaveRes = await realtimeEngine.leaveMatchQueueOrSession({
          uid: user.uid,
          matchId: body.matchId || null
        });
        if (leaveRes.releasedHoldId) {
          const rel = await releaseHoldSafely(activeLedger, leaveRes.releasedHoldId, 'MATCH_QUEUE_LEFT');
          if (rel?.wallet) {
            leaveRes.wallet = rel.wallet;
          }
        }
        return sendJson(res, 200, leaveRes);
      }

      if (method === 'GET' && pathname === '/api/streams') {
        const blockedUids = await activeLedger.getBlockedUsers(user.uid);
        const category = parsedUrl.searchParams.get('category') || null;
        return sendJson(res, 200, { streams: realtimeEngine.listLiveStreams({ category, blockedUids }) });
      }

      if (method === 'POST' && pathname === '/api/streams') {
        const body = await readJsonBody(req);
        const { user: activeUser } = await activeLedger.assertUserCanOperate(user.uid, { requireAgeVerified: true });
        const created = realtimeEngine.startBroadcast({
          hostUid: user.uid,
          hostName: body.hostName || activeUser.displayName || user.name || '',
          hostCountry: body.hostCountry || '',
          hostCountryCode: body.hostCountryCode || 'SA',
          title: body.title || 'بث مباشر على HUH',
          category: body.category || 'تعارف وسوالف',
          agencyName: activeUser.agencyName || null,
          isVip: Boolean(activeUser.isHost || activeUser.isAgent)
        });
        return sendJson(res, 201, created);
      }

      const streamJoinMatch = pathname.match(/^\/api\/streams\/([^/]+)\/join$/);
      if (method === 'POST' && streamJoinMatch) {
        await activeLedger.assertUserCanOperate(user.uid, { requireAgeVerified: true });
        const blockedUids = await activeLedger.getBlockedUsers(user.uid);
        const joined = realtimeEngine.joinBroadcast({
          streamId: decodeURIComponent(streamJoinMatch[1]),
          viewerUid: user.uid,
          viewerName: user.name || '',
          blockedUids
        });
        if (joined.stream && joined.stream.hostUid !== user.uid) {
          await activeLedger.recordServerActivity({ uid: user.uid, activityType: 'STREAM_WATCHED', count: 1 });
        }
        return sendJson(res, 200, joined);
      }

      const streamLeaveMatch = pathname.match(/^\/api\/streams\/([^/]+)\/leave$/);
      if (method === 'POST' && streamLeaveMatch) {
        return sendJson(res, 200, await realtimeEngine.leaveBroadcast({
          streamId: decodeURIComponent(streamLeaveMatch[1]),
          viewerUid: user.uid
        }));
      }

      const streamEmergencyStopMatch = pathname.match(/^\/api\/streams\/([^/]+)\/emergency-stop$/);
      if (method === 'POST' && streamEmergencyStopMatch) {
        const streamId = decodeURIComponent(streamEmergencyStopMatch[1]);
        const body = await readJsonBody(req);
        const targetStream = realtimeEngine.getStream(streamId);
        if (!targetStream) {
          throw new CallEngineError('STREAM_NOT_FOUND', 'البث غير موجود أو انتهى بالفعل', 404);
        }
        const reasonText = String(body.reason || 'إيقاف طارئ لحماية السلامة (+18)').trim().slice(0, 300);
        const report = await activeLedger.createModerationReport({
          reportType: 'EMERGENCY_STREAM_STOP',
          targetUserId: targetStream.hostUid,
          streamId,
          reporterUid: user.uid,
          reason: reasonText,
          details: 'EMERGENCY_STOP_TRIGGERED'
        });
        const sample = await activeLedger.createModerationSample({
          streamId,
          userUid: targetStream.hostUid,
          sampleType: 'EMERGENCY_STOP',
          provider: 'user_emergency_stop',
          status: MODERATION_STATUS.BLOCKED,
          riskScore: 1,
          labels: ['EMERGENCY_STOP'],
          estimatedCostUsd: 0,
          actionTaken: 'STREAM_EMERGENCY_STOPPED',
          details: `triggeredBy=${user.uid}; reportId=${report.id}; reason=${reasonText}`,
          incrementUsage: false
        });
        const ended = await realtimeEngine.endBroadcast({
          streamId,
          actorUid: user.uid,
          isAdmin: effectiveUser.isAdmin,
          systemOverride: true,
          reason: 'EMERGENCY_STOP'
        });
        return sendJson(res, 200, {
          ok: true,
          streamEnded: true,
          action: 'STREAM_EMERGENCY_STOPPED',
          stream: ended.stream,
          report,
          sample
        });
      }

      const streamEndMatch = pathname.match(/^\/api\/streams\/([^/]+)\/end$/);
      if (method === 'POST' && streamEndMatch) {
        return sendJson(res, 200, await realtimeEngine.endBroadcast({
          streamId: decodeURIComponent(streamEndMatch[1]),
          actorUid: user.uid,
          isAdmin: effectiveUser.isAdmin
        }));
      }

      // ==========================================================
      // 1-on-1 LiveKit Call Lifecycle
      // ==========================================================
      if (pathname.startsWith('/api/calls/')) {
        await getCallStoreAndHydrate();
      }

      if (method === 'POST' && pathname === '/api/calls/initiate') {
        const body = await readJsonBody(req);
        await activeLedger.assertUserCanOperate(user.uid, { requireAgeVerified: true });
        const result = callEngine.initiateCall({
          callerId: user.uid, // strictly derived from verified auth token
          callerName: body.callerName || user.name || user.uid,
          calleeId: body.calleeId,
          calleeName: body.calleeName || body.calleeId,
          callType: body.callType || 'AUDIO'
        });
        await persistCallMutation({ calls: [result.call] });
        return sendJson(res, 201, result);
      }

      const acceptMatch = pathname.match(/^\/api\/calls\/([^/]+)\/accept$/);
      if (method === 'POST' && acceptMatch) {
        const result = callEngine.acceptCall({
          callId: acceptMatch[1],
          calleeId: user.uid
        });
        await persistCallMutation({ calls: [result.call] });
        return sendJson(res, 200, result);
      }

      const rejectMatch = pathname.match(/^\/api\/calls\/([^/]+)\/reject$/);
      if (method === 'POST' && rejectMatch) {
        const body = await readJsonBody(req);
        const result = await callEngine.rejectCall({
          callId: rejectMatch[1],
          actorId: user.uid,
          reason: body.reason || 'DECLINED'
        });
        await persistCallMutation({ calls: [result.call] });
        return sendJson(res, 200, result);
      }

      const connMatch = pathname.match(/^\/api\/calls\/([^/]+)\/connection-state$/);
      if (method === 'POST' && connMatch) {
        const body = await readJsonBody(req);
        const result = callEngine.reportConnectionState({
          callId: connMatch[1],
          actorId: user.uid,
          state: body.state
        });
        await persistCallMutation({ calls: [result] });
        return sendJson(res, 200, { call: result });
      }

      const reconnMatch = pathname.match(/^\/api\/calls\/([^/]+)\/reconnect-token$/);
      if (method === 'POST' && reconnMatch) {
        const result = callEngine.issueReconnectToken({
          callId: reconnMatch[1],
          requesterId: user.uid
        });
        return sendJson(res, 200, result);
      }

      const endMatch = pathname.match(/^\/api\/calls\/([^/]+)\/end$/);
      if (method === 'POST' && endMatch) {
        const body = await readJsonBody(req);
        const result = await callEngine.endCall({
          callId: endMatch[1],
          actorId: user.uid,
          finalStatus: body.finalStatus || null,
          reason: body.reason || null
        });
        await persistCallMutation({ calls: [result.call] });
        return sendJson(res, 200, result);
      }

      // ==========================================================
      // Admin Governance, 4-Role RBAC, Reviews & Monitoring (/api/admin/*)
      // ==========================================================
      if (pathname.startsWith('/api/admin/')) {
        if (!effectiveUser.isAdmin || effectiveUser.adminRole === 'NONE') {
          return sendJson(res, 403, { error: 'FORBIDDEN_ADMIN', message: 'صلاحية الإدارة مطلوبة' });
        }

        if (method === 'GET' && pathname === '/api/admin/age-verifications') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.AGE_REVIEWS);
          const status = parsedUrl.searchParams.get('status') || 'PENDING_REVIEW';
          return sendJson(res, 200, { submissions: await activeLedger.listAgeVerifications(status) });
        }

        const ageReviewMatch = pathname.match(/^\/api\/admin\/age-verifications\/([^/]+)\/review$/);
        if (method === 'POST' && ageReviewMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.AGE_REVIEWS);
          const body = await readJsonBody(req);
          const targetUid = decodeURIComponent(ageReviewMatch[1]);
          const result = await activeLedger.reviewAgeVerification({
            reviewerUid: user.uid,
            targetUid,
            decision: body.decision,
            reason: body.reason || null
          });
          await adminBridge.syncUserClaims({
            uid: targetUid,
            ageVerified: Boolean(result?.user?.ageVerified)
          }).catch(() => {});
          return sendJson(res, 200, result);
        }

        if (method === 'GET' && pathname === '/api/admin/payouts') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.PAYOUTS_REVIEW);
          assertFeatureEnabled(featureFlags.payoutsEnabled, 'PAYOUTS_ENABLED', 'مراجعة السحوبات');
          const status = parsedUrl.searchParams.get('status') || null;
          return sendJson(res, 200, {
            payouts: await activeLedger.listPayoutRequests({ uid: user.uid, isAdmin: true, status })
          });
        }

        const payoutReviewMatch = pathname.match(/^\/api\/admin\/payouts\/([^/]+)\/review$/);
        if (method === 'POST' && payoutReviewMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.PAYOUTS_REVIEW);
          assertFeatureEnabled(featureFlags.payoutsEnabled, 'PAYOUTS_ENABLED', 'مراجعة السحوبات');
          const body = await readJsonBody(req);
          const result = await activeLedger.reviewPayoutRequest({
            reviewerUid: user.uid,
            requestId: decodeURIComponent(payoutReviewMatch[1]),
            decision: body.decision,
            note: body.note || null,
            paymentReference: body.paymentReference || null
          });
          return sendJson(res, 200, result);
        }

        const roleMatch = pathname.match(/^\/api\/admin\/users\/([^/]+)\/roles$/);
        if (method === 'POST' && roleMatch) {
          const body = await readJsonBody(req);
          const isEditingAdminPrivilege = body.isAdmin !== undefined || body.adminRole !== undefined;
          assertAdminPermission(
            effectiveUser,
            isEditingAdminPrivilege ? ADMIN_PERMISSIONS.ADMIN_ROLES_WRITE : ADMIN_PERMISSIONS.AGENCY_ROLES_WRITE
          );
          const targetUid = decodeURIComponent(roleMatch[1]);
          const updatedUser = await activeLedger.adminSetUserRolesAndCommission({
            reviewerUid: user.uid,
            reviewerAdminRole: effectiveUser.adminRole,
            targetUid,
            isAgent: body.isAgent,
            isHost: body.isHost,
            isAdmin: body.isAdmin,
            adminRole: body.adminRole,
            agentCommissionPercent: body.agentCommissionPercent,
            hostCommissionPercent: body.hostCommissionPercent,
            agencyName: body.agencyName
          });
          if (isEditingAdminPrivilege) {
            await adminBridge.syncUserClaims({
              uid: targetUid,
              adminRole: updatedUser.adminRole
            }).catch(() => {});
            await adminBridge.disableAndRevokeUser({
              uid: targetUid,
              disabled: updatedUser.status !== 'ACTIVE',
              revokeTokens: true
            }).catch(() => {});
          }
          return sendJson(res, 200, { user: updatedUser });
        }

        const userStatusMatch = pathname.match(/^\/api\/admin\/users\/([^/]+)\/status$/);
        if (method === 'POST' && userStatusMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.USER_STATUS);
          const body = await readJsonBody(req);
          const targetUid = decodeURIComponent(userStatusMatch[1]);
          const result = await activeLedger.setUserStatus({
            reviewerUid: user.uid,
            targetUid,
            status: body.status,
            reason: body.reason || ''
          });
          let terminatedSessions = null;
          if (result.user.status !== 'ACTIVE') {
            terminatedSessions = await terminateAllUserRealtimeSessions({
              uid: targetUid,
              activeLedger,
              reason: `ADMIN_STATUS_${result.user.status}`
            });
          }
          await adminBridge.syncUserClaims({
            uid: targetUid,
            status: result.user.status,
            banned: result.user.status === 'BANNED'
          }).catch(() => {});
          await adminBridge.disableAndRevokeUser({
            uid: targetUid,
            disabled: result.user.status !== 'ACTIVE',
            revokeTokens: true
          }).catch(() => {});
          return sendJson(res, 200, { ...result, terminatedSessions });
        }

        if (method === 'GET' && pathname === '/api/admin/host-applications') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.HOST_APPLICATIONS);
          const status = parsedUrl.searchParams.get('status') || 'PENDING';
          return sendJson(res, 200, { applications: await activeLedger.listHostApplications(status) });
        }

        const hostAppReviewMatch = pathname.match(/^\/api\/admin\/host-applications\/([^/]+)\/review$/);
        if (method === 'POST' && hostAppReviewMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.HOST_APPLICATIONS);
          const body = await readJsonBody(req);
          const result = await activeLedger.reviewHostApplication({
            reviewerUid: user.uid,
            targetUid: decodeURIComponent(hostAppReviewMatch[1]),
            decision: body.decision,
            hostCommissionPercent: body.hostCommissionPercent ?? null,
            reason: body.reason || null
          });
          return sendJson(res, 200, result);
        }

        if (method === 'GET' && pathname === '/api/admin/audit-logs') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.AUDIT_LOGS_READ);
          const logs = await activeLedger.getAuditLogs({
            actorUid: parsedUrl.searchParams.get('actorUid') || null,
            action: parsedUrl.searchParams.get('action') || null,
            targetId: parsedUrl.searchParams.get('targetId') || null,
            limit: parsedUrl.searchParams.get('limit') || 100
          });
          return sendJson(res, 200, { logs });
        }

        if (method === 'GET' && pathname === '/api/admin/economy/config') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.ECONOMY_CONFIG_READ);
          return sendJson(res, 200, await activeLedger.getEconomyConfig());
        }

        if (method === 'PUT' && pathname === '/api/admin/economy/config') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.ECONOMY_CONFIG_WRITE);
          const body = await readJsonBody(req);
          const updatedConfig = await activeLedger.updateEconomyConfig({
            reviewerUid: user.uid,
            hostCommissionPercent: body.hostCommissionPercent,
            agentCommissionPercent: body.agentCommissionPercent
          });
          return sendJson(res, 200, updatedConfig);
        }

        if (method === 'POST' && pathname === '/api/admin/billing/process-voided-purchase') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.BILLING_VOID);
          const body = await readJsonBody(req);
          if (!body.purchaseToken) {
            throw new CallEngineError('MISSING_PURCHASE_TOKEN', 'رمز الشراء مطلوب', 400);
          }
          const result = await activeLedger.processVoidedPurchase({
            purchaseToken: body.purchaseToken,
            reason: body.reason || 'ADMIN_MANUAL_VOID'
          });
          return sendJson(res, 200, result);
        }

        if (method === 'GET' && pathname === '/api/admin/reports') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.REPORTS_MODERATION);
          const status = parsedUrl.searchParams.get('status') || 'PENDING_REVIEW';
          return sendJson(res, 200, { reports: await activeLedger.listModerationReports(status) });
        }

        const repResolveMatch = pathname.match(/^\/api\/admin\/reports\/([^/]+)\/resolve$/);
        if (method === 'POST' && repResolveMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.REPORTS_MODERATION);
          const body = await readJsonBody(req);
          const resolved = await activeLedger.resolveModerationReport({
            reviewerUid: user.uid,
            reportId: decodeURIComponent(repResolveMatch[1]),
            status: body.status || 'RESOLVED',
            actionTaken: body.actionTaken || 'WARNING'
          });
          if (resolved.streamId && (body.actionTaken === 'END_STREAM' || body.actionTaken === 'SUSPEND_USER' || body.actionTaken === 'BAN_USER')) {
            await realtimeEngine.endBroadcast({
              streamId: resolved.streamId,
              actorUid: user.uid,
              isAdmin: true,
              reason: 'ADMIN_MODERATION'
            }).catch(() => {});
          }
          if (resolved.targetUserId && (body.actionTaken === 'SUSPEND_USER' || body.actionTaken === 'BAN_USER')) {
            const newStatus = body.actionTaken === 'BAN_USER' ? 'BANNED' : 'SUSPENDED';
            await terminateAllUserRealtimeSessions({
              uid: resolved.targetUserId,
              activeLedger,
              reason: `MODERATION_${newStatus}`
            });
            await adminBridge.syncUserClaims({
              uid: resolved.targetUserId,
              status: newStatus,
              banned: newStatus === 'BANNED'
            }).catch(() => {});
            await adminBridge.disableAndRevokeUser({
              uid: resolved.targetUserId,
              disabled: true,
              revokeTokens: true
            }).catch(() => {});
          }
          return sendJson(res, 200, { report: resolved });
        }

        if (method === 'GET' && pathname === '/api/admin/moderation/samples') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.MODERATION_SAMPLES);
          const status = parsedUrl.searchParams.get('status') || 'QUEUE';
          return sendJson(res, 200, { samples: await activeLedger.listModerationSamples(status) });
        }

        const sampleReviewMatch = pathname.match(/^\/api\/admin\/moderation\/samples\/([^/]+)\/review$/);
        if (method === 'POST' && sampleReviewMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.MODERATION_SAMPLES);
          const body = await readJsonBody(req);
          const updatedSample = await activeLedger.reviewModerationSample({
            reviewerUid: user.uid,
            sampleId: decodeURIComponent(sampleReviewMatch[1]),
            decision: body.decision,
            note: body.note || null
          });
          if (String(body.decision || '').toUpperCase() === 'CONFIRMED' && updatedSample.streamId) {
            await realtimeEngine.endBroadcast({
              streamId: updatedSample.streamId,
              actorUid: user.uid,
              isAdmin: true,
              reason: 'ADMIN_CONFIRMED_MODERATION_VIOLATION'
            }).catch(() => {});
          }
          return sendJson(res, 200, { sample: updatedSample });
        }

        const adminStreamEndMatch = pathname.match(/^\/api\/admin\/streams\/([^/]+)\/end$/);
        if (method === 'POST' && adminStreamEndMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.STREAM_TERMINATE);
          return sendJson(res, 200, await realtimeEngine.endBroadcast({
            streamId: decodeURIComponent(adminStreamEndMatch[1]),
            actorUid: user.uid,
            isAdmin: true,
            reason: 'ADMIN_TERMINATED'
          }));
        }

        if (pathname.startsWith('/api/admin/calls/')) {
          await getCallStoreAndHydrate();
        }

        if (method === 'GET' && pathname === '/api/admin/calls/monitoring') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.CALLS_READ);
          return sendJson(res, 200, callEngine.getMonitoringSnapshot());
        }

        if (method === 'GET' && pathname === '/api/admin/calls/logs') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.CALLS_READ);
          const limit = parsedUrl.searchParams.get('limit') || '200';
          const status = parsedUrl.searchParams.get('status') || null;
          const callType = parsedUrl.searchParams.get('callType') || null;
          const userId = parsedUrl.searchParams.get('userId') || null;
          return sendJson(res, 200, callEngine.getCompletedCallLogs({ limit, status, callType, userId }));
        }

        if (method === 'GET' && pathname === '/api/admin/calls/config') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.CALLS_READ);
          return sendJson(res, 200, callEngine.getConfig());
        }

        if (method === 'PUT' && pathname === '/api/admin/calls/config') {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.CALLS_WRITE);
          const body = await readJsonBody(req);
          const updated = callEngine.updateConfig(body);
          await persistCallMutation({ config: updated });
          return sendJson(res, 200, updated);
        }

        const ackMatch = pathname.match(/^\/api\/admin\/calls\/alerts\/([^/]+)\/ack$/);
        if (method === 'POST' && ackMatch) {
          assertAdminPermission(effectiveUser, ADMIN_PERMISSIONS.CALLS_WRITE);
          const updated = callEngine.acknowledgeAlert(ackMatch[1]);
          if (!updated) {
            return sendJson(res, 404, { error: 'ALERT_NOT_FOUND', message: 'التنبيه غير موجود' });
          }
          await persistCallMutation({ alerts: [updated] });
          return sendJson(res, 200, updated);
        }
      }

      return sendJson(res, 404, { error: 'NOT_FOUND', message: 'Endpoint not found' });
    } catch (err) {
      if (err instanceof CallEngineError) {
        return sendJson(res, err.statusCode, {
          error: err.code,
          message: err.message,
          details: err.details
        });
      }
      // Never leak stack traces or API secrets in HTTP responses
      return sendJson(res, 500, {
        error: 'INTERNAL_ERROR',
        message: 'حدث خطأ داخلي في الخادم'
      });
    }
  });

  return {
    server,
    callEngine,
    realtimeEngine,
    getLedger,
    getCallStore: getCallStoreAndHydrate,
    persistNow: save,
    persistCallMutation
  };
}

// Start server when executed directly
if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT || 8080);
  const envWithDefaultStore = {
    ...process.env,
    DATA_FILE: process.env.DATABASE_URL ? '' : (process.env.DATA_FILE || './data/calls-store.json')
  };
  const { server, callEngine, getLedger, getCallStore, persistCallMutation } = createCallServer({ env: envWithDefaultStore });
  Promise.all([getLedger(), getCallStore()])
    .then(([store, callStore]) => console.log(`[ayer-calls-backend] Ready (ledger=${store.mode}, calls=${callStore.mode})`))
    .catch((err) => console.error('[ayer-calls-backend] Initialization failed:', err.message));

  const sweepTimer = setInterval(async () => {
    try {
      await getCallStore();
      const closed = await callEngine.sweepStaleCalls();
      if (closed.length > 0) {
        await persistCallMutation({ calls: closed });
      }
    } catch {
      // non-fatal sweeper tick
    }
  }, 15_000);
  sweepTimer.unref();

  server.listen(port, () => {
    console.log(`[ayer-calls-backend] Listening on port ${port}`);
  });
}

