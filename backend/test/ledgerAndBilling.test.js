import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createCallServer } from '../src/server.js';
import { MemoryLedgerStore } from '../src/ledgerStore.js';

function startTestServer({
  nowMsRef = { value: 1_750_000_000_000 },
  enableAllFeatures = true,
  featureFlags = {},
  customPlayBillingVerifier = null
} = {}) {
  const ledger = new MemoryLedgerStore({ nowProvider: () => nowMsRef.value });
  const env = {
    LIVEKIT_URL: 'wss://livekit.test.invalid',
    LIVEKIT_API_KEY: 'lk_key',
    LIVEKIT_API_SECRET: 'lk_secret_12345678901234567890',
    ANDROID_PACKAGE_NAME: 'com.aistudio.ayer.live',
    ADMIN_UIDS: 'admin_1',
    ...(enableAllFeatures
      ? {
          PAYOUTS_ENABLED: 'true',
          MISSION_REWARDS_ENABLED: 'true',
          PAID_MEDIA_ENABLED: 'true',
          AGENCIES_ENABLED: 'true'
        }
      : {}),
    ...featureFlags
  };
  const playBillingVerifier = customPlayBillingVerifier || (async ({ productId, purchaseToken, expectedObfuscatedAccountId }) => ({
    orderId: `GPA.${purchaseToken.slice(0, 8)}`,
    purchaseState: 0,
    acknowledged: true,
    obfuscatedExternalAccountId: expectedObfuscatedAccountId || null,
    verificationSource: 'mock_google_play_v3_verifier',
    productId
  }));
  const tokenVerifier = async (token) => {
    if (token.startsWith('u:')) {
      const uid = token.slice(2);
      return { uid, name: uid, email: `${uid}@example.com`, isAdmin: uid === 'admin_1' };
    }
    return null;
  };
  const { server } = createCallServer({ env, ledger, tokenVerifier, playBillingVerifier });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address();
      resolve({
        baseUrl: `http://127.0.0.1:${addr.port}`,
        ledger,
        nowMsRef,
        close: () => new Promise((r) => server.close(r))
      });
    });
  });
}

async function api(baseUrl, method, path, token, body = null) {
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    ...(body !== null ? { body: JSON.stringify(body) } : {})
  });
  const json = await res.json();
  return { status: res.status, json };
}

async function approveAdult(ledger, uid) {
  await ledger.submitAgeVerification({
    uid,
    displayName: uid,
    email: `${uid}@example.com`,
    dob: '1995-05-12',
    acceptedTermsAt: 1000,
    acceptedPrivacyAt: 1000
  });
  await ledger.reviewAgeVerification({ reviewerUid: 'admin_1', targetUid: uid, decision: 'APPROVED' });
}

test('Google Play purchaseToken is idempotent for same user, rejects replay by other users, and enforces server catalog', async () => {
  const srv = await startTestServer();
  try {
    const reqs = Array.from({ length: 8 }, () =>
      api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:buyer_1', {
        packageName: 'com.aistudio.ayer.live',
        productId: 'ayer_coins_100',
        purchaseToken: 'pt_unique_replay_001'
      })
    );
    const results = await Promise.all(reqs);
    const ok = results.filter((r) => r.status === 200);

    // All 8 requests from same user succeed with 200 OK (1 initial, 7 idempotent duplicates)
    assert.equal(ok.length, 8);
    assert.equal(ok[0].json.creditedCoins, 110);
    const duplicates = ok.filter((r) => r.json.alreadyCredited === true || r.json.duplicate === true);
    assert.equal(duplicates.length, 7);

    // Points balance must be strictly 110 (credited exactly once, never multiplied)
    const summary = await api(srv.baseUrl, 'GET', '/api/wallet/summary', 'u:buyer_1');
    assert.equal(summary.json.wallet.pointsBalance, 110);

    // Replay attempt of the exact same purchaseToken by a DIFFERENT user must be rejected with 409
    const hijackAttempt = await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:buyer_2', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_unique_replay_001'
    });
    assert.equal(hijackAttempt.status, 409);
    assert.equal(hijackAttempt.json.error, 'DUPLICATE_PURCHASE_TOKEN_OTHER_USER');

    // Unknown SKU must be rejected
    const badSku = await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:buyer_1', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'hacked_1m_coins',
      purchaseToken: 'pt_unique_002'
    });
    assert.equal(badSku.status, 400);
    assert.equal(badSku.json.error, 'UNKNOWN_PRODUCT_ID');
  } finally {
    await srv.close();
  }
});

test('ACID wallet spend rejects overdrafts under concurrent debits and daily bonus enforces 24h cooldown', async () => {
  const srv = await startTestServer();
  try {
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:user_a', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_user_a_100'
    }); // balance = 110
    await approveAdult(srv.ledger, 'user_a');

    const spends = await Promise.all([
      api(srv.baseUrl, 'POST', '/api/wallet/spend', 'u:user_a', { amount: 80, entryType: 'MATCH_SPEND', idempotencyKey: 'sp_1' }),
      api(srv.baseUrl, 'POST', '/api/wallet/spend', 'u:user_a', { amount: 80, entryType: 'MATCH_SPEND', idempotencyKey: 'sp_2' })
    ]);
    assert.equal(spends.filter((s) => s.status === 200).length, 1);
    assert.equal(spends.filter((s) => s.status === 402).length, 1);

    const bonus1 = await api(srv.baseUrl, 'POST', '/api/wallet/daily-bonus', 'u:user_a', {});
    assert.equal(bonus1.status, 200);
    assert.equal(bonus1.json.creditedCoins, 10);

    const bonusTooSoon = await api(srv.baseUrl, 'POST', '/api/wallet/daily-bonus', 'u:user_a', {});
    assert.equal(bonusTooSoon.status, 429);

    // Advance clock 24h + 1ms
    srv.nowMsRef.value += 24 * 60 * 60 * 1000 + 1;
    const bonus2 = await api(srv.baseUrl, 'POST', '/api/wallet/daily-bonus', 'u:user_a', {});
    assert.equal(bonus2.status, 200);
  } finally {
    await srv.close();
  }
});

