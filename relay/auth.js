'use strict';

const crypto = require('crypto');

const MAX_SKEW_MS = 30_000;
const NONCE_TTL_MS = 120_000;
const ROLES = new Set(['agent', 'client']);

function computeSignature(token, role, id, ts, nonce) {
  return crypto
    .createHmac('sha256', token)
    .update(`${role}|${id}|${ts}|${nonce}`)
    .digest('hex');
}

class NonceCache {
  constructor(ttlMs = NONCE_TTL_MS) {
    this.ttlMs = ttlMs;
    this.seen = new Map();
  }

  claim(role, id, nonce) {
    const now = Date.now();
    for (const [key, at] of this.seen) {
      if (now - at > this.ttlMs) this.seen.delete(key);
    }
    const key = `${role}|${id}|${nonce}`;
    if (this.seen.has(key)) return false;
    this.seen.set(key, now);
    return true;
  }
}

// Returns null when the handshake is valid, otherwise a short reason string.
function verifyHandshake(handshake, token, nonces) {
  if (!handshake || typeof handshake !== 'object') return 'malformed hello';
  const { role, id, ts, nonce, sig } = handshake;

  if (!ROLES.has(role)) return 'unknown role';
  if (!token) return 'no token configured for role';
  if (typeof id !== 'string' || id.length === 0 || id.length > 64) return 'invalid id';
  if (typeof ts !== 'number' || !Number.isFinite(ts)) return 'invalid ts';
  if (typeof nonce !== 'string' || nonce.length < 8 || nonce.length > 64) return 'invalid nonce';
  if (typeof sig !== 'string' || !/^[0-9a-f]{64}$/.test(sig)) return 'invalid signature format';
  if (Math.abs(Date.now() - ts) > MAX_SKEW_MS) return 'timestamp outside window';

  const expected = Buffer.from(computeSignature(token, role, id, ts, nonce), 'hex');
  const provided = Buffer.from(sig, 'hex');
  if (expected.length !== provided.length) return 'bad signature';
  if (!crypto.timingSafeEqual(expected, provided)) return 'bad signature';
  if (!nonces.claim(role, id, nonce)) return 'replayed nonce';

  return null;
}

module.exports = { computeSignature, verifyHandshake, NonceCache, MAX_SKEW_MS };
