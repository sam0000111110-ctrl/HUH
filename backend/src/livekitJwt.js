import crypto from 'node:crypto';

function base64UrlEncode(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(JSON.stringify(input), 'utf8');
  return buf.toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function base64UrlDecode(str) {
  const padded = str.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (str.length % 4)) % 4);
  return Buffer.from(padded, 'base64').toString('utf8');
}

/**
 * Generates an HS256 JWT compatible with LiveKit Server / LiveKit Cloud.
 * Grants are strictly scoped to a single roomName and single participant identity.
 */
export function signHs256Jwt(payload, apiSecret) {
  if (!apiSecret) {
    throw new Error('LIVEKIT_API_SECRET is required to sign JWT tokens');
  }
  const header = { alg: 'HS256', typ: 'JWT' };
  const encodedHeader = base64UrlEncode(header);
  const encodedPayload = base64UrlEncode(payload);
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const signature = crypto
    .createHmac('sha256', apiSecret)
    .update(signingInput)
    .digest('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `${signingInput}.${signature}`;
}

export function verifyHs256Jwt(token, apiSecret, nowSec = Math.floor(Date.now() / 1000)) {
  if (!token || typeof token !== 'string') {
    throw new Error('Missing JWT token');
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Malformed JWT token');
  }
  const [encodedHeader, encodedPayload, providedSig] = parts;
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const expectedSig = crypto
    .createHmac('sha256', apiSecret)
    .update(signingInput)
    .digest('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');

  const providedBuf = Buffer.from(providedSig, 'utf8');
  const expectedBuf = Buffer.from(expectedSig, 'utf8');
  if (providedBuf.length !== expectedBuf.length || !crypto.timingSafeEqual(providedBuf, expectedBuf)) {
    throw new Error('Invalid JWT signature');
  }

  const payload = JSON.parse(base64UrlDecode(encodedPayload));
  if (typeof payload.nbf === 'number' && nowSec < payload.nbf) {
    throw new Error('Token not yet active');
  }
  if (typeof payload.exp === 'number' && nowSec >= payload.exp) {
    throw new Error('Token expired');
  }
  return payload;
}

/**
 * Mints a short-lived, strictly scoped LiveKit participant token for a 1-on-1 call.
 */
export function createParticipantToken({
  apiKey,
  apiSecret,
  identity,
  name,
  roomName,
  ttlSeconds = 300,
  canPublish = true,
  canSubscribe = true,
  canPublishData = true,
  canPublishSources = null,
  metadata = {},
  nowSec = Math.floor(Date.now() / 1000)
}) {
  if (!apiKey || !apiSecret) {
    throw new Error('LiveKit credentials missing on server');
  }
  if (!identity || !roomName) {
    throw new Error('Participant identity and roomName are required');
  }

  const videoGrant = {
    roomJoin: true,
    room: roomName,
    roomCreate: false,
    roomList: false,
    roomAdmin: false,
    canPublish,
    canSubscribe,
    canPublishData
  };
  if (Array.isArray(canPublishSources) && canPublishSources.length > 0) {
    videoGrant.canPublishSources = canPublishSources;
  }

  const payload = {
    iss: apiKey,
    sub: String(identity),
    jti: `${identity}_${crypto.randomUUID()}`,
    nbf: nowSec - 5,
    exp: nowSec + ttlSeconds,
    name: String(name || identity),
    metadata: JSON.stringify(metadata),
    video: videoGrant
  };

  return {
    token: signHs256Jwt(payload, apiSecret),
    expiresAtMs: (nowSec + ttlSeconds) * 1000
  };
}

export function createRoomAdminToken({
  apiKey,
  apiSecret,
  roomName,
  ttlSeconds = 60,
  nowSec = Math.floor(Date.now() / 1000)
}) {
  const payload = {
    iss: apiKey,
    nbf: nowSec - 5,
    exp: nowSec + ttlSeconds,
    video: {
      roomAdmin: true,
      roomCreate: true,
      room: roomName
    }
  };
  return signHs256Jwt(payload, apiSecret);
}

export function toLiveKitHttpUrl(livekitUrl) {
  if (!livekitUrl) return '';
  return livekitUrl
    .replace(/^wss:\/\//i, 'https://')
    .replace(/^ws:\/\//i, 'http://')
    .replace(/\/+$/, '');
}

/**
 * Calls LiveKit RoomService Twirp API to forcefully close an ended/orphaned room
 * so no empty room or unauthorized participant can linger.
 */
export class LiveKitRoomClient {
  constructor({ livekitUrl, apiKey, apiSecret, fetchImpl = globalThis.fetch } = {}) {
    this.livekitUrl = livekitUrl || '';
    this.httpUrl = toLiveKitHttpUrl(this.livekitUrl);
    this.apiKey = apiKey || '';
    this.apiSecret = apiSecret || '';
    this.fetchImpl = fetchImpl;
  }

  isConfigured() {
    return Boolean(this.httpUrl && this.apiKey && this.apiSecret && this.fetchImpl);
  }

  async deleteRoom(roomName) {
    if (!this.isConfigured() || !roomName) {
      return { ok: false, skipped: true, reason: 'LiveKit RoomClient not configured' };
    }
    const adminToken = createRoomAdminToken({
      apiKey: this.apiKey,
      apiSecret: this.apiSecret,
      roomName
    });
    const res = await this.fetchImpl(`${this.httpUrl}/twirp/livekit.RoomService/DeleteRoom`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({ room: roomName })
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      return { ok: false, status: res.status, error: text };
    }
    return { ok: true };
  }

  async removeParticipant(roomName, identity) {
    if (!this.isConfigured() || !roomName || !identity) {
      return { ok: false, skipped: true };
    }
    const adminToken = createRoomAdminToken({
      apiKey: this.apiKey,
      apiSecret: this.apiSecret,
      roomName
    });
    const res = await this.fetchImpl(`${this.httpUrl}/twirp/livekit.RoomService/RemoveParticipant`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({ room: roomName, identity })
    });
    return { ok: res.ok, status: res.status };
  }
}
