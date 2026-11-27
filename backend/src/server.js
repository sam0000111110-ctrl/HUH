import crypto from 'node:crypto';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { AuthoritativeCallEngine, CallEngineError } from './callEngine.js';
import { LiveKitRoomClient, verifyHs256Jwt } from './livekitJwt.js';
import { authenticateRequest } from './authMiddleware.js';
import { createLedgerStore, MemoryLedgerStore } from './ledgerStore.js';
import { verifyGooglePlayProductPurchase, acknowledgeAndConsumeGooglePlayPurchase } from './playBillingVerifier.js';
import { RealtimeBroadcastAndMatchEngine, calculateMatchCost } from './realtimeEngine.js';

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

function loadPersistedState(dataFile) {
  if (!dataFile) return null;
  try {
    if (fs.existsSync(dataFile)) {
      const parsed = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
      return parsed && typeof parsed === 'object' ? parsed : null;
    }
  } catch {
    return null;
  }
  return null;
}

function persistState(dataFile, engine) {
  if (!dataFile) return;
  try {
    const resolved = path.resolve(dataFile);
    const dir = path.dirname(resolved);
    fs.mkdirSync(dir, { recursive: true });
    const tmpFile = `${resolved}.${process.pid}.tmp`;
    fs.writeFileSync(tmpFile, JSON.stringify(engine.exportState(), null, 2), 'utf8');
    fs.renameSync(tmpFile, resolved);
  } catch {
    // non-fatal disk flush
  }
}

