import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { AuthoritativeCallEngine, CallEngineError } from './callEngine.js';
import { LiveKitRoomClient, verifyHs256Jwt } from './livekitJwt.js';
import { authenticateRequest } from './authMiddleware.js';

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
  tokenVerifier = null
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

  const save = () => persistState(dataFile, callEngine);

  const server = http.createServer(async (req, res) => {
    try {
      const parsedUrl = new URL(req.url || '/', 'http://localhost');
      const pathname = parsedUrl.pathname;
      const method = req.method || 'GET';

      if (method === 'GET' && pathname === '/health') {
        return sendJson(res, 200, {
          status: 'ok',
          provider: 'livekit',
          livekitConfigured: Boolean(livekitUrl && apiKey && apiSecret)
        });
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
        }
        return sendJson(res, 200, { received: true });
      }

      // Authenticate caller/admin
      const user = await authenticateRequest(req, env, tokenVerifier);
      if (!user) {
        return sendJson(res, 401, { error: 'UNAUTHORIZED', message: 'مطلوب تسجيل دخول صالح' });
      }

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

      // Admin Monitoring & Governance endpoints
      if (pathname.startsWith('/api/admin/calls')) {
        if (!user.isAdmin) {
          return sendJson(res, 403, { error: 'FORBIDDEN_ADMIN', message: 'صلاحية الإدارة مطلوبة' });
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

  return { server, callEngine, persistNow: save };
}

// Start server when executed directly
if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT || 8080);
  const envWithDefaultStore = {
    ...process.env,
    DATA_FILE: process.env.DATA_FILE || './data/calls-store.json'
  };
  const { server, callEngine, persistNow } = createCallServer({ env: envWithDefaultStore });
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