test('Atomic gift splits host earnings and parent agency commission and supports payout reserve + reject refund + paid', async () => {
  const srv = await startTestServer();
  try {
    // Non-admin blocked from assigning roles
    const nonAdminRole = await api(srv.baseUrl, 'POST', '/api/admin/users/agent_1/roles', 'u:random_1', {
      isAgent: true
    });
    assert.equal(nonAdminRole.status, 403);

    // Admin promotes agent_1 and host_1
    const agentRole = await api(srv.baseUrl, 'POST', '/api/admin/users/agent_1/roles', 'u:admin_1', {
      isAgent: true,
      agentCommissionPercent: 20,
      agencyName: 'وكالة النور'
    });
    assert.equal(agentRole.status, 200);
    assert.equal(agentRole.json.user.isAgent, true);

    const hostRole = await api(srv.baseUrl, 'POST', '/api/admin/users/host_1/roles', 'u:admin_1', {
      isHost: true,
      hostCommissionPercent: 50
    });
    assert.equal(hostRole.status, 200);

    // agent_1 recruits host_1 as sub-member under agency
    const recruit = await api(srv.baseUrl, 'POST', '/api/agency/sub-agents', 'u:agent_1', {
      subAgentUid: 'host_1',
      subAgentName: 'المضيفة هدى',
      country: 'EG'
    });
    assert.equal(recruit.status, 201);

    // Buyer purchases 1400 coins (pkg_1200) and sends two 600-coin gift_rocket gifts (1200 total) to host_1
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:sender_1', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_1200',
      purchaseToken: 'pt_sender_1200'
    });
    await approveAdult(srv.ledger, 'sender_1');

    const giftA = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:sender_1', {
      recipientUid: 'host_1',
      giftId: 'gift_rocket',
      idempotencyKey: 'gift_tx_001a'
    });
    assert.equal(giftA.status, 200);
    const giftB = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:sender_1', {
      recipientUid: 'host_1',
      giftId: 'gift_rocket',
      idempotencyKey: 'gift_tx_001b'
    });
    assert.equal(giftB.status, 200);
    assert.equal(giftB.json.senderWallet.pointsBalance, 200);
    assert.equal(giftA.json.recipientEarningsAdded + giftB.json.recipientEarningsAdded, 600);
    assert.equal(giftA.json.agentCommissionAdded + giftB.json.agentCommissionAdded, 240);

    // Underage user is permanently locked on underage DOB submission (WP-4 B1)
    const underage = await api(srv.baseUrl, 'POST', '/api/age-verification/submit', 'u:host_underage', {
      dob: '2012-01-01',
      ageYears: 14,
      acceptedTermsAt: 1000,
      acceptedPrivacyAt: 1000
    });
    assert.equal(underage.status, 403);
    assert.equal(underage.json.error, 'UNDERAGE_FORBIDDEN');

    const underageRetry = await api(srv.baseUrl, 'POST', '/api/age-verification/submit', 'u:host_underage', {
      dob: '1995-01-01',
      ageYears: 31,
      acceptedTermsAt: 1000,
      acceptedPrivacyAt: 1000
    });
    assert.equal(underageRetry.status, 403);
    assert.equal(underageRetry.json.error, 'UNDERAGE_LOCKED');

    // host_1 must verify +18 age before payout
    const payoutBeforeVerify = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:host_1', {
      amountCoins: 500,
      bankAccountHolder: 'هدى أحمد',
      bankName: 'بنك مصر',
      ibanOrAccount: 'EG1234567890'
    });
    assert.equal(payoutBeforeVerify.status, 403);

    await api(srv.baseUrl, 'POST', '/api/age-verification/submit', 'u:host_1', {
      dob: '1999-05-12',
      ageYears: 26,
      acceptedTermsAt: 1000,
      acceptedPrivacyAt: 1000
    });
    const pendingAge = await api(srv.baseUrl, 'GET', '/api/admin/age-verifications?status=PENDING_REVIEW', 'u:admin_1');
    assert.equal(pendingAge.status, 200);
    assert.equal(pendingAge.json.submissions.length, 1);

    const approveAge = await api(srv.baseUrl, 'POST', '/api/admin/age-verifications/host_1/review', 'u:admin_1', {
      decision: 'APPROVED'
    });
    assert.equal(approveAge.status, 200);
    assert.equal(approveAge.json.user.ageVerified, true);

    // host_1 submits payout of 500 coins -> reserved
    const p1 = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:host_1', {
      amountCoins: 500,
      bankAccountHolder: 'هدى أحمد',
      bankName: 'بنك مصر',
      ibanOrAccount: 'EG1234567890'
    });
    assert.equal(p1.status, 201);
    assert.equal(p1.json.wallet.hostEarningsCoins, 100);
    assert.equal(p1.json.wallet.reservedPayoutCoins, 500);

    // Admin rejects p1 -> funds refunded atomically
    const rej = await api(srv.baseUrl, 'POST', `/api/admin/payouts/${p1.json.request.id}/review`, 'u:admin_1', {
      decision: 'REJECTED',
      note: 'IBAN mismatch'
    });
    assert.equal(rej.status, 200);
    assert.equal(rej.json.wallet.hostEarningsCoins, 600);
    assert.equal(rej.json.wallet.reservedPayoutCoins, 0);

    // host_1 resubmits -> 24h hold blocks immediate approval, then passes after 24h
    const p2 = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:host_1', {
      amountCoins: 500,
      bankAccountHolder: 'هدى أحمد',
      bankName: 'بنك مصر',
      ibanOrAccount: 'EG999988887777'
    });
    assert.equal(p2.status, 201);
    assert.equal(p2.json.request.ibanOrAccount, '********7777');
    assert.equal(p2.json.request.usdEquivalentCents, 200); // 500 coins @ 250/$1 = $2.00

    const tooEarly = await api(srv.baseUrl, 'POST', `/api/admin/payouts/${p2.json.request.id}/review`, 'u:admin_1', {
      decision: 'PAID',
      paymentReference: 'WIRE-2026-EARLY'
    });
    assert.equal(tooEarly.status, 409);
    assert.equal(tooEarly.json.error, 'PAYOUT_HOLD_ACTIVE');

    srv.nowMsRef.value += 24 * 60 * 60 * 1000 + 1;

    const paid = await api(srv.baseUrl, 'POST', `/api/admin/payouts/${p2.json.request.id}/review`, 'u:admin_1', {
      decision: 'PAID',
      paymentReference: 'WIRE-2026-881'
    });
    assert.equal(paid.status, 200);
    assert.equal(paid.json.request.status, 'PAID');
    assert.equal(paid.json.wallet.reservedPayoutCoins, 0);
    assert.equal(paid.json.wallet.hostEarningsCoins, 100);
  } finally {
    await srv.close();
  }
});

