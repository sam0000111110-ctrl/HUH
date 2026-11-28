import crypto from 'node:crypto';
import { CallEngineError } from './callEngine.js';

const GOOGLE_CERTS_URL = 'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
let cachedCerts = null;
let cachedCertsExpiresAt = 0;

export const ADMIN_ROLES = Object.freeze({
  NONE: 'NONE',
  SUPER_ADMIN: 'SUPER_ADMIN',
  FINANCE_ADMIN: 'FINANCE_ADMIN',
  MODERATION_ADMIN: 'MODERATION_ADMIN',
  AGENCY_MANAGER: 'AGENCY_MANAGER'
});

const VALID_ADMIN_ROLE_SET = new Set(Object.values(ADMIN_ROLES));
const ASSIGNABLE_ADMIN_ROLES = new Set([
  ADMIN_ROLES.SUPER_ADMIN,
  ADMIN_ROLES.FINANCE_ADMIN,
  ADMIN_ROLES.MODERATION_ADMIN,
  ADMIN_ROLES.AGENCY_MANAGER
]);

export const ADMIN_PERMISSIONS = Object.freeze({
  AGE_REVIEWS: 'AGE_REVIEWS',
  REPORTS_MODERATION: 'REPORTS_MODERATION',
  USER_STATUS: 'USER_STATUS',
  MODERATION_SAMPLES: 'MODERATION_SAMPLES',
  STREAM_TERMINATE: 'STREAM_TERMINATE',
  PAYOUTS_REVIEW: 'PAYOUTS_REVIEW',
  ECONOMY_CONFIG_READ: 'ECONOMY_CONFIG_READ',
  ECONOMY_CONFIG_WRITE: 'ECONOMY_CONFIG_WRITE',
  BILLING_VOID: 'BILLING_VOID',
  HOST_APPLICATIONS: 'HOST_APPLICATIONS',
  AGENCY_ROLES_WRITE: 'AGENCY_ROLES_WRITE',
  ADMIN_ROLES_WRITE: 'ADMIN_ROLES_WRITE',
  CALLS_READ: 'CALLS_READ',
  CALLS_WRITE: 'CALLS_WRITE',
  AUDIT_LOGS_READ: 'AUDIT_LOGS_READ'
});

const ROLE_PERMISSION_MATRIX = Object.freeze({
  [ADMIN_ROLES.SUPER_ADMIN]: new Set(Object.values(ADMIN_PERMISSIONS)),
  [ADMIN_ROLES.FINANCE_ADMIN]: new Set([
    ADMIN_PERMISSIONS.PAYOUTS_REVIEW,
    ADMIN_PERMISSIONS.ECONOMY_CONFIG_READ,
    ADMIN_PERMISSIONS.BILLING_VOID,
    ADMIN_PERMISSIONS.CALLS_READ,
    ADMIN_PERMISSIONS.CALLS_WRITE,
    ADMIN_PERMISSIONS.AUDIT_LOGS_READ
  ]),
  [ADMIN_ROLES.MODERATION_ADMIN]: new Set([
    ADMIN_PERMISSIONS.AGE_REVIEWS,
    ADMIN_PERMISSIONS.REPORTS_MODERATION,
    ADMIN_PERMISSIONS.USER_STATUS,
    ADMIN_PERMISSIONS.MODERATION_SAMPLES,
    ADMIN_PERMISSIONS.STREAM_TERMINATE,
    ADMIN_PERMISSIONS.AUDIT_LOGS_READ
  ]),
  [ADMIN_ROLES.AGENCY_MANAGER]: new Set([
    ADMIN_PERMISSIONS.HOST_APPLICATIONS,
    ADMIN_PERMISSIONS.AGENCY_ROLES_WRITE,
    ADMIN_PERMISSIONS.AUDIT_LOGS_READ
  ]),
  [ADMIN_ROLES.NONE]: new Set()
});

export function normalizeAdminRole(rawRole, fallbackIsAdmin = false) {
  const upper = String(rawRole || '').trim().toUpperCase();
  if (VALID_ADMIN_ROLE_SET.has(upper)) {
    return upper;
  }
  return fallbackIsAdmin ? ADMIN_ROLES.SUPER_ADMIN : ADMIN_ROLES.NONE;
}

