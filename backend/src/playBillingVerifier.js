import crypto from 'node:crypto';
import { CallEngineError } from './callEngine.js';

/**
 * Authoritative Coin Package Catalog (Server-side source of truth).
 * Product IDs match Google Play Console In-App Product SKUs.
 */
export const SERVER_COIN_CATALOG = Object.freeze({
  pkg_100: {
    packageId: 'pkg_100',
    productId: 'ayer_coins_100',
    baseCoins: 100,
    bonusCoins: 10,
    totalCoins: 110,
    priceUsd: 0.99
  },
  pkg_550: {
    packageId: 'pkg_550',
    productId: 'ayer_coins_550',
    baseCoins: 550,
    bonusCoins: 65,
    totalCoins: 615,
    priceUsd: 4.99
  },
  pkg_1200: {
    packageId: 'pkg_1200',
    productId: 'ayer_coins_1200',
    baseCoins: 1200,
    bonusCoins: 200,
    totalCoins: 1400,
    priceUsd: 9.99
  },
  pkg_3000: {
    packageId: 'pkg_3000',
    productId: 'ayer_coins_3000',
    baseCoins: 3000,
    bonusCoins: 600,
    totalCoins: 3600,
    priceUsd: 24.99
  },
  pkg_7500: {
    packageId: 'pkg_7500',
    productId: 'ayer_coins_7500',
    baseCoins: 7500,
    bonusCoins: 1800,
    totalCoins: 9300,
    priceUsd: 59.99
  }
});

export function resolveCatalogPackage(productIdOrPackageId) {
  const key = String(productIdOrPackageId || '').trim();
  if (!key) return null;
  if (SERVER_COIN_CATALOG[key]) return SERVER_COIN_CATALOG[key];
  return Object.values(SERVER_COIN_CATALOG).find((pkg) => pkg.productId === key) || null;
}

function base64UrlEncode(obj) {
  const buf = Buffer.isBuffer(obj) ? obj : Buffer.from(JSON.stringify(obj), 'utf8');
  return buf.toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

async function getGooglePlayAccessToken(env, fetchImpl = globalThis.fetch) {
  if (env.GOOGLE_PLAY_ACCESS_TOKEN) {
    return env.GOOGLE_PLAY_ACCESS_TOKEN;
  }
  const rawJson = env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
  if (!rawJson) {
    return null;
  }
  const creds = typeof rawJson === 'string' ? JSON.parse(rawJson) : rawJson;
  const nowSec = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const claim = {
    iss: creds.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: creds.token_uri || 'https://oauth2.googleapis.com/token',
    iat: nowSec,
    exp: nowSec + 3600
  };
  const signingInput = `${base64UrlEncode(header)}.${base64UrlEncode(claim)}`;
  const signer = crypto.createSign('RSA-SHA256');
  signer.update(signingInput);
  signer.end();
  const sig = signer.sign(creds.private_key);
  const assertion = `${signingInput}.${base64UrlEncode(sig)}`;

  const tokenRes = await fetchImpl(claim.aud, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion
    })
  });
  if (!tokenRes.ok) {
    throw new CallEngineError('PLAY_OAUTH_FAILED', 'تعذر الحصول على رمز مصادقة Google Play Developer API', 502);
  }
  const tokenJson = await tokenRes.json();
  return tokenJson.access_token;
}

/**
 * Verifies a Google Play consumable purchase via Google Play Developer API v3.
 * Decoupled from consumption: verifies purchase status and obfuscated account id,
 * allowing ledger crediting to happen before consumption.
 */
