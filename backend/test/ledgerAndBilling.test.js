import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createCallServer } from '../src/server.js';
import { MemoryLedgerStore } from '../src/ledgerStore.js';

function startTestServer({ nowMsRef = { value: 1_750_000_000_000 } } = {}) {
  const ledger = new MemoryLedgerStore({ nowProvider: () => nowMsRef.value });
  const env = {
    LIVEKIT_URL: 'wss://livekit.test.invalid',
    LIVEKIT_API_KEY: 'lk_key',
    LIVEKIT_API_SECRET: 'lk_secret_12345678901234567890',
    ANDROID_PACKAGE_NAME: 'com.aistudio.ayer.live',
    ADMIN_UIDS: 'admin_1'
  };
  const playBillingVerifier = async ({ productId, purchaseToken }) => ({
    orderId: `GPA.${purchaseToken.slice(0, 8)}`,
    purchaseState: 0,
    acknowledged: true,
    verificationSource: 'mock_google_play_v3_verifier',
    productId
  });
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

test('Google Play purchaseToken prevents replay under concurrent requests and enforces server catalog', async () => {
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
    const conflicts = results.filter((r) => r.status === 409);

    assert.equal(ok.length, 1);
    assert.equal(ok[0].json.creditedCoins, 110);
    assert.equal(conflicts.length, 7);

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

    // Buyer purchases 1400 coins (pkg_1200) and sends 1200-coin gift to host_1
    await api(srv.baseUrl, 'POST', '/api/billing/verify-play-purchase', 'u:sender_1', {
      packageName: 'com.aistudio.ayer.live',
      productId: 'ayer_coins_1200',
      purchaseToken: 'pt_sender_1200'
    });

    const gift = await api(srv.baseUrl, 'POST', '/api/wallet/gift', 'u:sender_1', {
      recipientUid: 'host_1',
      giftId: 'gift_crown',
      giftName: 'تاج ملكي',
      coinsCost: 1200,
      idempotencyKey: 'gift_tx_001'
    });
    assert.equal(gift.status, 200);
    assert.equal(gift.json.senderWallet.pointsBalance, 200);
    assert.equal(gift.json.recipientEarningsAdded, 600);
    assert.equal(gift.json.agentCommissionAdded, 240);

    // host_1 must verify +18 age before payout
    const underage = await api(srv.baseUrl, 'POST', '/api/age-verification/submit', 'u:host_1', {
      dob: '2012-01-01',
      ageYears: 14,
      acceptedTermsAt: 1000,
      acceptedPrivacyAt: 1000
    });
    assert.equal(underage.status, 403);

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

    // host_1 resubmits and admin marks PAID
    const p2 = await api(srv.baseUrl, 'POST', '/api/payouts/request', 'u:host_1', {
      amountCoins: 500,
      bankAccountHolder: 'هدى أحمد',
      bankName: 'بنك مصر',
      ibanOrAccount: 'EG999988887777'
    });
    assert.equal(p2.status, 201);

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
  } finally {
    await srv.close();
  }
});