export function createCallServer({
  env = process.env,
  engine = null,
  ledger = null,
  tokenVerifier = null,
  playBillingVerifier = null,
  playBillingConsumer = null,
  fetchImpl = globalThis.fetch
} = {}) {
  const livekitUrl = env.LIVEKIT_URL || '';
  const apiKey = env.LIVEKIT_API_KEY || '';
  const apiSecret = env.LIVEKIT_API_SECRET || '';
  const dataFile = env.DATA_FILE || '';

  const roomClient = new LiveKitRoomClient({ livekitUrl, apiKey, apiSecret });

  const callEngine = engine || new AuthoritativeCallEngine({
    livekitUrl,
    apiKey,
    apiSecret,
    roomCloser: (roomName) => roomClient.deleteRoom(roomName),
    initialState: loadPersistedState(dataFile)
  });

  const realtimeEngine = new RealtimeBroadcastAndMatchEngine({
    livekitUrl,
    apiKey,
    apiSecret,
    roomCloser: (roomName) => roomClient.deleteRoom(roomName)
  });

  const ledgerStore = ledger || (env.DATABASE_URL ? null : new MemoryLedgerStore());
  let resolvedLedger = ledgerStore;
  let ledgerPromise = ledgerStore ? Promise.resolve(ledgerStore) : null;
  const getLedger = async () => {
    if (resolvedLedger) return resolvedLedger;
    if (!ledgerPromise) {
      ledgerPromise = createLedgerStore(env).then((store) => {
        resolvedLedger = store;
        return store;
      });
    }
    return ledgerPromise;
  };

  const save = () => persistState(dataFile, callEngine);
  const featureFlags = resolveFeatureFlags(env);

  const server = http.createServer(async (req, res) => {
    try {
      const parsedUrl = new URL(req.url || '/', 'http://localhost');
      const pathname = parsedUrl.pathname;
      const method = req.method || 'GET';

      if (method === 'GET' && pathname === '/health') {
        const activeLedger = await getLedger();
        return sendJson(res, 200, {
          status: 'ok',
          provider: 'livekit',
          livekitConfigured: Boolean(livekitUrl && apiKey && apiSecret),
          storageMode: activeLedger.mode,
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
          const matchingCall = Array.from(callEngine.calls.values()).find((c) => c.roomName === roomName);
          if (matchingCall && (eventName === 'room_finished' || eventName === 'participant_left')) {
            await callEngine.endCall({
              callId: matchingCall.callId,
              actorId: 'livekit_webhook',
              reason: `WEBHOOK_${eventName.toUpperCase()}`,
              systemOverride: true
            });
            save();
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

      const activeLedger = await getLedger();

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
        if (user.isAdmin && !summary.user.isAdmin) {
          summary.user = await activeLedger.adminSetUserRolesAndCommission({
            reviewerUid: 'system_auth_claims',
            targetUid: user.uid,
            isAdmin: true
          });
        }
        return sendJson(res, 200, { ...summary, featureFlags });
      }

      if (method === 'GET' && pathname === '/api/wallet/summary') {
        const limit = Number(parsedUrl.searchParams.get('limit') || '50');
        const summary = await activeLedger.getWalletSummary(user.uid, limit);
        return sendJson(res, 200, { ...summary, featureFlags });
      }

      if (method === 'POST' && pathname === '/api/wallet/daily-bonus') {
        const result = await activeLedger.claimDailyBonus({ uid: user.uid });
        return sendJson(res, 200, result);
      }

      if (method === 'POST' && pathname === '/api/wallet/mission-reward') {
        assertFeatureEnabled(featureFlags.missionRewardsEnabled, 'MISSION_REWARDS_ENABLED', 'مكافآت المهام');
        const body = await readJsonBody(req);
        const result = await activeLedger.claimMissionReward({
          uid: user.uid,
          missionId: body.missionId
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
            coinsCost: body.coinsCost
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
        const sub = await activeLedger.submitAgeVerification({
          uid: user.uid,
          displayName: body.displayName || user.name || '',
          email: body.email || user.email || '',
          dob: body.dob,
          ageYears: body.ageYears,
          acceptedTermsAt: body.acceptedTermsAt,
          acceptedPrivacyAt: body.acceptedPrivacyAt
        });
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
        return sendJson(res, 200, await activeLedger.deleteUserAccount({ uid: user.uid }));
      }

      // ==========================================================
      // Real Matchmaking Queue & LiveKit Broadcast Rooms
      // ==========================================================
      if (method === 'POST' && pathname === '/api/match/join') {
        const body = await readJsonBody(req);
        const summary = await activeLedger.getWalletSummary(user.uid, 5);
        const requiredCost = calculateMatchCost({
          chatType: body.chatType || 'VIDEO',
          genderFilter: body.genderFilter || 'ANY',
          countryFilter: body.countryFilter || 'ANY'
        });
        if ((summary.wallet?.pointsBalance || 0) < requiredCost) {
          throw new CallEngineError('INSUFFICIENT_COINS', `رصيدك غير كافٍ. تحتاج ${requiredCost} نقطة للمطابقة`, 400);
        }
        const blockedUids = await activeLedger.getBlockedUsers(user.uid);
        const matchResult = realtimeEngine.joinMatchQueue({
          uid: user.uid,
          displayName: body.displayName || summary.user.displayName || user.name || '',
          country: body.country || '',
          countryCode: body.countryCode || 'SA',
          userGender: body.userGender || 'ANY',
          age: summary.user.ageYears || 21,
          bio: body.bio || '',
          chatType: body.chatType || 'VIDEO',
          genderFilter: body.genderFilter || 'ANY',
          countryFilter: body.countryFilter || 'ANY',
          blockedUids
        });
        if (matchResult.status === 'MATCHED' && matchResult.newlyMatched) {
          const callerDebit = await activeLedger.spendCoins({
            uid: user.uid,
            amount: matchResult.costCoins,
            entryType: 'MATCH_SPEND',
            description: `مطابقة عشوائية (${matchResult.chatType})`,
            idempotencyKey: `match_${matchResult.matchId}_${user.uid}`,
            referenceId: matchResult.matchId
          });
          matchResult.wallet = callerDebit.wallet;
          if (matchResult.partnerUid && matchResult.partnerCostCoins > 0) {
            await activeLedger.spendCoins({
              uid: matchResult.partnerUid,
              amount: matchResult.partnerCostCoins,
              entryType: 'MATCH_SPEND',
              description: `مطابقة عشوائية (${matchResult.chatType})`,
              idempotencyKey: `match_${matchResult.matchId}_${matchResult.partnerUid}`,
              referenceId: matchResult.matchId
            }).catch(() => null);
          }
        }
        return sendJson(res, 200, matchResult);
      }

      if (method === 'GET' && pathname === '/api/match/poll') {
        const pollRes = realtimeEngine.pollMatchQueue(user.uid);
        if (pollRes.status === 'MATCHED') {
          const summary = await activeLedger.getWalletSummary(user.uid, 5);
          pollRes.wallet = summary.wallet;
        }
        return sendJson(res, 200, pollRes);
      }

      if (method === 'POST' && pathname === '/api/match/leave') {
        const body = await readJsonBody(req);
        return sendJson(res, 200, await realtimeEngine.leaveMatchQueueOrSession({
          uid: user.uid,
          matchId: body.matchId || null
        }));
      }

      if (method === 'GET' && pathname === '/api/streams') {
        const blockedUids = await activeLedger.getBlockedUsers(user.uid);
        const category = parsedUrl.searchParams.get('category') || null;
        return sendJson(res, 200, { streams: realtimeEngine.listLiveStreams({ category, blockedUids }) });
      }

      if (method === 'POST' && pathname === '/api/streams') {
        const body = await readJsonBody(req);
        const summary = await activeLedger.getWalletSummary(user.uid, 5);
        if (!summary.user.ageVerified && !summary.user.isAdmin) {
          throw new CallEngineError('AGE_VERIFICATION_REQUIRED', 'يلزم توثيق العمر (+18) قبل بدء البث المباشر', 403);
        }
        const created = realtimeEngine.startBroadcast({
          hostUid: user.uid,
          hostName: body.hostName || summary.user.displayName || user.name || '',
          hostCountry: body.hostCountry || '',
          hostCountryCode: body.hostCountryCode || 'SA',
          title: body.title || 'بث مباشر على HUH',
          category: body.category || 'تعارف وسوالف',
          agencyName: summary.user.agencyName || null,
          isVip: Boolean(summary.user.isHost || summary.user.isAgent)
        });
        return sendJson(res, 201, created);
      }

      const streamJoinMatch = pathname.match(/^\/api\/streams\/([^/]+)\/join$/);
      if (method === 'POST' && streamJoinMatch) {
        const blockedUids = await activeLedger.getBlockedUsers(user.uid);
        const joined = realtimeEngine.joinBroadcast({
          streamId: decodeURIComponent(streamJoinMatch[1]),
          viewerUid: user.uid,
          viewerName: user.name || '',
          blockedUids
        });
        return sendJson(res, 200, joined);
      }

      const streamLeaveMatch = pathname.match(/^\/api\/streams\/([^/]+)\/leave$/);
      if (method === 'POST' && streamLeaveMatch) {
        return sendJson(res, 200, await realtimeEngine.leaveBroadcast({
          streamId: decodeURIComponent(streamLeaveMatch[1]),
          viewerUid: user.uid
        }));
      }

      const streamEndMatch = pathname.match(/^\/api\/streams\/([^/]+)\/end$/);
      if (method === 'POST' && streamEndMatch) {
        return sendJson(res, 200, await realtimeEngine.endBroadcast({
          streamId: decodeURIComponent(streamEndMatch[1]),
          actorUid: user.uid,
          isAdmin: user.isAdmin
        }));
      }

      // ==========================================================
      // 1-on-1 LiveKit Call Lifecycle
      // ==========================================================
      if (method === 'POST' && pathname === '/api/calls/initiate') {
        const body = await readJsonBody(req);
        const result = callEngine.initiateCall({
          callerId: user.uid, // strictly derived from verified auth token
          callerName: body.callerName || user.name || user.uid,
          calleeId: body.calleeId,
          calleeName: body.calleeName || body.calleeId,
          callType: body.callType || 'AUDIO'
        });
        save();
        return sendJson(res, 201, result);
      }

      const acceptMatch = pathname.match(/^\/api\/calls\/([^/]+)\/accept$/);
      if (method === 'POST' && acceptMatch) {
        const result = callEngine.acceptCall({
          callId: acceptMatch[1],
          calleeId: user.uid
        });
        save();
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
        save();
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
        save();
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
        save();
        return sendJson(res, 200, result);
      }

      // ==========================================================
      // Admin Governance, Reviews & Monitoring (/api/admin/*)
      // ==========================================================
      if (pathname.startsWith('/api/admin/')) {
        if (!user.isAdmin) {
          return sendJson(res, 403, { error: 'FORBIDDEN_ADMIN', message: 'صلاحية الإدارة مطلوبة' });
        }

        if (method === 'GET' && pathname === '/api/admin/age-verifications') {
          const status = parsedUrl.searchParams.get('status') || 'PENDING_REVIEW';
          return sendJson(res, 200, { submissions: await activeLedger.listAgeVerifications(status) });
        }

        const ageReviewMatch = pathname.match(/^\/api\/admin\/age-verifications\/([^/]+)\/review$/);
        if (method === 'POST' && ageReviewMatch) {
          const body = await readJsonBody(req);
          const result = await activeLedger.reviewAgeVerification({
            reviewerUid: user.uid,
            targetUid: decodeURIComponent(ageReviewMatch[1]),
            decision: body.decision,
            reason: body.reason || null
          });
          return sendJson(res, 200, result);
        }

        if (method === 'GET' && pathname === '/api/admin/payouts') {
          assertFeatureEnabled(featureFlags.payoutsEnabled, 'PAYOUTS_ENABLED', 'مراجعة السحوبات');
          const status = parsedUrl.searchParams.get('status') || null;
          return sendJson(res, 200, {
            payouts: await activeLedger.listPayoutRequests({ uid: user.uid, isAdmin: true, status })
          });
        }

        const payoutReviewMatch = pathname.match(/^\/api\/admin\/payouts\/([^/]+)\/review$/);
        if (method === 'POST' && payoutReviewMatch) {
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
          const updatedUser = await activeLedger.adminSetUserRolesAndCommission({
            reviewerUid: user.uid,
            targetUid: decodeURIComponent(roleMatch[1]),
            isAgent: body.isAgent,
            isHost: body.isHost,
            isAdmin: body.isAdmin,
            agentCommissionPercent: body.agentCommissionPercent,
            hostCommissionPercent: body.hostCommissionPercent,
            agencyName: body.agencyName
          });
          return sendJson(res, 200, { user: updatedUser });
        }

        if (method === 'POST' && pathname === '/api/admin/billing/process-voided-purchase') {
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
          const status = parsedUrl.searchParams.get('status') || 'PENDING_REVIEW';
          return sendJson(res, 200, { reports: await activeLedger.listModerationReports(status) });
        }

        const repResolveMatch = pathname.match(/^\/api\/admin\/reports\/([^/]+)\/resolve$/);
        if (method === 'POST' && repResolveMatch) {
          const body = await readJsonBody(req);
          const resolved = await activeLedger.resolveModerationReport({
            reviewerUid: user.uid,
            reportId: decodeURIComponent(repResolveMatch[1]),
            status: body.status || 'RESOLVED',
            actionTaken: body.actionTaken || 'WARNING'
          });
          if (resolved.streamId && (body.actionTaken === 'END_STREAM' || body.actionTaken === 'SUSPEND_USER')) {
            await realtimeEngine.endBroadcast({
              streamId: resolved.streamId,
              actorUid: user.uid,
              isAdmin: true,
              reason: 'ADMIN_MODERATION'
            }).catch(() => {});
          }
          return sendJson(res, 200, { report: resolved });
        }

        const adminStreamEndMatch = pathname.match(/^\/api\/admin\/streams\/([^/]+)\/end$/);
        if (method === 'POST' && adminStreamEndMatch) {
          return sendJson(res, 200, await realtimeEngine.endBroadcast({
            streamId: decodeURIComponent(adminStreamEndMatch[1]),
            actorUid: user.uid,
            isAdmin: true,
            reason: 'ADMIN_TERMINATED'
          }));
        }

        if (method === 'GET' && pathname === '/api/admin/calls/monitoring') {
          return sendJson(res, 200, callEngine.getMonitoringSnapshot());
        }

        if (method === 'GET' && pathname === '/api/admin/calls/logs') {
          const limit = parsedUrl.searchParams.get('limit') || '200';
          const status = parsedUrl.searchParams.get('status') || null;
          const callType = parsedUrl.searchParams.get('callType') || null;
          const userId = parsedUrl.searchParams.get('userId') || null;
          return sendJson(res, 200, callEngine.getCompletedCallLogs({ limit, status, callType, userId }));
        }

        if (method === 'GET' && pathname === '/api/admin/calls/config') {
          return sendJson(res, 200, callEngine.getConfig());
        }

        if (method === 'PUT' && pathname === '/api/admin/calls/config') {
          const body = await readJsonBody(req);
          const updated = callEngine.updateConfig(body);
          save();
          return sendJson(res, 200, updated);
        }

        const ackMatch = pathname.match(/^\/api\/admin\/calls\/alerts\/([^/]+)\/ack$/);
        if (method === 'POST' && ackMatch) {
          const updated = callEngine.acknowledgeAlert(ackMatch[1]);
          if (!updated) {
            return sendJson(res, 404, { error: 'ALERT_NOT_FOUND', message: 'التنبيه غير موجود' });
          }
          save();
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

  return { server, callEngine, realtimeEngine, getLedger, persistNow: save };
}

// Start server when executed directly
if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT || 8080);
  const envWithDefaultStore = {
    ...process.env,
    DATA_FILE: process.env.DATA_FILE || './data/calls-store.json'
  };
  const { server, callEngine, getLedger, persistNow } = createCallServer({ env: envWithDefaultStore });
  getLedger()
    .then((store) => console.log(`[ayer-calls-backend] Authoritative ledger ready (${store.mode})`))
    .catch((err) => console.error('[ayer-calls-backend] Ledger initialization failed:', err.message));

  const sweepTimer = setInterval(async () => {
    try {
      const closed = await callEngine.sweepStaleCalls();
      if (closed.length > 0) {
        persistNow();
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

