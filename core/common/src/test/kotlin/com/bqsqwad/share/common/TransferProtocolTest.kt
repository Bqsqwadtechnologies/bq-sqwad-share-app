package com.bqsqwad.share.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

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
    fun fileManifestRoundTripsAndCorrelatesByNearbyPayloadId() {
        val digest = ByteArray(32) { (it * 3).toByte() }
        val encoded = TransferProtocol.fileManifestBody(
            filePayloadId = 987654321L,
            name = "family-photo.jpg",
            mime = "image/jpeg",
            size = 8_765_432L,
            fileSha256 = digest,
        )
        val decoded = TransferProtocol.parseFileManifest(encoded)
        assertEquals(987654321L, decoded.filePayloadId)
        assertEquals("family-photo.jpg", decoded.name)
        assertEquals("image/jpeg", decoded.mime)
        assertEquals(8_765_432L, decoded.size)
        assertArrayEquals(digest, decoded.sha256)
    }

    @Test
    fun fileManifestRejectsCorruptionAndInvalidMetadata() {
        val valid = TransferProtocol.fileManifestBody(5L, "doc.pdf", "application/pdf", 10L, ByteArray(32))
        val corrupt = valid.copyOf().also { it[0] = 0 }
        try {
            TransferProtocol.parseFileManifest(corrupt)
            throw AssertionError("Corrupt manifest should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        try {
            TransferProtocol.fileManifestBody(0L, "doc.pdf", "application/pdf", 10L, ByteArray(32))
            throw AssertionError("Invalid payload ID should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
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
    fun rejectsMalformedFrameLengthsAndTrailingBytes() {
        val valid = TransferProtocol.frame(TransferProtocol.TYPE_HELLO, byteArrayOf(1, 2))
        val malformedFrames = listOf(valid.copyOf(valid.size - 1), valid + byteArrayOf(3))
        malformedFrames.forEach { malformed ->
            try {
                TransferProtocol.parse(malformed)
                throw AssertionError("Malformed frame should be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun rejectsMalformedOfferChunkAndAckBodies() {
        val validOffer = TransferProtocol.offerBody(1, "file.txt", 10L, ByteArray(32))
        try {
            TransferProtocol.parseOffer(validOffer.copyOf(validOffer.size - 1))
            throw AssertionError("Truncated offer should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        val validChunk = TransferProtocol.chunkBody(1, 0L, byteArrayOf(1, 2, 3))
        try {
            TransferProtocol.parseChunk(validChunk + byteArrayOf(4))
            throw AssertionError("Chunk with trailing data should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            TransferProtocol.parseAck(ByteArray(13))
            throw AssertionError("Ack with invalid length should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun rejectsNegativeAndOversizedTransferMetadata() {
        try {
            TransferProtocol.offerBody(-1, "file.txt", 1L, ByteArray(32))
            throw AssertionError("Negative file ID should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        try {
            TransferProtocol.chunkBody(1, -1L, byteArrayOf(1))
            throw AssertionError("Negative chunk offset should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        try {
            TransferProtocol.ackBody(1, -1L)
            throw AssertionError("Negative ack offset should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
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
    @Rule @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resumableReceiverPersistsCheckpointAndFinalizesVerifiedFile() {
        val source = ByteArray(70_000) { (it % 239).toByte() }
        val offer = ResumableFileReceiver.Offer(
            transferId = "resume-001",
            displayName = "family-photo.bin",
            totalBytes = source.size.toLong(),
            sha256 = TransferProtocol.sha256(source),
        )
        val firstReceiver = ResumableFileReceiver(temporaryFolder.root)
        assertEquals(0L, firstReceiver.begin(offer))
        val firstChunk = source.copyOfRange(0, 32_000)
        assertEquals(
            32_000L,
            firstReceiver.appendChunk("resume-001", 0L, firstChunk, TransferProtocol.sha256(firstChunk)),
        )

        val afterRestart = ResumableFileReceiver(temporaryFolder.root)
        assertEquals(32_000L, afterRestart.begin(offer))
        assertEquals(32_000L, afterRestart.resumeOffset("resume-001"))
        val remaining = source.copyOfRange(32_000, source.size)
        assertEquals(
            source.size.toLong(),
            afterRestart.appendChunk("resume-001", 32_000L, remaining, TransferProtocol.sha256(remaining)),
        )
        val received = afterRestart.complete("resume-001")
        assertEquals("family-photo.bin", received.name)
        assertArrayEquals(source, received.readBytes())
        assertFalse(File(temporaryFolder.root, "resume-001.meta").exists())
    }

    @Test
    fun resumableReceiverRejectsWrongOffsetsAndCorruptChunks() {
        val source = "verified content".toByteArray()
        val receiver = ResumableFileReceiver(temporaryFolder.root)
        receiver.begin(
            ResumableFileReceiver.Offer(
                "resume-002",
                "document.txt",
                source.size.toLong(),
                TransferProtocol.sha256(source),
            ),
        )
        try {
            receiver.appendChunk("resume-002", 1L, source, TransferProtocol.sha256(source))
            throw AssertionError("Out-of-order chunk must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        try {
            receiver.appendChunk("resume-002", 0L, source, ByteArray(32))
            throw AssertionError("Corrupt chunk must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertEquals(0L, receiver.resumeOffset("resume-002"))
    }

}
