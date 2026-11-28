import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AuthoritativeCallEngine, CALL_STATUS, CALL_TYPES, CallEngineError } from '../src/callEngine.js';
import { verifyHs256Jwt } from '../src/livekitJwt.js';
import { createCallServer } from '../src/server.js';

function createClock(startMs = 1_780_000_000_000) {
  let current = startMs;
  return {
    now: () => current,
    advanceSeconds: (sec) => {
      current += sec * 1000;
    }
  };
}

test('Audio & Video call initiation mints room-scoped LiveKit JWT and does NOT bill during dialing', async () => {
  const clock = createClock();
  const closedRooms = [];
  const engine = new AuthoritativeCallEngine({
    livekitUrl: 'wss://test.livekit.cloud',
    apiKey: 'lk_key_1',
    apiSecret: 'lk_secret_super_strong_test',
    roomCloser: async (roomName) => { closedRooms.push(roomName); return { ok: true }; },
    nowProvider: clock.now
  });

  const initRes = engine.initiateCall({
    callerId: 'user_a',
    callerName: 'أحمد',
    calleeId: 'user_b',
    calleeName: 'سارة',
    callType: CALL_TYPES.VIDEO
  });

  assert.equal(initRes.call.status, CALL_STATUS.DIALING);
  assert.equal(initRes.call.acceptedAt, null);
  assert.equal(initRes.call.connectedDurationSeconds, 0);
  assert.equal(initRes.call.estimatedCost, 0);

  const claims = verifyHs256Jwt(initRes.token, 'lk_secret_super_strong_test', Math.floor(clock.now() / 1000));
  assert.equal(claims.sub, 'user_a');
  assert.equal(claims.video.room, initRes.call.roomName);
  assert.equal(claims.video.roomCreate, false);
  assert.equal(claims.video.roomAdmin, false);

  // Ring for 25 seconds without answering, then cancel -> MUST remain 0 duration and 0 cost
  clock.advanceSeconds(25);
  const cancelRes = await engine.rejectCall({
    callId: initRes.call.callId,
    actorId: 'user_a',
    reason: 'CALLER_CANCELLED'
  });

  assert.equal(cancelRes.call.status, CALL_STATUS.CANCELLED);
  assert.equal(cancelRes.call.connectedDurationSeconds, 0);
  assert.equal(cancelRes.call.estimatedCost, 0);
  assert.deepEqual(closedRooms, [initRes.call.roomName]);
});

test('Server-authoritative timer starts strictly on accept, excludes RECONNECTING gap, and is idempotent on end', async () => {
  const clock = createClock();
  const closedRooms = [];
  const engine = new AuthoritativeCallEngine({
    apiKey: 'lk_key_1',
    apiSecret: 'lk_secret_super_strong_test',
    roomCloser: async (room) => { closedRooms.push(room); return { ok: true }; },
    nowProvider: clock.now
  });

  const initRes = engine.initiateCall({
    callerId: 'user_a',
    callerName: 'أحمد',
    calleeId: 'user_b',
    calleeName: 'سارة',
    callType: CALL_TYPES.AUDIO
  });

  // Ring 15s (excluded!)
  clock.advanceSeconds(15);
  const acceptRes = engine.acceptCall({ callId: initRes.call.callId, calleeId: 'user_b' });
  assert.equal(acceptRes.call.status, CALL_STATUS.ACTIVE);

  // Talk 60s
  clock.advanceSeconds(60);
  // Drop network for 20s (excluded!)
  engine.reportConnectionState({ callId: initRes.call.callId, actorId: 'user_a', state: 'RECONNECTING' });
  clock.advanceSeconds(20);
  engine.reportConnectionState({ callId: initRes.call.callId, actorId: 'user_a', state: 'CONNECTED' });
  // Talk another 60s
  clock.advanceSeconds(60);

  const end1 = await engine.endCall({ callId: initRes.call.callId, actorId: 'user_a' });
  assert.equal(end1.alreadyFinalized, false);
  assert.equal(end1.call.connectedDurationSeconds, 120);
  // 2 mins * 2 participants * 0.02 = 0.08
  assert.equal(end1.call.estimatedCost, 0.08);

  // Calling endCall a second time must be idempotent and NOT double count
  const end2 = await engine.endCall({ callId: initRes.call.callId, actorId: 'user_b' });
  assert.equal(end2.alreadyFinalized, true);
  assert.equal(closedRooms.length, 1);

  const snap = engine.getMonitoringSnapshot();
  assert.equal(snap.successfulCallsCount, 1);
  assert.equal(snap.totalAudioMinutes, 2);
});

