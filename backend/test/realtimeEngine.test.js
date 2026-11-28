import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createCallServer } from '../src/server.js';
import { verifyHs256Jwt } from '../src/livekitJwt.js';

function startTestServer({ customTokens = {}, closedRooms = [] } = {}) {
  const env = {
    LIVEKIT_URL: 'wss://test.livekit.local',
    LIVEKIT_API_KEY: 'lk_test_key',
    LIVEKIT_API_SECRET: 'lk_test_secret_1234567890'
  };
  const { server, realtimeEngine, getLedger } = createCallServer({
    env,
    tokenVerifier: async (token) => customTokens[token] || null
  });
  realtimeEngine.roomCloser = async (roomName) => {
    closedRooms.push(roomName);
    return { ok: true };
  };

  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address();
      resolve({
        server,
        env,
        baseUrl: `http://127.0.0.1:${addr.port}`,
        getLedger,
        close: () => new Promise((r) => server.close(r))
      });
    });
  });
}

async function apiFetch(baseUrl, path, { method = 'GET', token = null, body = null } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, json };
}

async function approveAdult(ledger, uid) {
  await ledger.submitAgeVerification({
    uid,
    displayName: uid,
    email: `${uid}@huh.app`,
    dob: '1995-03-15',
    acceptedTermsAt: 1000,
    acceptedPrivacyAt: 1000
  });
  await ledger.reviewAgeVerification({ reviewerUid: 'admin_1', targetUid: uid, decision: 'APPROVED' });
}