test('Moderation reports, user blocks, and account deletion endpoints work safely', async () => {
  const srv = await startTestServer();
  try {
    const rep = await api(srv.baseUrl, 'POST', '/api/reports', 'u:reporter_1', {
      reportType: 'USER_PROFILE',
      targetUserId: 'bad_user',
      reason: 'محتوى غير لائق'
    });
    assert.equal(rep.status, 201);

    const adminReps = await api(srv.baseUrl, 'GET', '/api/admin/reports?status=PENDING_REVIEW', 'u:admin_1');
    assert.equal(adminReps.status, 200);
    assert.equal(adminReps.json.reports.length, 1);

    const resRep = await api(srv.baseUrl, 'POST', `/api/admin/reports/${rep.json.report.id}/resolve`, 'u:admin_1', {
      status: 'RESOLVED',
      actionTaken: 'SUSPEND_HOST'
    });
    assert.equal(resRep.status, 200);
    assert.equal(resRep.json.report.actionTaken, 'SUSPEND_HOST');

    const blk = await api(srv.baseUrl, 'POST', '/api/blocks', 'u:reporter_1', { blockedUid: 'bad_user' });
    assert.deepEqual(blk.json.blockedUsers, ['bad_user']);

    const del = await api(srv.baseUrl, 'DELETE', '/api/account', 'u:reporter_1');
    assert.equal(del.status, 200);
    assert.equal(del.json.deleted, true);

    // Authoritative mission reward (server-verified activity + daily replay guard)
    const mChatRemoved = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:mission_user', {
      missionId: 'mission_chat'
    });
    assert.equal(mChatRemoved.status, 400);

    const mUnverified = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:mission_user', {
      missionId: 'mission_matches'
    });
    assert.equal(mUnverified.status, 403);
    assert.equal(mUnverified.json.error, 'MISSION_NOT_VERIFIED');

    await srv.ledger.recordServerActivity({ uid: 'mission_user', activityType: 'MATCH_COMPLETED', count: 1 });
    const mOk = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:mission_user', {
      missionId: 'mission_matches'
    });
    assert.equal(mOk.status, 200);
    assert.equal(mOk.json.creditedCoins, 10);
    assert.equal(mOk.json.wallet.pointsBalance, 10);

    const mDup = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:mission_user', {
      missionId: 'mission_matches'
    });
    assert.equal(mDup.status, 409);

    const mBad = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:mission_user', {
      missionId: 'hack_10000'
    });
    assert.equal(mBad.status, 400);

    await approveAdult(srv.ledger, 'mission_user');
    const hostApply = await api(srv.baseUrl, 'POST', '/api/host/apply', 'u:mission_user', {
      agencyName: 'الوكالة الماسية'
    });
    assert.equal(hostApply.status, 200);
    assert.equal(hostApply.json.user.hostStatus, 'PENDING');
    assert.equal(hostApply.json.user.agencyName, 'الوكالة الماسية');
  } finally {
    await srv.close();
  }
});