test('Security: Unauthorized user cannot accept call, request reconnect token, or end someone else call', async () => {
  const clock = createClock();
  const engine = new AuthoritativeCallEngine({ apiKey: 'k', apiSecret: 's', nowProvider: clock.now });
  const { call } = engine.initiateCall({ callerId: 'u1', calleeId: 'u2', callType: CALL_TYPES.AUDIO });

  assert.throws(() => engine.acceptCall({ callId: call.callId, calleeId: 'u1' }), (e) => e.code === 'FORBIDDEN_PARTICIPANT');
  assert.throws(() => engine.acceptCall({ callId: call.callId, calleeId: 'intruder' }), (e) => e.code === 'FORBIDDEN_PARTICIPANT');
  assert.throws(() => engine.issueReconnectToken({ callId: call.callId, requesterId: 'intruder' }), (e) => e.code === 'FORBIDDEN_ROOM_ACCESS');
  await assert.rejects(() => engine.endCall({ callId: call.callId, actorId: 'intruder' }), (e) => e.code === 'FORBIDDEN_PARTICIPANT');
});

test('Rate limiting blocks abusive rapid call initiation and raises admin alert', () => {
  const clock = createClock();
  const engine = new AuthoritativeCallEngine({ apiKey: 'k', apiSecret: 's', nowProvider: clock.now });
  engine.updateConfig({ rateLimitCallsPerMinute: 3 });

  engine.initiateCall({ callerId: 'spammer', calleeId: 'u2' });
  engine.initiateCall({ callerId: 'spammer', calleeId: 'u3' });
  engine.initiateCall({ callerId: 'spammer', calleeId: 'u4' });

  assert.throws(
    () => engine.initiateCall({ callerId: 'spammer', calleeId: 'u5' }),
    (e) => e instanceof CallEngineError && e.code === 'RATE_LIMITED'
  );

  const alerts = engine.getMonitoringSnapshot().alerts;
  assert.ok(alerts.some((a) => a.code === 'ABNORMAL_USAGE_SPIKE'));
});

test('Budget thresholds 50%, 80%, 100% trigger alerts and hard-stop blocks non-exempt callers while exempt VIP continues', async () => {
  const clock = createClock();
  const engine = new AuthoritativeCallEngine({ apiKey: 'k', apiSecret: 's', nowProvider: clock.now });
  engine.updateConfig({
    monthlyBudgetAmount: 10.0,
    alertThreshold1Percent: 50,
    alertThreshold2Percent: 80,
    hardStopAt100Percent: true,
    videoCostPerParticipantMinute: 1.0, // 2.0 per 1-on-1 video minute
    exemptUserIds: ['vip_host']
  });

  // 3 min video call = 3 * 2 * 1.0 = 6.0 (60% -> crosses 50% threshold!)
  const c1 = engine.initiateCall({ callerId: 'u1', calleeId: 'u2', callType: CALL_TYPES.VIDEO });
  engine.acceptCall({ callId: c1.call.callId, calleeId: 'u2' });
  clock.advanceSeconds(180);
  await engine.endCall({ callId: c1.call.callId, actorId: 'u1' });

  let snap = engine.getMonitoringSnapshot();
  assert.ok(snap.alerts.some((a) => a.code === 'BUDGET_THRESHOLD_1'));

  // Another 2 min video call = 4.0 -> cumulative 10.0 (100%!)
  const c2 = engine.initiateCall({ callerId: 'u3', calleeId: 'u4', callType: CALL_TYPES.VIDEO });
  engine.acceptCall({ callId: c2.call.callId, calleeId: 'u4' });
  clock.advanceSeconds(120);
  await engine.endCall({ callId: c2.call.callId, actorId: 'u3' });

  snap = engine.getMonitoringSnapshot();
  assert.equal(snap.monthlyEstimatedCost, 10.0);
  assert.ok(snap.alerts.some((a) => a.code === 'BUDGET_100_STOP'));

  // Non-exempt user is hard-blocked
  assert.throws(
    () => engine.initiateCall({ callerId: 'u1', calleeId: 'u4', callType: CALL_TYPES.AUDIO }),
    (e) => e.code === 'BUDGET_EXCEEDED'
  );

  // Exempt VIP user can still initiate
  const vipCall = engine.initiateCall({ callerId: 'vip_host', calleeId: 'u2', callType: CALL_TYPES.AUDIO });
  assert.equal(vipCall.call.status, CALL_STATUS.DIALING);
});