export function resolveEffectiveAdminRole(user) {
  if (!user) return ADMIN_ROLES.NONE;
  const fromField = normalizeAdminRole(user.adminRole, Boolean(user.isAdmin));
  if (fromField !== ADMIN_ROLES.NONE) return fromField;
  return user.isAdmin ? ADMIN_ROLES.SUPER_ADMIN : ADMIN_ROLES.NONE;
}

export function hasAdminPermission(user, permission) {
  const role = resolveEffectiveAdminRole(user);
  const perms = ROLE_PERMISSION_MATRIX[role];
  return Boolean(perms && perms.has(permission));
}

export function assertAdminPermission(user, permission) {
  const role = resolveEffectiveAdminRole(user);
  if (role === ADMIN_ROLES.NONE) {
    throw new CallEngineError('FORBIDDEN_ADMIN', 'صلاحية الإدارة مطلوبة', 403);
  }
  if (!hasAdminPermission(user, permission)) {
    throw new CallEngineError(
      'FORBIDDEN_ROLE_SCOPE',
      `دورك الإداري (${role}) لا يملك صلاحية تنفيذ هذا الإجراء (${permission}).`,
      403,
      { adminRole: role, requiredPermission: permission }
    );
  }
}

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

function parseConfiguredAdminRoleMap(env) {
  const map = new Map();
  const adminUids = String(env.ADMIN_UIDS || '').split(',').map((s) => s.trim()).filter(Boolean);
  for (const uid of adminUids) {
    map.set(uid, ADMIN_ROLES.SUPER_ADMIN);
  }
  const rawMap = String(env.ADMIN_ROLE_MAP || '').trim();
  if (rawMap) {
    if (rawMap.startsWith('{')) {
      try {
        const parsed = JSON.parse(rawMap);
        for (const [uid, role] of Object.entries(parsed)) {
          const cleanRole = normalizeAdminRole(role, false);
          if (uid && cleanRole !== ADMIN_ROLES.NONE) {
            map.set(uid.trim(), cleanRole);
          }
        }
      } catch {
        // Ignore invalid JSON config safely
      }
    } else {
      for (const pair of rawMap.split(',')) {
        const [uid, role] = pair.split(':').map((s) => s?.trim());
        const cleanRole = normalizeAdminRole(role, false);
        if (uid && cleanRole !== ADMIN_ROLES.NONE) {
          map.set(uid, cleanRole);
        }
      }
    }
  }
  return map;
}

function timingSafeCompareStrings(a, b) {
  const hashA = crypto.createHash('sha256').update(String(a || ''), 'utf8').digest();
  const hashB = crypto.createHash('sha256').update(String(b || ''), 'utf8').digest();
  return crypto.timingSafeEqual(hashA, hashB);
}

function extractClientIp(req, env) {
  if (env.TRUST_PROXY === 'true' && req.headers['x-forwarded-for']) {
    const first = String(req.headers['x-forwarded-for']).split(',')[0].trim();
    if (first) return first;
  }
  return String(req.socket?.remoteAddress || req.connection?.remoteAddress || req.ip || '127.0.0.1').trim();
}

function isIpAllowedForAdminKey(clientIp, env) {
  const configured = String(env.ADMIN_KEY_ALLOWED_IPS || '127.0.0.1,::1,::ffff:127.0.0.1')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  if (configured.includes('*')) return true;
  const normalizedIp = clientIp.replace(/^::ffff:/, '');
  return configured.some((allowed) => {
    const normAllowed = allowed.replace(/^::ffff:/, '');
    return clientIp === allowed || normalizedIp === normAllowed;
  });
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

  const isLegacyAdmin = payload.admin === true || payload.role === 'admin';
  const adminRole = normalizeAdminRole(payload.adminRole, isLegacyAdmin);

  return {
    uid: payload.sub,
    name: payload.name || payload.email || payload.sub,
    email: payload.email || null,
    isAdmin: adminRole !== ADMIN_ROLES.NONE,
    adminRole,
    claimStatus: payload.status ? String(payload.status).toUpperCase() : (payload.banned === true ? 'BANNED' : null),
    claimAgeVerified: typeof payload.ageVerified === 'boolean' ? payload.ageVerified : null
  };
}