test('WP-0 Feature Flags default to disabled (fail-closed 503 FEATURE_DISABLED) when not explicitly enabled', async () => {
  const srv = await startTestServer({ enableAllFeatures: false });
  try {
    const flagsRes = await api(srv.baseUrl, 'GET', '/api/config/flags');
    assert.equal(flagsRes.status, 200);
    assert.deepEqual(flagsRes.json, {
      payoutsEnabled: false,
      missionRewardsEnabled: false,
      paidMediaEnabled: false,
      agenciesEnabled: false
    });

    const syncRes = await api(srv.baseUrl, 'POST', '/api/auth/sync', 'u:flag_user_1', {
      displayName: 'Flag User',
      email: 'flag@example.com'
    });
    assert.equal(syncRes.status, 200);
    assert.deepEqual(syncRes.json.featureFlags, flagsRes.json);

    const missionBlocked = await api(srv.baseUrl, 'POST', '/api/wallet/mission-reward', 'u:flag_user_1', {
      missionId: 'mission_chat'
    });
    assert.equal(missionBlocked.status, 503);
    assert.equal(missionBlocked.json.error, 'FEATURE_DISABLED');

    const payoutBlocked = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:flag_user_1', {
      amountCoins: 500,
      bankAccountHolder: 'User',
      bankName: 'Bank',
      ibanOrAccount: 'SA1234'
    });
    assert.equal(payoutBlocked.status, 503);
    assert.equal(payoutBlocked.json.error, 'FEATURE_DISABLED');

    const adminPayoutBlocked = await api(srv.baseUrl, 'GET', '/api/admin/payouts', 'u:admin_1');
    assert.equal(adminPayoutBlocked.status, 503);
    assert.equal(adminPayoutBlocked.json.error, 'FEATURE_DISABLED');

    const agencyBlocked = await api(srv.baseUrl, 'GET', '/api/agency/dashboard', 'u:flag_user_1');
    assert.equal(agencyBlocked.status, 503);
    assert.equal(agencyBlocked.json.error, 'FEATURE_DISABLED');

    const hostApplyBlocked = await api(srv.baseUrl, 'POST', '/api/host/apply', 'u:flag_user_1', {
      agencyName: 'Test'
    });
    assert.equal(hostApplyBlocked.status, 503);
    assert.equal(hostApplyBlocked.json.error, 'FEATURE_DISABLED');

    const paidMediaBlocked = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:flag_user_1', {
      recipientUid: 'u_other',
      giftId: 'media_msg_1',
      giftName: 'Unlock Media',
      coinsCost: 50,
      idempotencyKey: 'unlock_media_msg_1'
    });
    assert.equal(paidMediaBlocked.status, 503);
    assert.equal(paidMediaBlocked.json.error, 'FEATURE_DISABLED');
  } finally {
    await srv.close();
  }
});

test('WP-2: Obfuscated Account ID mismatch between Google Play receipt and caller is rejected with 403', async () => {
  const customPlayBillingVerifier = async ({ expectedObfuscatedAccountId, purchaseToken, productId }) => {
    // Simulate Google Play returning an obfuscated external account id belonging to another user
    return {
      orderId: 'GPA.mismatch.123',
      purchaseState: 0,
      acknowledged: true,
      obfuscatedExternalAccountId: 'mismatched_foreign_account_hash_64_characters_long_000000000000',
      verificationSource: 'mock_mismatch_verifier',
      productId
    };
  };

  const srv = await startTestServer({ customPlayBillingVerifier });
  try {
    const res = await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:legit_user', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_mismatch_token'
    });
    assert.equal(res.status, 403);
    assert.equal(res.json.error, 'FORBIDDEN_PURCHASE_ACCOUNT_MISMATCH');
  } finally {
    await srv.close();
  }
});

test('WP-2: Voided Purchase clawback enforces non-negative balance, records debt_coins, blocks spending, and settles upon next purchase', async () => {
  const srv = await startTestServer();
  try {
    // 1. User buys 110 coins (balance = 110, debt = 0)
    const buyRes = await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:clawback_user', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_clawback_110'
    });
    assert.equal(buyRes.status, 200);
    assert.equal(buyRes.json.wallet.pointsBalance, 110);
    assert.equal(buyRes.json.wallet.debtCoins, 0);

    await approveAdult(srv.ledger, 'clawback_user');
    // 2. User spends 80 coins (balance = 30)
    const spendRes = await api(srv.baseUrl, 'POST', '/api/wallet/spend', 'u:clawback_user', {
      amount: 80,
      entryType: 'MATCH_SPEND',
      idempotencyKey: 'sp_cb_1'
    });
    assert.equal(spendRes.status, 200);
    assert.equal(spendRes.json.wallet.pointsBalance, 30);

    // 3. Google Play voids / refunds the purchase (110 coins clawed back)
    // Available 30 points deducted down to 0, remaining 80 recorded as debt_coins
    const voidRes = await api(srv.baseUrl, 'POST', '/api/admin/billing/process-voided-purchase', 'u:admin_1', {
      purchaseToken: 'pt_clawback_110',
      reason: 'GOOGLE_PLAY_CUSTOMER_CHARGEBACK'
    });
    assert.equal(voidRes.status, 200);
    assert.equal(voidRes.json.ok, true);
    assert.equal(voidRes.json.coinsClawedBack, 110);
    assert.equal(voidRes.json.deductedFromBalance, 30);
    assert.equal(voidRes.json.addedDebt, 80);
    assert.equal(voidRes.json.debtCoins, 80);

    // 4. Verify wallet state: points_balance = 0 (never negative), debt_coins = 80
    const summaryAfterClawback = await api(srv.baseUrl, 'GET', '/api/wallet/summary', 'u:clawback_user');
    assert.equal(summaryAfterClawback.json.wallet.pointsBalance, 0);
    assert.equal(summaryAfterClawback.json.wallet.debtCoins, 80);

    // 5. Attempting to spend coins is rejected with 403 ACCOUNT_HAS_DEBT
    const spendWhileInDebt = await api(srv.baseUrl, 'POST', '/api/wallet/spend', 'u:clawback_user', {
      amount: 10,
      entryType: 'MATCH_SPEND',
      idempotencyKey: 'sp_cb_2'
    });
    assert.equal(spendWhileInDebt.status, 403);
    assert.equal(spendWhileInDebt.json.error, 'ACCOUNT_HAS_DEBT');

    // 6. Attempting to send gifts is rejected with 403 ACCOUNT_HAS_DEBT
    const giftWhileInDebt = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:clawback_user', {
      recipientUid: 'u_host_test',
      giftId: 'rose',
      coinsCost: 10,
      idempotencyKey: 'gift_cb_1'
    });
    assert.equal(giftWhileInDebt.status, 403);
    assert.equal(giftWhileInDebt.json.error, 'ACCOUNT_HAS_DEBT');

    // 7. User buys another 110 coins pack to settle debt:
    // 80 settles existing debt -> debt_coins becomes 0
    // Remaining 30 credited to points_balance -> pointsBalance becomes 30
    const settleBuyRes = await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:clawback_user', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_settle_debt_001'
    });
    assert.equal(settleBuyRes.status, 200);
    assert.equal(settleBuyRes.json.wallet.debtCoins, 0);
    assert.equal(settleBuyRes.json.wallet.pointsBalance, 30);
    assert.equal(settleBuyRes.json.debtSettled, 80);

    // 8. Spending is unblocked again
    const spendAfterSettled = await api(srv.baseUrl, 'POST', '/api/wallet/spend', 'u:clawback_user', {
      amount: 20,
      entryType: 'MATCH_SPEND',
      idempotencyKey: 'sp_cb_3'
    });
    assert.equal(spendAfterSettled.status, 200);
    assert.equal(spendAfterSettled.json.wallet.pointsBalance, 10);
  } finally {
    await srv.close();
  }
});