test('Phase D: Live broadcast lifecycle, publisher/subscriber JWTs, atomic gift, and host/webhook close', async () => {
  const closedRooms = [];
  const srv = await startTestServer({
    closedRooms,
    customTokens: {
      admin_tok: { uid: 'admin_1', name: 'Admin', email: 'a@huh.app', isAdmin: true },
      host_tok: { uid: 'host_1', name: 'Host Reem', email: 'h@huh.app', isAdmin: false },
      viewer_tok: { uid: 'viewer_1', name: 'Viewer Ali', email: 'v@huh.app', isAdmin: false }
    }
  });

  try {
    const ledger = await srv.getLedger();
    await ledger.ensureUser({ uid: 'host_1', displayName: 'Host Reem', email: 'h@huh.app' });
    await ledger.ensureUser({ uid: 'viewer_1', displayName: 'Viewer Ali', email: 'v@huh.app' });
    await ledger.adminSetUserRolesAndCommission({
      reviewerUid: 'admin_1',
      targetUid: 'host_1',
      isHost: true,
      hostCommissionPercent: 60
    });
    await ledger.creditPlayPurchase({
      uid: 'viewer_1',
      packageId: 'pkg_100',
      productId: 'ayer_coins_100',
      coinsCredited: 100,
      priceUsd: 0.99,
      purchaseToken: 'ptok_viewer_1'
    });
    await approveAdult(ledger, 'viewer_1');

    // 1. Unverified host blocked from starting broadcast (403)
    const unverifiedRes = await apiFetch(srv.baseUrl, '/api/streams', {
      method: 'POST',
      token: 'host_tok',
      body: { title: 'بث تجربة', category: 'تعارف وسوالف' }
    });
    assert.equal(unverifiedRes.status, 403);
    assert.equal(unverifiedRes.json.error, 'AGE_VERIFICATION_REQUIRED');

    // Approve +18 for host
    await ledger.submitAgeVerification({
      uid: 'host_1',
      displayName: 'Host Reem',
      email: 'h@huh.app',
      dob: '1999-03-15',
      ageYears: 27,
      acceptedTermsAt: Date.now(),
      acceptedPrivacyAt: Date.now()
    });
    await ledger.reviewAgeVerification({ reviewerUid: 'admin_1', targetUid: 'host_1', decision: 'APPROVED' });

    // 2. Host starts broadcast -> gets PUBLISHER LiveKit token
    const startRes = await apiFetch(srv.baseUrl, '/api/streams', {
      method: 'POST',
      token: 'host_tok',
      body: { title: 'سهرة موسيقية مباشرة', category: 'موسيقى', hostCountry: 'الإمارات', hostCountryCode: 'AE' }
    });
    assert.equal(startRes.status, 201);
    assert.equal(startRes.json.role, 'PUBLISHER');
    const pubClaims = verifyHs256Jwt(startRes.json.token, srv.env.LIVEKIT_API_SECRET);
    assert.equal(pubClaims.video.canPublish, true);
    assert.equal(pubClaims.sub, 'host_1');

    const streamId = startRes.json.stream.id;

    // 3. Viewer lists streams & joins -> gets SUBSCRIBER token (canPublish: false)
    const listRes = await apiFetch(srv.baseUrl, '/api/streams', { token: 'viewer_tok' });
    assert.equal(listRes.status, 200);
    assert.equal(listRes.json.streams.length, 1);
    assert.equal(listRes.json.streams[0].id, streamId);

    const joinRes = await apiFetch(srv.baseUrl, `/api/streams/${streamId}/join`, {
      method: 'POST',
      token: 'viewer_tok'
    });
    assert.equal(joinRes.status, 200);
    assert.equal(joinRes.json.role, 'SUBSCRIBER');
    const subClaims = verifyHs256Jwt(joinRes.json.token, srv.env.LIVEKIT_API_SECRET);
    assert.equal(subClaims.video.canPublish, false);
    assert.equal(subClaims.video.canSubscribe, true);
    assert.equal(joinRes.json.stream.viewersCount, 2);

    // 4. Viewer sends atomic gift to stream -> updates diamondsReceived & host earnings
    const giftRes = await apiFetch(srv.baseUrl, '/api/wallet/gift', {
      method: 'POST',
      token: 'viewer_tok',
      body: {
        streamId,
        giftId: 'gift_crown',
        giftName: 'تاج الماس',
        coinsCost: 50,
        idempotencyKey: 'gift_stream_1'
      }
    });
    assert.equal(giftRes.status, 200);
    assert.equal(giftRes.json.senderWallet.pointsBalance, 50);
    assert.equal(giftRes.json.recipientEarningsAdded, 30); // 60% of 50

    const afterGiftList = await apiFetch(srv.baseUrl, '/api/streams', { token: 'viewer_tok' });
    assert.equal(afterGiftList.json.streams[0].diamondsReceived, 50);

    // 5. Non-host viewer cannot end the stream
    const forbidEnd = await apiFetch(srv.baseUrl, `/api/streams/${streamId}/end`, {
      method: 'POST',
      token: 'viewer_tok'
    });
    assert.equal(forbidEnd.status, 403);

    // 6. Block hides stream from viewer
    await apiFetch(srv.baseUrl, '/api/blocks', {
      method: 'POST',
      token: 'viewer_tok',
      body: { blockedUid: 'host_1' }
    });
    const blockedList = await apiFetch(srv.baseUrl, '/api/streams', { token: 'viewer_tok' });
    assert.equal(blockedList.json.streams.length, 0);

    // 7. Host ends stream -> closes LiveKit room
    const endRes = await apiFetch(srv.baseUrl, `/api/streams/${streamId}/end`, {
      method: 'POST',
      token: 'host_tok'
    });
    assert.equal(endRes.status, 200);
    assert.equal(endRes.json.stream.status, 'ENDED');
    assert.ok(closedRooms.includes(startRes.json.stream.roomName));
  } finally {
    await srv.close();
  }
});

