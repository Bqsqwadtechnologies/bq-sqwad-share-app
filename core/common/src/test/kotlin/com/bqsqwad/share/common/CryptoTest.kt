package com.bqsqwad.share.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoTest {
    @Test
    fun roundTrip() {
        val key = SessionCrypto.newKey()
        val restored = SessionCrypto.keyFromString(SessionCrypto.keyToString(key))
        val plain = "BQ Sqwad Share".toByteArray()
        val enc = SessionCrypto.encrypt(key, plain)
        assertArrayEquals(plain, SessionCrypto.decrypt(restored, enc))
    }

    @Test
    fun codeIsSixDigits() {
        val code = SessionCrypto.sixDigitCode()
        assertEquals(6, code.length)
        code.toInt()
    }

    @Test
    fun speedEta() {
        val sample = estimateSpeed(bytesDone = 1_000, elapsedMs = 1_000, total = 5_000)
        assertEquals(1_000L, sample.bytesPerSecond)
        assertEquals(4L, sample.etaSeconds)
    }

    @Test
    fun mimeFolders() {
        assertEquals(FileKind.PHOTO, kindForMime("image/jpeg", "a.jpg"))
        assertEquals("Videos", folderFor(FileKind.VIDEO))
    }
}
