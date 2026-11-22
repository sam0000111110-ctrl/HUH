import crypto from 'node:crypto';

const GOOGLE_CERTS_URL = 'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
let cachedCerts = null;
let cachedCertsExpiresAt = 0;

function decodeBase64UrlJson(segment) {
  const padded = segment.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (segment.length % 4)) % 4);
  return JSON.parse(Buffer.from(padded, 'base64').toString('utf8'));
}

function decodeBase64UrlBuffer(segment) {
  const padded = segment.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (segment.length % 4)) % 4);
  return Buffer.from(padded, 'base64');
}

async function fetchFirebaseCerts(fetchImpl = globalThis.fetch) {
  const now = Date.now();
  if (cachedCerts && now < cachedCertsExpiresAt) {
    return cachedCerts;
  }
  const res = await fetchImpl(GOOGLE_CERTS_URL);
  if (!res.ok) {
    throw new Error(`Failed to fetch Firebase public keys: ${res.status}`);
  }
  const cacheControl = res.headers.get('cache-control') || '';
  const maxAgeMatch = cacheControl.match(/max-age=(\d+)/i);
  const maxAgeSec = maxAgeMatch ? Number(maxAgeMatch[1]) : 3600;
  cachedCerts = await res.json();
  cachedCertsExpiresAt = now + maxAgeSec * 1000;
  return cachedCerts;
}

/**
 * Verifies a Firebase Auth RS256 ID Token against Google's X.509 certificates.
 */
export async function verifyFirebaseIdToken(idToken, { projectId, fetchImpl = globalThis.fetch } = {}) {
  if (!idToken || typeof idToken !== 'string') {
    throw new Error('Missing Firebase ID token');
  }
  const parts = idToken.split('.');
  if (parts.length !== 3) {
    throw new Error('Malformed Firebase ID token');
  }
  const [headerB64, payloadB64, sigB64] = parts;
  const header = decodeBase64UrlJson(headerB64);
  const payload = decodeBase64UrlJson(payloadB64);

  if (header.alg !== 'RS256' || !header.kid) {
    throw new Error('Unsupported Firebase token algorithm or missing kid');
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (!payload.sub || typeof payload.sub !== 'string') {
    throw new Error('Missing subject uid');
  }
  if (typeof payload.exp !== 'number' || payload.exp <= nowSec) {
    throw new Error('Firebase token expired');
  }
  if (typeof payload.iat !== 'number' || payload.iat > nowSec + 300) {
    throw new Error('Invalid Firebase token issued-at');
  }
  if (projectId) {
    if (payload.aud !== projectId || payload.iss !== `https://securetoken.google.com/${projectId}`) {
      throw new Error('Firebase token audience/issuer mismatch');
    }
  }

  const certs = await fetchFirebaseCerts(fetchImpl);
  const certPem = certs[header.kid];
  if (!certPem) {
    throw new Error('Unknown Firebase signing key ID');
  }

  const verifier = crypto.createVerify('RSA-SHA256');
  verifier.update(`${headerB64}.${payloadB64}`);
  verifier.end();

  const signature = decodeBase64UrlBuffer(sigB64);
  if (!verifier.verify(certPem, signature)) {
    throw new Error('Invalid Firebase token signature');
  }

  return {
    uid: payload.sub,
    name: payload.name || payload.email || payload.sub,
    email: payload.email || null,
    isAdmin: payload.admin === true || payload.role === 'admin'
  };
}

/**
 * Resolves authenticated user from Authorization Bearer header.
 * Never trusts client-supplied uid headers directly in production.
 */
export async function authenticateRequest(req, env = process.env, customVerifier = null) {
  const authHeader = req.headers['authorization'] || req.headers['Authorization'] || '';
  const match = String(authHeader).match(/^Bearer\s+(.+)$/i);
  const adminKeyHeader = req.headers['x-admin-key'] || '';
  const configuredAdminUids = new Set(
    String(env.ADMIN_UIDS || '').split(',').map((s) => s.trim()).filter(Boolean)
  );

  // Admin API key fast-path strictly for server/dashboard admin routes
  if (env.ADMIN_API_KEY && adminKeyHeader && adminKeyHeader === env.ADMIN_API_KEY) {
    return {
      uid: 'admin_console',
      name: 'Admin Console',
      isAdmin: true
    };
  }

  if (!match) {
    return null;
  }

  const token = match[1].trim();
  if (customVerifier) {
    const user = await customVerifier(token);
    if (user && configuredAdminUids.has(user.uid)) {
      user.isAdmin = true;
    }
    return user;
  }

  if (env.ALLOW_DEV_AUTH === 'true' && token.startsWith('dev:')) {
    const uid = token.slice(4).trim();
    if (!uid) return null;
    return {
      uid,
      name: uid,
      isAdmin: configuredAdminUids.has(uid)
    };
  }

  if (!env.FIREBASE_PROJECT_ID) {
    throw new Error('FIREBASE_PROJECT_ID is not configured on backend');
  }

  const user = await verifyFirebaseIdToken(token, { projectId: env.FIREBASE_PROJECT_ID });
  if (configuredAdminUids.has(user.uid)) {
    user.isAdmin = true;
  }
  return user;
}