test('WP-2: RTDN webhook processes canceled purchase and triggers clawback', async () => {
  const srv = await startTestServer();
  try {
    // 1. Initial purchase
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:rtdn_user', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_rtdn_cancel_token'
    });

    // 2. Simulate Google Play RTDN Webhook Notification
    const rtdnPayload = {
      version: '1.0',
      packageName: 'com.aistudio.ayer.live',
      eventTimeMillis: '1750000005000',
      oneTimeProductNotification: {
        version: '1.0',
        notificationType: 2, // ONE_TIME_PRODUCT_CANCELED
        purchaseToken: 'pt_rtdn_cancel_token',
        sku: 'ayer_coins_100'
      }
    };

    const webhookRes = await api(srv.baseUrl, 'POST', '/api/billing/webhook/play-rtdn', null, rtdnPayload);
    assert.equal(webhookRes.status, 200);
    assert.equal(webhookRes.json.ok, true);
    assert.equal(webhookRes.json.clawback.coinsClawedBack, 110);
    assert.equal(webhookRes.json.clawback.deductedFromBalance, 110);
    assert.equal(webhookRes.json.clawback.debtCoins, 0);

    // Wallet is down to 0
    const summary = await api(srv.baseUrl, 'GET', '/api/wallet/summary', 'u:rtdn_user');
    assert.equal(summary.json.wallet.pointsBalance, 0);
  } finally {
    await srv.close();
  }
});

test('WP-2 Exit Gate: Mocked Play API with failure injection after GET verification never consumes before ledger credit, and retry succeeds cleanly', async () => {
  const crypto = await import('node:crypto');
  const expectedHash = crypto.createHash('sha256').update('fail_inject_user').digest('hex');

  let getCount = 0;
  let ackCount = 0;
  let consumeCount = 0;
  let failNextConsume = false;

  const mockFetch = async (url, opts = {}) => {
    const method = (opts.method || 'GET').toUpperCase();
    if (method === 'GET' && url.includes('/purchases/products/')) {
      getCount += 1;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          orderId: 'GPA.mock.fail.001',
          purchaseState: 0,
          consumptionState: 0,
          acknowledgementState: 0,
          obfuscatedExternalAccountId: expectedHash
        })
      };
    }
    if (method === 'POST' && url.endsWith(':acknowledge')) {
      ackCount += 1;
      return { ok: true, status: 204, json: async () => ({}) };
    }
    if (method === 'POST' && url.endsWith(':consume')) {
      consumeCount += 1;
      if (failNextConsume) {
        return { ok: false, status: 500, json: async () => ({ error: 'TRANSIENT_PLAY_ERROR' }) };
      }
      return { ok: true, status: 204, json: async () => ({}) };
    }
    throw new Error(`Unexpected fetch URL: ${method} ${url}`);
  };

  const ledger = new MemoryLedgerStore();
  // Inject a simulated crash/failure on the very first creditPlayPurchase attempt (after Play GET verification)
  const originalCredit = ledger.creditPlayPurchase.bind(ledger);
  let injectLedgerFailure = true;
  ledger.creditPlayPurchase = async (args) => {
    if (injectLedgerFailure) {
      injectLedgerFailure = false;
      throw new Error('INJECTED_DB_FAILURE_AFTER_VERIFY');
    }
    return originalCredit(args);
  };

  const { server } = createCallServer({
    env: {
      LIVEKIT_URL: 'wss://livekit.test.invalid',
      LIVEKIT_API_KEY: 'lk_key',
      LIVEKIT_API_SECRET: 'lk_secret_12345678901234567890',
      ANDROID_PACKAGE_NAME: 'com.aistudio.ayer.live',
      GOOGLE_PLAY_ACCESS_TOKEN: 'mock_google_access_token'
    },
    ledger,
    fetchImpl: mockFetch,
    tokenVerifier: async (token) => (token === 'u:fail_inject_user' ? { uid: 'fail_inject_user', name: 'Test', email: 't@example.com' } : null)
  });

  const baseUrl = await new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve(`http://127.0.0.1:${server.address().port}`));
  });

  try {
    // 1. Attempt 1: Play GET succeeds, then ledger throws INJECTED_DB_FAILURE_AFTER_VERIFY
    const firstAttempt = await api(baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:fail_inject_user', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_fail_inject_001'
    });
    assert.equal(firstAttempt.status, 500);
    assert.equal(getCount, 1);
    // CRITICAL INVARIANT: Play purchase MUST NOT be acknowledged or consumed when ledger credit fails!
    assert.equal(ackCount, 0);
    assert.equal(consumeCount, 0);

    // 2. Retry (simulating transient :consume failure right after ledger succeeds)
    failNextConsume = true;
    const secondAttempt = await api(baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:fail_inject_user', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_fail_inject_001'
    });
    assert.equal(secondAttempt.status, 200);
    assert.equal(secondAttempt.json.creditedCoins, 110);
    assert.equal(secondAttempt.json.wallet.pointsBalance, 110);
    assert.equal(getCount, 2);
    assert.equal(ackCount, 1);
    assert.equal(consumeCount, 1);

    // 3. Third retry returns idempotent 200 OK without double-crediting
    const thirdAttempt = await api(baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:fail_inject_user', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_fail_inject_001'
    });
    assert.equal(thirdAttempt.status, 200);
    assert.equal(thirdAttempt.json.alreadyCredited, true);
    assert.equal(thirdAttempt.json.duplicate, true);
    assert.equal(thirdAttempt.json.wallet.pointsBalance, 110);
  } finally {
    await new Promise((r) => server.close(r));
  }
});