export async function verifyGooglePlayProductPurchase({
  packageName,
  productId,
  purchaseToken,
  expectedObfuscatedAccountId = null,
  env = process.env,
  fetchImpl = globalThis.fetch,
  customVerifier = null
}) {
  if (!packageName || !productId || !purchaseToken) {
    throw new CallEngineError('INVALID_IAP_PAYLOAD', 'بيانات شراء Google Play غير مكتملة', 400);
  }

  const expectedPkgName = env.ANDROID_PACKAGE_NAME || 'com.aistudio.ayer.live';
  if (packageName !== expectedPkgName) {
    throw new CallEngineError('INVALID_PACKAGE_NAME', 'معرّف التطبيق غير مطابق للحزمة المعتمدة', 403);
  }

  const catalogEntry = resolveCatalogPackage(productId);
  if (!catalogEntry) {
    throw new CallEngineError('UNKNOWN_PRODUCT_ID', 'حزمة النقاط غير معروفة في كتالوج الخادم', 400);
  }

  if (customVerifier) {
    const verified = await customVerifier({
      packageName,
      productId: catalogEntry.productId,
      purchaseToken,
      expectedObfuscatedAccountId
    });
    if (expectedObfuscatedAccountId) {
      const actualId = verified.obfuscatedExternalAccountId || null;
      if (!actualId && env.ALLOW_MISSING_OBFUSCATED_ACCOUNT_ID !== 'true') {
        throw new CallEngineError('FORBIDDEN_PURCHASE_ACCOUNT_MISSING', 'معرف الحساب المشفر مفقود في إيصال الشراء', 403);
      }
      if (actualId && actualId !== expectedObfuscatedAccountId) {
        throw new CallEngineError('FORBIDDEN_PURCHASE_ACCOUNT_MISMATCH', 'معرف الحساب المشفر لا يطابق المستخدم الموثق', 403);
      }
    }
    return {
      catalogEntry,
      orderId: verified.orderId || null,
      purchaseState: verified.purchaseState ?? 0,
      acknowledged: verified.acknowledged ?? false,
      obfuscatedExternalAccountId: verified.obfuscatedExternalAccountId || null,
      verificationSource: verified.verificationSource || 'custom_verifier'
    };
  }

  const accessToken = await getGooglePlayAccessToken(env, fetchImpl);
  if (!accessToken) {
    throw new CallEngineError(
      'PLAY_BILLING_NOT_CONFIGURED',
      'خدمة التحقق من Google Play غير مهيأة على الخادم (GOOGLE_PLAY_SERVICE_ACCOUNT_JSON)',
      503
    );
  }

  const baseApi = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/products/${encodeURIComponent(catalogEntry.productId)}/tokens/${encodeURIComponent(purchaseToken)}`;
  const res = await fetchImpl(baseApi, {
    method: 'GET',
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (!res.ok) {
    throw new CallEngineError('PLAY_VERIFICATION_REJECTED', 'فشل التحقق من إيصال Google Play', 402);
  }
  const purchase = await res.json();
  // purchaseState: 0 = Purchased, 1 = Canceled, 2 = Pending
  if (purchase.purchaseState !== 0) {
    throw new CallEngineError('PLAY_PURCHASE_NOT_COMPLETED', 'حالة الشراء غير مكتملة أو ملغاة', 402);
  }
  if (purchase.consumptionState === 1) {
    throw new CallEngineError('PLAY_PURCHASE_ALREADY_CONSUMED', 'تم استهلاك عملية الشراء هذه مسبقاً', 409);
  }

  if (expectedObfuscatedAccountId) {
    const actualId = purchase.obfuscatedExternalAccountId || null;
    if (!actualId && env.ALLOW_MISSING_OBFUSCATED_ACCOUNT_ID !== 'true') {
      throw new CallEngineError('FORBIDDEN_PURCHASE_ACCOUNT_MISSING', 'معرف الحساب المشفر مفقود في إيصال Google Play', 403);
    }
    if (actualId && actualId !== expectedObfuscatedAccountId) {
      throw new CallEngineError('FORBIDDEN_PURCHASE_ACCOUNT_MISMATCH', 'معرف الحساب المشفر لا يطابق المستخدم الموثق', 403);
    }
  }

  const acknowledged = purchase.acknowledgementState === 1;

  return {
    catalogEntry,
    orderId: purchase.orderId || null,
    purchaseState: purchase.purchaseState,
    acknowledged,
    obfuscatedExternalAccountId: purchase.obfuscatedExternalAccountId || null,
    verificationSource: 'google_play_androidpublisher_v3'
  };
}

/**
 * Acknowledges and consumes a verified purchase on Google Play Developer API v3.
 * Called strictly AFTER atomic crediting in the server ledger.
 */
export async function acknowledgeAndConsumeGooglePlayPurchase({
  packageName,
  productId,
  purchaseToken,
  env = process.env,
  fetchImpl = globalThis.fetch,
  customConsumer = null
}) {
  if (!packageName || !productId || !purchaseToken) {
    return { acknowledged: false, consumed: false };
  }

  if (customConsumer) {
    return await customConsumer({ packageName, productId, purchaseToken });
  }

  const catalogEntry = resolveCatalogPackage(productId);
  const actualProductId = catalogEntry?.productId || productId;

  const accessToken = await getGooglePlayAccessToken(env, fetchImpl).catch(() => null);
  if (!accessToken) {
    return { acknowledged: false, consumed: false, note: 'ACCESS_TOKEN_UNAVAILABLE' };
  }

  const baseApi = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/products/${encodeURIComponent(actualProductId)}/tokens/${encodeURIComponent(purchaseToken)}`;

  let acknowledged = false;
  try {
    const ackRes = await fetchImpl(`${baseApi}:acknowledge`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ developerPayload: 'ayer_verified' })
    });
    acknowledged = ackRes.ok;
  } catch {
    // Non-fatal: ledger already credited
  }

  let consumed = false;
  try {
    const conRes = await fetchImpl(`${baseApi}:consume`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    consumed = conRes.ok;
  } catch {
    // Non-fatal: user has coins, subsequent retries are idempotent
  }

  return { acknowledged, consumed };
}
