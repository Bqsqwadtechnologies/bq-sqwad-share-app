package com.bqsqwad.share.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferProtocolTest {
    @Test
    fun frameRoundTrip() {
        val body = "hello".toByteArray()
        val framed = TransferProtocol.frame(TransferProtocol.TYPE_HELLO, body)
        val parsed = TransferProtocol.parse(framed)
        assertEquals(TransferProtocol.TYPE_HELLO, parsed.type)
        assertArrayEquals(body, parsed.body)
    }

    @Test
    fun offerAndChunkWithChecksum() {
        val file = ByteArray(600_000) { it.toByte() }
        val hash = TransferProtocol.sha256(file)
        val offer = TransferProtocol.offerBody(7, "photo.jpg", file.size.toLong(), hash)
        val o = TransferProtocol.parseOffer(offer)
        assertEquals(7, o.fileId)
        assertEquals("photo.jpg", o.name)
        assertArrayEquals(hash, o.fileSha256)

        val frames = TransferProtocol.chunkFile(7, file, resumeOffset = 0, chunkSize = 128 * 1024)
        assertTrue(frames.size >= 4)
        var reconstructed = ByteArray(0)
        for (f in frames) {
            val p = TransferProtocol.parse(f)
            assertEquals(TransferProtocol.TYPE_CHUNK, p.type)
            val c = TransferProtocol.parseChunk(p.body)
            assertEquals(reconstructed.size.toLong(), c.offset)
            reconstructed += c.data
        }
        assertArrayEquals(file, reconstructed)
        assertTrue(TransferProtocol.verifyFile(reconstructed, hash))
    }

    @Test
    fun resumeFromOffset() {
        val file = ByteArray(50_000) { (it % 251).toByte() }
        val frames = TransferProtocol.chunkFile(1, file, resumeOffset = 20_000, chunkSize = 10_000)
        var offset = 20_000L
        for (f in frames) {
            val c = TransferProtocol.parseChunk(TransferProtocol.parse(f).body)
            assertEquals(offset, c.offset)
            offset += c.data.size
        }
        assertEquals(file.size.toLong(), offset)
    }

    @Test
    fun badChunkChecksumFails() {
        val data = "abc".toByteArray()
        val body = TransferProtocol.chunkBody(1, 0, data)
        body[20] = (body[20].toInt() xor 0xff).toByte()
        try {
            TransferProtocol.parseChunk(body)
            assertFalse("expected checksum failure", true)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("checksum"))
        }
    }

    @Test
    fun encryptedFrames() {
        val key = SessionCrypto.newKey()
        val plain = TransferProtocol.frame(TransferProtocol.TYPE_COMPLETE, byteArrayOf(1, 2, 3))
        val enc = TransferProtocol.encryptFrame(key, plain)
        val dec = TransferProtocol.decryptFrame(key, enc)
        assertArrayEquals(plain, dec)
        assertEquals(TransferProtocol.TYPE_COMPLETE, TransferProtocol.parse(dec).type)
    }

    @Test
    fun shareIdAndInternetCode() {
        val id = TransferProtocol.shortShareId()
        assertTrue(id.startsWith("BQ-"))
        assertEquals(7, id.length)
        assertEquals(6, TransferProtocol.internetCode().length)
    }

    @Test
    fun ackResume() {
        val ack = TransferProtocol.parseAck(TransferProtocol.ackBody(3, 99_999))
        assertEquals(3, ack.fileId)
        assertEquals(99_999L, ack.nextOffset)
    }
}