test('Disabling video calls downgrades VIDEO request to AUDIO-only automatically', () => {
  const clock = createClock();
  const engine = new AuthoritativeCallEngine({ apiKey: 'k', apiSecret: 's', nowProvider: clock.now });
  engine.updateConfig({ disableVideoKeepAudioOnly: true });

  const res = engine.initiateCall({ callerId: 'u1', calleeId: 'u2', callType: CALL_TYPES.VIDEO });
  assert.equal(res.call.callType, CALL_TYPES.AUDIO);
  assert.equal(res.call.downgradedToAudio, true);
});

test('HTTP Server enforces auth, admin guard, and prevents leaking secrets', async () => {
  const { server } = createCallServer({
    env: {
      LIVEKIT_URL: 'wss://test.livekit.cloud',
      LIVEKIT_API_KEY: 'secret_api_key',
      LIVEKIT_API_SECRET: 'super_secret_hmac_key',
      ADMIN_UIDS: 'admin_1',
      ALLOW_DEV_AUTH: 'true'
    }
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const addr = server.address();
  const baseUrl = `http://127.0.0.1:${addr.port}`;

  try {
    // Unauthenticated rejected
    const unauth = await fetch(`${baseUrl}/api/calls/initiate`, { method: 'POST', body: '{}' });
    assert.equal(unauth.status, 401);

    // Normal user blocked from admin monitoring
    const nonAdmin = await fetch(`${baseUrl}/api/admin/calls/monitoring`, {
      headers: { Authorization: 'Bearer dev:regular_user' }
    });
    assert.equal(nonAdmin.status, 403);

    // Admin allowed & secrets never appear in monitoring JSON
    const adminRes = await fetch(`${baseUrl}/api/admin/calls/monitoring`, {
      headers: { Authorization: 'Bearer dev:admin_1' }
    });
    assert.equal(adminRes.status, 200);
    const bodyText = await adminRes.text();
    assert.ok(!bodyText.includes('super_secret_hmac_key'));
    assert.ok(!bodyText.includes('secret_api_key'));
  } finally {
    server.close();
  }
});

test('Completion logger records start/end times and participants in JSON store and serves Admin dashboard logs', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ayer-calls-store-'));
  const dataFile = path.join(tmpDir, 'calls-store.json');
  const sharedEnv = {
    LIVEKIT_URL: 'wss://test.livekit.cloud',
    LIVEKIT_API_KEY: 'lk_test_key',
    LIVEKIT_API_SECRET: 'lk_test_secret_should_not_persist',
    ADMIN_UIDS: 'admin_1',
    ALLOW_DEV_AUTH: 'true',
    DATA_FILE: dataFile
  };

  const { server, getLedger } = createCallServer({ env: sharedEnv });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  const ledger = await getLedger();
  await ledger.submitAgeVerification({
    uid: 'user_caller',
    displayName: 'سامر',
    email: 'caller@ayer.test',
    dob: '1995-01-01',
    acceptedTermsAt: 1000,
    acceptedPrivacyAt: 1000
  });
  await ledger.reviewAgeVerification({ reviewerUid: 'admin_1', targetUid: 'user_caller', decision: 'APPROVED' });

  let completedCallId = '';
  try {
    // 1. Caller initiates a video call
    const initRes = await fetch(`${baseUrl}/api/calls/initiate`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer dev:user_caller',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        callerName: 'سامر',
        calleeId: 'user_callee',
        calleeName: 'سارة',
        callType: 'VIDEO'
      })
    });
    assert.equal(initRes.status, 201);
    const initBody = await initRes.json();
    completedCallId = initBody.call.callId;

    // Not in completed callLogs while still dialing
    assert.equal(fs.existsSync(dataFile), true);
    const midStore = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
    assert.equal(midStore.callLogs.length, 0);

    // 2. Callee accepts the call
    const acceptRes = await fetch(`${baseUrl}/api/calls/${completedCallId}/accept`, {
      method: 'POST',
      headers: { Authorization: 'Bearer dev:user_callee' }
    });
    assert.equal(acceptRes.status, 200);

    // 3. Call ends
    const endRes = await fetch(`${baseUrl}/api/calls/${completedCallId}/end`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer dev:user_caller',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ reason: 'USER_HANGUP' })
    });
    assert.equal(endRes.status, 200);

    // Duplicate end must remain idempotent with 1 log entry
    const endDupRes = await fetch(`${baseUrl}/api/calls/${completedCallId}/end`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer dev:user_callee',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ reason: 'DUPLICATE_HANGUP' })
    });
    assert.equal(endDupRes.status, 200);

    // Verify JSON store file recorded start/end times + participants and zero secrets
    const rawDisk = fs.readFileSync(dataFile, 'utf8');
    assert.ok(!rawDisk.includes('lk_test_secret_should_not_persist'));
    const store = JSON.parse(rawDisk);
    assert.equal(store.callLogs.length, 1);

    const logEntry = store.callLogs[0];
    assert.equal(logEntry.callId, completedCallId);
    assert.equal(logEntry.callType, 'VIDEO');
    assert.equal(logEntry.status, 'ENDED');
    assert.equal(typeof logEntry.initiatedAt, 'number');
    assert.equal(typeof logEntry.acceptedAt, 'number');
    assert.equal(typeof logEntry.startedAt, 'number');
    assert.equal(typeof logEntry.endedAt, 'number');
    assert.ok(logEntry.endedAt >= logEntry.startedAt);
    assert.ok(logEntry.startedAtIso.endsWith('Z'));
    assert.ok(logEntry.endedAtIso.endsWith('Z'));
    assert.deepEqual(logEntry.participants, [
      { userId: 'user_caller', name: 'سامر', role: 'caller' },
      { userId: 'user_callee', name: 'سارة', role: 'callee' }
    ]);

    // Verify Admin logs endpoint enforcement & filtering
    const nonAdminLogs = await fetch(`${baseUrl}/api/admin/calls/logs`, {
      headers: { Authorization: 'Bearer dev:user_caller' }
    });
    assert.equal(nonAdminLogs.status, 403);

    const adminLogsRes = await fetch(`${baseUrl}/api/admin/calls/logs?callType=VIDEO&userId=user_callee`, {
      headers: { Authorization: 'Bearer dev:admin_1' }
    });
    assert.equal(adminLogsRes.status, 200);
    const adminLogsBody = await adminLogsRes.json();
    assert.equal(adminLogsBody.total, 1);
    assert.equal(adminLogsBody.logs[0].callId, completedCallId);
  } finally {
    server.close();
  }

  // Verify JSON store survives server restart & serves monitoring + logs
  const { server: restartedServer } = createCallServer({ env: sharedEnv });
  await new Promise((resolve) => restartedServer.listen(0, '127.0.0.1', resolve));
  const restartedUrl = `http://127.0.0.1:${restartedServer.address().port}`;
  try {
    const monRes = await fetch(`${restartedUrl}/api/admin/calls/monitoring`, {
      headers: { Authorization: 'Bearer dev:admin_1' }
    });
    assert.equal(monRes.status, 200);
    const monBody = await monRes.json();
    assert.equal(monBody.completedCallsCount, 1);
    assert.equal(monBody.callLogs.length, 1);
    assert.equal(monBody.callLogs[0].callId, completedCallId);
    assert.equal(monBody.callLogs[0].participants.length, 2);
  } finally {
    restartedServer.close();
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
