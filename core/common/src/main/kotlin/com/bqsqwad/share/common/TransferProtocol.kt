package com.bqsqwad.share.common

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey

/**
 * Frame: magic(4)='BQSF' | version(1)=1 | type(1) | flags(2) | bodyLen(4) | body
 * Types: HELLO=1 OFFER=2 ACCEPT=3 CHUNK=4 ACK=5 COMPLETE=6 CANCEL=7 RESUME=8
 * CHUNK body: fileId(4) | offset(8) | length(4) | sha256(32) | data
 */
object TransferProtocol {
    const val MAGIC = 0x42515346
    const val VERSION: Byte = 1
    const val TYPE_HELLO: Byte = 1
    const val TYPE_OFFER: Byte = 2
    const val TYPE_ACCEPT: Byte = 3
    const val TYPE_CHUNK: Byte = 4
    const val TYPE_ACK: Byte = 5
    const val TYPE_COMPLETE: Byte = 6
    const val TYPE_CANCEL: Byte = 7
    const val TYPE_RESUME: Byte = 8
    const val DEFAULT_CHUNK = 256 * 1024
    const val MAX_CHUNK = 1024 * 1024

    fun sha256(data: ByteArray, offset: Int = 0, length: Int = data.size): ByteArray {
        val d = MessageDigest.getInstance("SHA-256")
        d.update(data, offset, length)
        return d.digest()
    }

    fun sha256Hex(data: ByteArray): String =
        sha256(data).joinToString("") { "%02x".format(it) }

    fun frame(type: Byte, body: ByteArray, flags: Short = 0): ByteArray {
        val buf = ByteBuffer.allocate(12 + body.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MAGIC)
        buf.put(VERSION)
        buf.put(type)
        buf.putShort(flags)
        buf.putInt(body.size)
        buf.put(body)
        return buf.array()
    }

    data class ParsedFrame(val type: Byte, val flags: Short, val body: ByteArray)

    fun parse(frame: ByteArray): ParsedFrame {
        require(frame.size >= 12) { "Frame too short" }
        val buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.int == MAGIC) { "Bad magic" }
        require(buf.get() == VERSION) { "Unsupported version" }
        val type = buf.get()
        val flags = buf.short
        val len = buf.int
        require(len >= 0 && len == buf.remaining()) { "Bad length" }
        val body = ByteArray(len)
        buf.get(body)
        return ParsedFrame(type, flags, body)
    }

    fun offerBody(fileId: Int, name: String, size: Long, fileSha256: ByteArray): ByteArray {
        require(fileId >= 0) { "Invalid file ID" }
        require(size >= 0L) { "Invalid file size" }
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size in 1..4096) { "Invalid file name length" }
        require(fileSha256.size == 32) { "SHA-256 must be 32 bytes" }
        val buf = ByteBuffer.allocate(4 + 8 + 4 + nameBytes.size + 32).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(fileId)
        buf.putLong(size)
        buf.putInt(nameBytes.size)
        buf.put(nameBytes)
        buf.put(fileSha256)
        return buf.array()
    }

    data class Offer(val fileId: Int, val size: Long, val name: String, val fileSha256: ByteArray)

    fun parseOffer(body: ByteArray): Offer {
        require(body.size >= 49) { "Offer too short" }
        val buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        val fileId = buf.int
        val size = buf.long
        val nlen = buf.int
        require(fileId >= 0) { "Invalid file ID" }
        require(size >= 0L) { "Invalid file size" }
        require(nlen in 1..4096 && nlen <= buf.remaining() - 32) { "Invalid file name length" }
        val nameBytes = ByteArray(nlen)
        buf.get(nameBytes)
        require(buf.remaining() == 32) { "Invalid offer length" }
        val hash = ByteArray(32)
        buf.get(hash)
        return Offer(fileId, size, String(nameBytes, Charsets.UTF_8), hash)
    }

    fun chunkBody(fileId: Int, offset: Long, data: ByteArray): ByteArray {
        require(fileId >= 0) { "Invalid file ID" }
        require(offset >= 0L) { "Invalid chunk offset" }
        require(data.size in 1..MAX_CHUNK) { "Invalid chunk size" }
        val hash = sha256(data)
        val buf = ByteBuffer.allocate(4 + 8 + 4 + 32 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(fileId)
        buf.putLong(offset)
        buf.putInt(data.size)
        buf.put(hash)
        buf.put(data)
        return buf.array()
    }

    data class Chunk(val fileId: Int, val offset: Long, val data: ByteArray, val chunkSha256: ByteArray)

    fun parseChunk(body: ByteArray): Chunk {
        require(body.size >= 49) { "Chunk too short" }
        val buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        val fileId = buf.int
        val offset = buf.long
        val len = buf.int
        require(fileId >= 0) { "Invalid file ID" }
        require(offset >= 0L) { "Invalid chunk offset" }
        require(len in 1..MAX_CHUNK && len == buf.remaining() - 32) { "Invalid chunk length" }
        val hash = ByteArray(32)
        buf.get(hash)
        val data = ByteArray(len)
        buf.get(data)
        require(sha256(data).contentEquals(hash)) { "Chunk checksum mismatch" }
        return Chunk(fileId, offset, data, hash)
    }

    fun ackBody(fileId: Int, nextOffset: Long): ByteArray {
        require(fileId >= 0) { "Invalid file ID" }
        require(nextOffset >= 0L) { "Invalid acknowledgement offset" }
        val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(fileId)
        buf.putLong(nextOffset)
        return buf.array()
    }

    data class Ack(val fileId: Int, val nextOffset: Long)

    fun parseAck(body: ByteArray): Ack {
        require(body.size == 12) { "Invalid acknowledgement length" }
        val buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        val fileId = buf.int
        val nextOffset = buf.long
        require(fileId >= 0) { "Invalid file ID" }
        require(nextOffset >= 0L) { "Invalid acknowledgement offset" }
        return Ack(fileId, nextOffset)
    }

    fun encryptFrame(key: SecretKey, plainFrame: ByteArray): ByteArray =
        SessionCrypto.encrypt(key, plainFrame)

    fun decryptFrame(key: SecretKey, cipher: ByteArray): ByteArray =
        SessionCrypto.decrypt(key, cipher)

    fun chunkFile(fileId: Int, content: ByteArray, resumeOffset: Long = 0L, chunkSize: Int = DEFAULT_CHUNK): List<ByteArray> {
        require(chunkSize in 1..MAX_CHUNK)
        require(resumeOffset in 0..content.size.toLong())
        val out = ArrayList<ByteArray>()
        var offset = resumeOffset.toInt()
        while (offset < content.size) {
            val end = minOf(offset + chunkSize, content.size)
            val slice = content.copyOfRange(offset, end)
            out += frame(TYPE_CHUNK, chunkBody(fileId, offset.toLong(), slice))
            offset = end
        }
        return out
    }

    fun verifyFile(content: ByteArray, expectedSha256: ByteArray): Boolean =
        sha256(content).contentEquals(expectedSha256)

    fun shortShareId(seed: ByteArray = ByteArray(4).also { SecureRandom().nextBytes(it) }): String {
        val n = ((seed[0].toInt() and 0xff) shl 8) or (seed[1].toInt() and 0xff)
        return "BQ-%04d".format(n % 10000)
    }

    fun internetCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return (1..6).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }
}