test('WP-2: Missing obfuscatedExternalAccountId in Play receipt is rejected with 403 and RTDN voidedPurchaseNotification flags gifted host', async () => {
  const customVerifier = async ({ productId }) => ({
    orderId: 'GPA.missing.account',
    purchaseState: 0,
    acknowledged: true,
    obfuscatedExternalAccountId: null,
    productId
  });
  const srvNoAcct = await startTestServer({ customPlayBillingVerifier: customVerifier });
  try {
    const missingRes = await api(srvNoAcct.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:buyer_x', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_missing_acct_001'
    });
    assert.equal(missingRes.status, 403);
    assert.equal(missingRes.json.error, 'FORBIDDEN_PURCHASE_ACCOUNT_MISSING');
  } finally {
    await srvNoAcct.close();
  }

  // Host flagging + voidedPurchaseNotification Pub/Sub base64 envelope test
  const srv = await startTestServer();
  try {
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:gifting_buyer', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_100',
      purchaseToken: 'pt_gift_refund_001'
    });

    await approveAdult(srv.ledger, 'gifting_buyer');
    // Buyer gifts 50 coins (gift_crown) to host_review_target
    const giftRes = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:gifting_buyer', {
      recipientUid: 'host_review_target',
      giftId: 'gift_crown',
      giftName: 'تاج الملوك',
      coinsCost: 50,
      idempotencyKey: 'gift_before_refund_1'
    });
    assert.equal(giftRes.status, 200);
    const hostEarningsBefore = (await srv.ledger.ensureUser({ uid: 'host_review_target' })).wallet.hostEarningsCoins;
    assert.equal(hostEarningsBefore, 25);

    // Send Google Pub/Sub base64-encoded voidedPurchaseNotification
    const rtdnInner = {
      version: '1.0',
      packageName: 'com.aistudio.ayer.live',
      eventTimeMillis: '1750000010000',
      voidedPurchaseNotification: {
        purchaseToken: 'pt_gift_refund_001',
        orderId: 'GPA.pt_gift_',
        productType: 2,
        refundType: 1
      }
    };
    const pubSubEnvelope = {
      message: {
        data: Buffer.from(JSON.stringify(rtdnInner), 'utf8').toString('base64')
      }
    };

    const webhookRes = await api(srv.baseUrl, 'POST', '/api/billing/webhook/play-rtdn', null, pubSubEnvelope);
    assert.equal(webhookRes.status, 200);
    assert.equal(webhookRes.json.ok, true);
    assert.equal(webhookRes.json.clawback.deductedFromBalance, 60);
    assert.equal(webhookRes.json.clawback.addedDebt, 50);

    // Host earnings are NOT silently clawed back, but flagged in auditLogs
    const hostEarningsAfter = (await srv.ledger.ensureUser({ uid: 'host_review_target' })).wallet.hostEarningsCoins;
    assert.equal(hostEarningsAfter, hostEarningsBefore);

    const hostFlag = srv.ledger.auditLogs.find(
      (a) => a.action === 'HOST_EARNINGS_REVIEW_FLAGGED' && a.targetId === 'host_review_target'
    );
    assert.ok(hostFlag, 'Expected HOST_EARNINGS_REVIEW_FLAGGED audit log for host');
    const debtAlert = srv.ledger.auditLogs.find(
      (a) => a.action === 'REFUND_CLAWBACK_DEBT' && a.targetId === 'gifting_buyer'
    );
    assert.ok(debtAlert, 'Expected REFUND_CLAWBACK_DEBT audit alert for buyer');
  } finally {
    await srv.close();
  }
});

