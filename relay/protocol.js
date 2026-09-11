'use strict';

const HEADER_SIZE = 22;
const SID_OFFSET = 6;
const SID_SIZE = 16;

const FRAME = {
  STDOUT: 0x01,
  STDIN: 0x02,
  REPLAY_DONE: 0x03,
};

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function uuidToBytes(uuid) {
  const hex = String(uuid).replace(/-/g, '');
  if (hex.length !== 32) throw new Error(`invalid uuid: ${uuid}`);
  return Buffer.from(hex, 'hex');
}

function bytesToUuid(bytes) {
  if (bytes.length !== SID_SIZE) throw new Error('session id must be 16 bytes');
  const hex = Buffer.from(bytes).toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function readHeader(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < HEADER_SIZE) return null;
  return {
    type: buf.readUInt8(0),
    flags: buf.readUInt8(1),
    seq: buf.readUInt32BE(2),
    sid: buf.subarray(SID_OFFSET, SID_OFFSET + SID_SIZE),
  };
}

function buildFrame(type, seq, sid, payload) {
  const sidBytes = Buffer.isBuffer(sid) ? sid : uuidToBytes(sid);
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(payload || '', 'utf8');
  const buf = Buffer.allocUnsafe(HEADER_SIZE + body.length);
  buf.writeUInt8(type, 0);
  buf.writeUInt8(0, 1);
  buf.writeUInt32BE(seq >>> 0, 2);
  sidBytes.copy(buf, SID_OFFSET);
  body.copy(buf, HEADER_SIZE);
  return buf;
}

module.exports = {
  HEADER_SIZE,
  SID_SIZE,
  FRAME,
  UUID_RE,
  uuidToBytes,
  bytesToUuid,
  readHeader,
  buildFrame,
};
