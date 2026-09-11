package com.piremote.app.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Wire format shared with the relay and the PC agent.
 *
 * Control messages travel as JSON text frames; terminal bytes travel as binary
 * frames with the fixed header below. Mirror of `relay/protocol.js` — the two
 * must be changed together.
 *
 * ```
 * offset  size  field
 * 0       1     type      0x01 stdout | 0x02 stdin | 0x03 replay_done
 * 1       1     flags     reserved
 * 2       4     seq       uint32 BE, increments per chunk
 * 6       16    sessionId UUID
 * 22      ...   payload
 * ```
 */
object Protocol {

    const val HEADER_SIZE = 22
    private const val SID_OFFSET = 6

    const val FRAME_STDOUT = 0x01
    const val FRAME_STDIN = 0x02
    const val FRAME_REPLAY_DONE = 0x03

    class Header(val type: Int, val flags: Int, val seq: Int, val sessionId: UUID)

    fun uuidToBytes(uuid: UUID): ByteArray =
        ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()

    fun bytesToUuid(bytes: ByteArray, offset: Int = 0): UUID {
        val buffer = ByteBuffer.wrap(bytes, offset, 16).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long)
    }

    fun buildFrame(type: Int, seq: Int, sessionId: UUID, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(type.toByte())
        buffer.put(0)
        buffer.putInt(seq)
        buffer.put(uuidToBytes(sessionId))
        buffer.put(payload, 0, payload.size)
        return buffer.array()
    }

    fun readHeader(data: ByteArray): Header? {
        if (data.size < HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val type = buffer.get().toInt() and 0xFF
        val flags = buffer.get().toInt() and 0xFF
        val seq = buffer.getInt()
        return Header(type, flags, seq, bytesToUuid(data, SID_OFFSET))
    }

    fun payloadOf(data: ByteArray): ByteArray =
        if (data.size <= HEADER_SIZE) ByteArray(0) else data.copyOfRange(HEADER_SIZE, data.size)
}
