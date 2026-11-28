import crypto from 'node:crypto';

const PREFIX = 'enc:v1:';

export function resolveBankEncryptionKey(env = process.env, fallbackSecret = null) {
  const raw = String(env.BANK_ENCRYPTION_KEY || '').trim();
  if (!raw) {
    if (env.NODE_ENV === 'production') {
      throw new Error('BANK_ENCRYPTION_KEY is required in production to encrypt bank payout details.');
    }
    if (!fallbackSecret) {
      throw new Error('Ephemeral bank encryption key missing.');
    }
    return fallbackSecret;
  }
  if (/^[0-9a-fA-F]{64}$/.test(raw)) {
    return Buffer.from(raw, 'hex');
  }
  return crypto.createHash('sha256').update(raw, 'utf8').digest();
}

export function encryptSensitiveField(plaintext, keyBuffer) {
  const text = String(plaintext ?? '').trim();
  if (!text) return '';
  if (text.startsWith(PREFIX)) return text;
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', keyBuffer, iv);
  const encrypted = Buffer.concat([cipher.update(text, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `${PREFIX}${iv.toString('base64')}:${tag.toString('base64')}:${encrypted.toString('base64')}`;
}

export function decryptSensitiveField(storedValue, keyBuffer) {
  const val = String(storedValue ?? '');
  if (!val) return '';
  if (!val.startsWith(PREFIX)) return val;
  const parts = val.slice(PREFIX.length).split(':');
  if (parts.length !== 3) {
    throw new Error('Malformed encrypted field payload');
  }
  const [ivB64, tagB64, cipherB64] = parts;
  const iv = Buffer.from(ivB64, 'base64');
  const tag = Buffer.from(tagB64, 'base64');
  const ciphertext = Buffer.from(cipherB64, 'base64');
  const decipher = crypto.createDecipheriv('aes-256-gcm', keyBuffer, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

export function maskAccountNumber(ibanOrAccount) {
  const clean = String(ibanOrAccount ?? '').replace(/\s+/g, '');
  if (!clean) return '****';
  if (clean.length <= 4) return '*'.repeat(clean.length);
  const tail = clean.slice(-4);
  const maskLen = Math.min(8, Math.max(4, clean.length - 4));
  return `${'*'.repeat(maskLen)}${tail}`;
}
