'use strict';

function countNewlines(buf) {
  let n = 0;
  for (let i = 0; i < buf.length; i += 1) {
    if (buf[i] === 0x0a) n += 1;
  }
  return n;
}

// Bounded replay buffer. Evicts oldest chunks once either limit is exceeded so
// a long-running session cannot grow the agent's memory without bound.
class RingBuffer {
  constructor({ maxLines = 5000, maxBytes = 2 * 1024 * 1024 } = {}) {
    this.maxLines = maxLines;
    this.maxBytes = maxBytes;
    this.chunks = [];
    this.bytes = 0;
    this.lines = 0;
  }

  push(seq, data) {
    this.chunks.push({ seq, data });
    this.bytes += data.length;
    this.lines += countNewlines(data);
    this._trim();
  }

  _trim() {
    while (
      this.chunks.length > 1 &&
      (this.bytes > this.maxBytes || this.lines > this.maxLines)
    ) {
      const removed = this.chunks.shift();
      this.bytes -= removed.data.length;
      this.lines -= countNewlines(removed.data);
    }
  }

  get firstSeq() {
    return this.chunks.length ? this.chunks[0].seq : null;
  }

  get lastSeq() {
    return this.chunks.length ? this.chunks[this.chunks.length - 1].seq : null;
  }

  // Returns the chunks a client with `lastSeq` has not seen yet, plus whether
  // anything was evicted in the meantime (in which case the client's screen is
  // no longer reconstructible and must be reset).
  since(lastSeq) {
    const first = this.firstSeq;
    const gap = first !== null && lastSeq + 1 < first;
    return {
      chunks: this.chunks.filter((c) => c.seq > lastSeq),
      gap,
    };
  }

  get stats() {
    return { chunks: this.chunks.length, bytes: this.bytes, lines: this.lines };
  }
}

module.exports = { RingBuffer, countNewlines };