function applyConfiguredRoles(user, configuredRoleMap) {
  if (!user) return null;
  const configuredRole = configuredRoleMap.get(user.uid);
  const resolvedRole = configuredRole || normalizeAdminRole(user.adminRole, Boolean(user.isAdmin));
  return {
    ...user,
    adminRole: resolvedRole,
    isAdmin: resolvedRole !== ADMIN_ROLES.NONE
  };
}

/**
 * Resolves authenticated user from Authorization Bearer header.
 * Hardened x-admin-key: disabled in production, requires timingSafeEqual + IP allowlist + individual operator attribution.
 */
export async function authenticateRequest(req, env = process.env, customVerifier = null) {
  const authHeader = req.headers['authorization'] || req.headers['Authorization'] || '';
  const match = String(authHeader).match(/^Bearer\s+(.+)$/i);
  const adminKeyHeader = String(req.headers['x-admin-key'] || '').trim();
  const configuredRoleMap = parseConfiguredAdminRoleMap(env);

  if (adminKeyHeader) {
    if (env.NODE_ENV === 'production' && env.ALLOW_ADMIN_API_KEY_IN_PROD !== 'true') {
      throw new CallEngineError(
        'ADMIN_KEY_DISABLED_IN_PRODUCTION',
        'استخدام مفتاح الإدارة السريع (x-admin-key) معطل نهائياً في بيئة الإنتاج. استخدم مصادقة Firebase RBAC.',
        403
      );
    }
    if (!env.ADMIN_API_KEY || !timingSafeCompareStrings(adminKeyHeader, env.ADMIN_API_KEY)) {
      throw new CallEngineError('INVALID_ADMIN_KEY', 'مفتاح الإدارة غير صالح', 401);
    }
    const clientIp = extractClientIp(req, env);
    if (!isIpAllowedForAdminKey(clientIp, env)) {
      throw new CallEngineError('FORBIDDEN_ADMIN_IP', 'عنوان IP غير مصرح له باستخدام مفتاح الإدارة', 403, { clientIp });
    }
    const actorUid = String(req.headers['x-admin-actor-uid'] || '').trim();
    if (!actorUid || actorUid === 'admin_console' || actorUid === 'system') {
      throw new CallEngineError(
        'MISSING_ADMIN_ACTOR_UID',
        'يلزم إرسال معرف المشرف الفردي (x-admin-actor-uid) غير المشترك للتدقيق المحاسبي والأمني',
        401
      );
    }
    const allowedActors = String(env.ADMIN_CONSOLE_ACTOR_UIDS || '').split(',').map((s) => s.trim()).filter(Boolean);
    if (allowedActors.length > 0 && !allowedActors.includes(actorUid)) {
      throw new CallEngineError('FORBIDDEN_ADMIN_ACTOR', 'معرف المشرف غير مدرج ضمن قائمة المشغلين المصرح لهم', 403);
    }
    const requestedRole = String(req.headers['x-admin-actor-role'] || '').trim().toUpperCase();
    const mappedRole = configuredRoleMap.get(actorUid);
    const resolvedRole = mappedRole || (ASSIGNABLE_ADMIN_ROLES.has(requestedRole) ? requestedRole : ADMIN_ROLES.SUPER_ADMIN);
    return {
      uid: actorUid,
      name: `Admin Operator (${actorUid})`,
      email: null,
      isAdmin: true,
      adminRole: resolvedRole,
      authMethod: 'admin_api_key'
    };
  }

  if (!match) {
    return null;
  }

  const token = match[1].trim();
  if (customVerifier) {
    const user = await customVerifier(token);
    return applyConfiguredRoles(user, configuredRoleMap);
  }

  if (env.ALLOW_DEV_AUTH === 'true' && token.startsWith('dev:')) {
    const uid = token.slice(4).trim();
    if (!uid) return null;
    return applyConfiguredRoles({
      uid,
      name: uid,
      email: null,
      isAdmin: false,
      adminRole: ADMIN_ROLES.NONE
    }, configuredRoleMap);
  }

  if (!env.FIREBASE_PROJECT_ID) {
    throw new Error('FIREBASE_PROJECT_ID is not configured on backend');
  }

  const user = await verifyFirebaseIdToken(token, { projectId: env.FIREBASE_PROJECT_ID });
  return applyConfiguredRoles(user, configuredRoleMap);
}
