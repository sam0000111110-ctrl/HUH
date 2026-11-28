import crypto from 'node:crypto';

const OAUTH_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const IDENTITY_TOOLKIT_SCOPE = 'https://www.googleapis.com/auth/identitytoolkit';

function parseServiceAccountJson(raw) {
  if (!raw || typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;
  try {
    return JSON.parse(trimmed);
  } catch {
    try {
      const decoded = Buffer.from(trimmed, 'base64').toString('utf8');
      return JSON.parse(decoded);
    } catch {
      return null;
    }
  }
}

function base64UrlEncode(input) {
  return Buffer.from(input)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

export class FirebaseAdminBridge {
  constructor({ env = process.env, fetchImpl = globalThis.fetch, customTransport = null, nowProvider = () => Date.now() } = {}) {
    this.env = env;
    this.fetchImpl = fetchImpl;
    this.customTransport = customTransport;
    this.now = nowProvider;
    this._cachedAccessToken = null;
    this._cachedTokenExpiresAtMs = 0;
  }

  _resolveServiceAccount() {
    const raw = this.env.FIREBASE_SERVICE_ACCOUNT_JSON || this.env.GOOGLE_SERVICE_ACCOUNT_JSON || '';
    const parsed = parseServiceAccountJson(raw);
    if (!parsed || !parsed.client_email || !parsed.private_key) {
      return null;
    }
    return parsed;
  }

  _resolveProjectId(serviceAccount) {
    return this.env.FIREBASE_PROJECT_ID || serviceAccount?.project_id || '';
  }

  isConfigured() {
    if (typeof this.customTransport === 'function') return true;
    const sa = this._resolveServiceAccount();
    return Boolean(sa && this._resolveProjectId(sa));
  }

  async _getAccessToken(serviceAccount) {
    const nowMs = this.now();
    if (this._cachedAccessToken && nowMs < this._cachedTokenExpiresAtMs - 60_000) {
      return this._cachedAccessToken;
    }
    const iatSec = Math.floor(nowMs / 1000);
    const expSec = iatSec + 3600;
    const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const claims = base64UrlEncode(JSON.stringify({
      iss: serviceAccount.client_email,
      sub: serviceAccount.client_email,
      aud: OAUTH_TOKEN_URL,
      scope: IDENTITY_TOOLKIT_SCOPE,
      iat: iatSec,
      exp: expSec
    }));
    const signingInput = `${header}.${claims}`;
    const signer = crypto.createSign('RSA-SHA256');
    signer.update(signingInput);
    signer.end();
    const signature = base64UrlEncode(signer.sign(serviceAccount.private_key));
    const assertion = `${signingInput}.${signature}`;

    const body = new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion
    });
    const res = await this.fetchImpl(OAUTH_TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    });
    if (!res.ok) {
      throw new Error(`Google OAuth token exchange failed: ${res.status}`);
    }
    const json = await res.json();
    if (!json?.access_token) {
      throw new Error('Missing access_token from Google OAuth response');
    }
    this._cachedAccessToken = json.access_token;
    this._cachedTokenExpiresAtMs = nowMs + Number(json.expires_in || 3600) * 1000;
    return this._cachedAccessToken;
  }

  async _callAccountsUpdate(payload) {
    if (typeof this.customTransport === 'function') {
      return this.customTransport('accounts:update', payload);
    }
    const sa = this._resolveServiceAccount();
    const projectId = this._resolveProjectId(sa);
    if (!sa || !projectId) {
      return { configured: false, skipped: true };
    }
    const token = await this._getAccessToken(sa);
    const endpoint = `https://identitytoolkit.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/accounts:update`;
    const res = await this.fetchImpl(endpoint, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      throw new Error(`Firebase Identity Toolkit update failed (${res.status}): ${text.slice(0, 200)}`);
    }
    return { configured: true, ok: true, data: await res.json().catch(() => ({})) };
  }

  async syncUserClaims({ uid, ageVerified = null, status = null, banned = null, adminRole = null }) {
    if (!uid) return { skipped: true };
    const claims = {};
    if (typeof ageVerified === 'boolean') claims.ageVerified = ageVerified;
    if (status) {
      claims.status = String(status).toUpperCase();
      claims.banned = claims.status === 'BANNED';
    } else if (typeof banned === 'boolean') {
      claims.banned = banned;
    }
    if (adminRole) {
      const normalized = String(adminRole).toUpperCase();
      claims.adminRole = normalized;
      claims.admin = normalized !== 'NONE';
      claims.role = normalized !== 'NONE' ? 'admin' : 'user';
    }
    return this._callAccountsUpdate({
      localId: String(uid),
      customAttributes: JSON.stringify(claims)
    });
  }

  async disableAndRevokeUser({ uid, disabled = true, revokeTokens = true }) {
    if (!uid) return { skipped: true };
    const payload = {
      localId: String(uid),
      disableUser: Boolean(disabled)
    };
    if (revokeTokens) {
      payload.validSince = String(Math.floor(this.now() / 1000));
    }
    return this._callAccountsUpdate(payload);
  }
}