test('WP-2: PostgresLedgerStore SQL parity for idempotent IAP credit, clawback, debt block, and host flagging', async () => {
  const { PostgresLedgerStore } = await import('../src/ledgerStore.js');

  // Minimal stateful mock pg Pool implementing the exact SQL statements issued by PostgresLedgerStore WP-2 paths
  const db = {
    users: new Map(),
    wallets: new Map(),
    iap: new Map(),
    ledger: [],
    audits: []
  };

  const makeExecutor = () => ({
    async query(sql, params = []) {
      const q = sql.replace(/\s+/g, ' ').trim();
      if (q === 'BEGIN' || q === 'COMMIT' || q === 'ROLLBACK') return { rowCount: 0, rows: [] };
      if (q.startsWith('INSERT INTO users')) {
        const [uid, displayName, email, referralCode, isAdmin, adminRole] = params;
        if (!db.users.has(uid)) {
          db.users.set(uid, {
            uid, display_name: displayName, email, photo_url: null, bio: '', country: '', country_code: '',
            gender: '', dob: null, age_years: 0, age_verified: false, age_verification_status: 'UNVERIFIED',
            status: 'ACTIVE', status_reason: null, underage_locked: false, underage_locked_reason: null, underage_locked_at: null,
            is_admin: Boolean(isAdmin), admin_role: adminRole || 'NONE', is_agent: false, is_host: false, host_status: 'NONE',
            agent_commission_percent: 20, host_commission_percent: 50, agency_id: '', referral_code: referralCode,
            profile_visibility: 'PUBLIC', direct_message_permission: 'EVERYONE',
            accepted_terms_at: null, accepted_privacy_at: null, deleted_at: null
          });
        }
        return { rowCount: 1, rows: [] };
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
      if (q.startsWith('INSERT INTO iap_transactions')) {
        const [purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros, purchase_state, acknowledged, obfuscated_external_account_id, verification_source] = params;
        if (db.iap.has(purchase_token)) return { rowCount: 0, rows: [] };
        const row = {
          purchase_token, order_id, uid, package_id, product_id, coins_credited, amount_micros,
          currency_code: 'USD', purchase_state, acknowledged, obfuscated_external_account_id,
          is_voided: false, voided_at: null, voided_reason: null, verification_source, verified_at: new Date().toISOString()
        };
        db.iap.set(purchase_token, row);
        return { rowCount: 1, rows: [{ ...row }] };
      }
      if (q.startsWith('SELECT * FROM iap_transactions WHERE purchase_token = $1')) {
        const row = db.iap.get(params[0]);
        return { rowCount: row ? 1 : 0, rows: row ? [{ ...row }] : [] };
      }
      if (q.startsWith('UPDATE iap_transactions SET acknowledged = TRUE')) {
        const row = db.iap.get(params[0]);
        if (!row) return { rowCount: 0, rows: [] };
        row.acknowledged = true;
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('UPDATE iap_transactions SET is_voided = TRUE')) {
        const [reason, token] = params;
        const row = db.iap.get(token);
        if (!row) return { rowCount: 0, rows: [] };
        row.is_voided = true;
        row.voided_at = new Date().toISOString();
        row.voided_reason = reason;
        return { rowCount: 1, rows: [] };
      }
      if (q.includes('SET points_balance = points_balance + $1, debt_coins = debt_coins - $2')) {
        const [addPts, settleDebt, totalAcc, uid] = params;
        const w = db.wallets.get(uid);
        w.points_balance += addPts;
        w.debt_coins -= settleDebt;
        w.total_accumulated_points += totalAcc;
        assert.ok(w.points_balance >= 0 && w.debt_coins >= 0);
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.includes('SET points_balance = points_balance - $1, debt_coins = debt_coins + $2')) {
        const [deductPts, addDebt, uid] = params;
        const w = db.wallets.get(uid);
        w.points_balance -= deductPts;
        w.debt_coins += addDebt;
        assert.ok(w.points_balance >= 0 && w.debt_coins >= 0);
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.includes('SET points_balance = points_balance - $1, total_points_spent = total_points_spent + $1')) {
        const [spendAmt, uid] = params;
        const w = db.wallets.get(uid);
        if (w.points_balance < spendAmt) return { rowCount: 0, rows: [] };
        w.points_balance -= spendAmt;
        w.total_points_spent += spendAmt;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.includes('SET platform_revenue_coins = platform_revenue_coins + $1')) {
        const [amt, uid] = params;
        const w = db.wallets.get(uid);
        w.platform_revenue_coins += amt;
        return { rowCount: 1, rows: [{ ...w }] };
      }
      if (q.startsWith('SELECT * FROM ledger_entries WHERE idempotency_key = $1')) {
        const found = db.ledger.filter((e) => e.idempotency_key === params[0]);
        return { rowCount: found.length, rows: found };
      }
      if (q.startsWith('INSERT INTO ledger_entries')) {
        if (q.includes("'PURCHASE_DEBT_SETTLEMENT'") || q.includes("'PURCHASE'") || q.includes("'REFUND_CLAWBACK'") || q.includes("'PLATFORM_REVENUE'")) {
          const [entry_uuid, uid, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          const entry_type = q.includes("'PURCHASE_DEBT_SETTLEMENT'")
            ? 'PURCHASE_DEBT_SETTLEMENT'
            : q.includes("'REFUND_CLAWBACK'")
              ? 'REFUND_CLAWBACK'
              : q.includes("'PLATFORM_REVENUE'") ? 'PLATFORM_REVENUE' : 'PURCHASE';
          if (!db.ledger.some((e) => e.idempotency_key === idempotency_key)) {
            db.ledger.push({ entry_uuid, uid, entry_type, delta, balance_after, idempotency_key, reference_id, description, metadata: JSON.parse(metadata) });
          }
        } else {
          const [entry_uuid, uid, entry_type, delta, balance_after, idempotency_key, reference_id, description, metadata] = params;
          if (!db.ledger.some((e) => e.idempotency_key === idempotency_key)) {
            db.ledger.push({ entry_uuid, uid, entry_type, delta, balance_after, idempotency_key, reference_id, description, metadata: JSON.parse(metadata) });
          }
        }
        return { rowCount: 1, rows: [] };
      }
      if (q.startsWith('INSERT INTO audit_logs')) {
        if (q.includes("'IAP_PURCHASE_CREDITED'")) {
          db.audits.push({ actor_uid: params[0], action: 'IAP_PURCHASE_CREDITED', target_id: params[1], details: JSON.parse(params[2]) });
        } else if (q.includes("'IAP_PURCHASE_VOIDED'")) {
          db.audits.push({ actor_uid: params[0], action: 'IAP_PURCHASE_VOIDED', target_id: params[1], details: JSON.parse(params[2]) });
        } else if (q.includes("'REFUND_CLAWBACK_DEBT'")) {
          db.audits.push({ actor_uid: 'system', action: 'REFUND_CLAWBACK_DEBT', target_id: params[0], details: JSON.parse(params[1]) });
        } else if (q.includes("'HOST_EARNINGS_REVIEW_FLAGGED'")) {
          db.audits.push({ actor_uid: 'system', action: 'HOST_EARNINGS_REVIEW_FLAGGED', target_id: params[0], details: JSON.parse(params[1]) });
        }
        return { rowCount: 1, rows: [] };
      }
      if (q.includes("COALESCE(metadata->>'recipientUid', reference_id) AS recipient_uid")) {
        const uid = params[0];
        const recipients = [...new Set(
          db.ledger
            .filter((e) => e.uid === uid && e.entry_type === 'GIFT_SENT')
            .map((e) => e.metadata?.recipientUid || e.reference_id)
            .filter(Boolean)
        )];
        return { rowCount: recipients.length, rows: recipients.map((r) => ({ recipient_uid: r })) };
      }
      throw new Error(`Unhandled mock PG SQL: ${q}`);
    },
    release() {}
  });

  const exec = makeExecutor();
  const mockPool = {
    query: (sql, params) => exec.query(sql, params),
    connect: async () => exec
  };

  const pgStore = new PostgresLedgerStore({ pool: mockPool });

  // 1. Purchase 110 coins (acknowledged = false initially, then markIapAcknowledged)
  const res1 = await pgStore.creditPlayPurchase({
    uid: 'pg_user_1',
    packageId: 'coins_100',
    productId: 'ayer_coins_100',
    coinsCredited: 110,
    priceUsd: 0.99,
    purchaseToken: 'pg_pt_1',
    orderId: 'GPA.pg.1',
    acknowledged: false,
    obfuscatedExternalAccountId: 'hash_pg_user_1'
  });
  assert.equal(res1.wallet.pointsBalance, 110);
  assert.equal(res1.wallet.debtCoins, 0);
  assert.equal((await pgStore.getIapTransaction('pg_pt_1')).acknowledged, false);
  assert.equal(await pgStore.markIapAcknowledged('pg_pt_1'), true);
  assert.equal((await pgStore.getIapTransaction('pg_pt_1')).acknowledged, true);

  // 2. Idempotent same-user retry and different-user rejection
  const dup = await pgStore.creditPlayPurchase({
    uid: 'pg_user_1',
    packageId: 'coins_100',
    productId: 'ayer_coins_100',
    coinsCredited: 110,
    purchaseToken: 'pg_pt_1'
  });
  assert.equal(dup.alreadyCredited, true);
  await assert.rejects(
    () => pgStore.creditPlayPurchase({
      uid: 'pg_attacker',
      packageId: 'coins_100',
      productId: 'ayer_coins_100',
      coinsCredited: 110,
      purchaseToken: 'pg_pt_1'
    }),
    (err) => err.code === 'DUPLICATE_PURCHASE_TOKEN_OTHER_USER' && err.statusCode === 409
  );

  // 3. Verify +18 then spend 70 coins (balance -> 40) + record a historical GIFT_SENT ledger row
  db.users.get('pg_user_1').age_verified = true;
  db.users.get('pg_user_1').age_verification_status = 'APPROVED';
  const spendRes = await pgStore.spendCoins({ uid: 'pg_user_1', amount: 70, idempotencyKey: 'pg_sp_1' });
  assert.equal(spendRes.wallet.pointsBalance, 40);
  db.ledger.push({
    entry_uuid: 'e_gift_1',
    uid: 'pg_user_1',
    entry_type: 'GIFT_SENT',
    reference_id: 'stream_99',
    metadata: { recipientUid: 'pg_host_1', giftId: 'rose', streamId: 'stream_99' }
  });

  // 4. Void purchase -> deducts 40, puts 70 in debtCoins, flags pg_host_1 (NOT stream_99)
  const voidRes = await pgStore.processVoidedPurchase({ purchaseToken: 'pg_pt_1', reason: 'TEST_VOID' });
  assert.equal(voidRes.deductedFromBalance, 40);
  assert.equal(voidRes.addedDebt, 70);
  assert.equal(voidRes.debtCoins, 70);
  assert.ok(db.audits.some((a) => a.action === 'HOST_EARNINGS_REVIEW_FLAGGED' && a.target_id === 'pg_host_1'));
  assert.ok(!db.audits.some((a) => a.target_id === 'stream_99'));

  // 5. Spend blocked by debt, then settled by new 110 purchase -> 40 balance & 0 debt
  await assert.rejects(
    () => pgStore.spendCoins({ uid: 'pg_user_1', amount: 10, idempotencyKey: 'pg_sp_2' }),
    (err) => err.code === 'ACCOUNT_HAS_DEBT' && err.statusCode === 403
  );
  const settle = await pgStore.creditPlayPurchase({
    uid: 'pg_user_1',
    packageId: 'coins_100',
    productId: 'ayer_coins_100',
    coinsCredited: 110,
    purchaseToken: 'pg_pt_2'
  });
  assert.equal(settle.debtSettled, 70);
  assert.equal(settle.wallet.debtCoins, 0);
  assert.equal(settle.wallet.pointsBalance, 40);
});