test('Phase D: Matchmaking queue pairs compatible users atomically, rejects insufficient balance, and respects blocks', async () => {
  const srv = await startTestServer({
    customTokens: {
      u1_tok: { uid: 'user_1', name: 'Noor', email: 'n@huh.app', isAdmin: false },
      u2_tok: { uid: 'user_2', name: 'Zaid', email: 'z@huh.app', isAdmin: false },
      u3_tok: { uid: 'user_3', name: 'BrokeUser', email: 'b@huh.app', isAdmin: false }
    }
  });

  try {
    const ledger = await srv.getLedger();
    await ledger.creditPlayPurchase({
      uid: 'user_1',
      packageId: 'pkg_100',
      productId: 'ayer_coins_100',
      coinsCredited: 20,
      priceUsd: 0.99,
      purchaseToken: 'ptok_u1'
    });
    await ledger.creditPlayPurchase({
      uid: 'user_2',
      packageId: 'pkg_100',
      productId: 'ayer_coins_100',
      coinsCredited: 20,
      priceUsd: 0.99,
      purchaseToken: 'ptok_u2'
    });
    await ledger.ensureUser({ uid: 'user_3', displayName: 'BrokeUser', email: 'b@huh.app' });
    await approveAdult(ledger, 'user_1');
    await approveAdult(ledger, 'user_2');
    await approveAdult(ledger, 'user_3');

    // Insufficient balance rejected (402 from holdCoins)
    const brokeRes = await apiFetch(srv.baseUrl, '/api/match/join', {
      method: 'POST',
      token: 'u3_tok',
      body: { chatType: 'VIDEO' }
    });
    assert.equal(brokeRes.status, 402);
    assert.equal(brokeRes.json.error, 'INSUFFICIENT_COINS');

    // User 1 queues -> WAITING and coins are held (not yet spent)
    const q1 = await apiFetch(srv.baseUrl, '/api/match/join', {
      method: 'POST',
      token: 'u1_tok',
      body: { chatType: 'VIDEO', countryCode: 'SA', userGender: 'FEMALE', genderFilter: 'ANY', countryFilter: 'ANY' }
    });
    assert.equal(q1.status, 200);
    assert.equal(q1.json.status, 'WAITING');
    assert.equal(q1.json.wallet.pointsBalance, 17);
    assert.equal(q1.json.wallet.heldCoins, 3);

    // User 2 joins compatible queue -> MATCHED immediately + both holds captured atomically
    const q2 = await apiFetch(srv.baseUrl, '/api/match/join', {
      method: 'POST',
      token: 'u2_tok',
      body: { chatType: 'VIDEO', countryCode: 'AE', userGender: 'MALE', genderFilter: 'FEMALE', countryFilter: 'SA' }
    });
    assert.equal(q2.status, 200);
    assert.equal(q2.json.status, 'MATCHED');
    assert.equal(q2.json.partner.uid, 'user_1');
    assert.equal(q2.json.costCoins, 7); // 3 video + 2 gender + 2 country
    assert.equal(q2.json.wallet.pointsBalance, 13);
    assert.equal(q2.json.wallet.heldCoins, 0);

    // User 1 polls -> receives MATCHED + heldCoins released/captured (0 held, 17 balance)
    const p1 = await apiFetch(srv.baseUrl, '/api/match/poll', { token: 'u1_tok' });
    assert.equal(p1.status, 200);
    assert.equal(p1.json.status, 'MATCHED');
    assert.equal(p1.json.partner.uid, 'user_2');
    assert.equal(p1.json.wallet.pointsBalance, 17);
    assert.equal(p1.json.wallet.heldCoins, 0);

    // User 1 leaves match -> room closed
    const leave1 = await apiFetch(srv.baseUrl, '/api/match/leave', {
      method: 'POST',
      token: 'u1_tok',
      body: { matchId: p1.json.matchId }
    });
    assert.equal(leave1.status, 200);
    assert.equal(leave1.json.status, 'IDLE');
  } finally {
    await srv.close();
  }
});

test('WP-1 A8: Matchmaking releases held coins when leaving waiting queue cleanly', async () => {
  const srv = await startTestServer({
    customTokens: {
      u1_tok: { uid: 'hold_user_1', name: 'H1', email: 'h1@huh.app', isAdmin: false }
    }
  });
  try {
    const ledger = await srv.getLedger();
    await ledger.creditPlayPurchase({
      uid: 'hold_user_1',
      packageId: 'pkg_100',
      productId: 'ayer_coins_100',
      coinsCredited: 20,
      priceUsd: 0.99,
      purchaseToken: 'ptok_hold_u1'
    });
    await approveAdult(ledger, 'hold_user_1');

    const joinRes = await apiFetch(srv.baseUrl, '/api/match/join', {
      method: 'POST',
      token: 'u1_tok',
      body: { chatType: 'VIDEO', genderFilter: 'FEMALE' } // 5 coins
    });
    assert.equal(joinRes.status, 200);
    assert.equal(joinRes.json.status, 'WAITING');
    assert.equal(joinRes.json.wallet.pointsBalance, 15);
    assert.equal(joinRes.json.wallet.heldCoins, 5);

    const leaveRes = await apiFetch(srv.baseUrl, '/api/match/leave', {
      method: 'POST',
      token: 'u1_tok',
      body: {}
    });
    assert.equal(leaveRes.status, 200);
    assert.equal(leaveRes.json.status, 'IDLE');
    assert.equal(leaveRes.json.wallet.pointsBalance, 20);
    assert.equal(leaveRes.json.wallet.heldCoins, 0);
  } finally {
    await srv.close();
  }
});
