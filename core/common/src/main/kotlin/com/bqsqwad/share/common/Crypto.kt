package com.bqsqwad.share.common

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SessionCrypto {
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun newKey(): SecretKey {
        val gen = KeyGenerator.getInstance("AES")
        gen.init(256)
        return gen.generateKey()
    }

    fun keyToString(key: SecretKey): String = Base64.getUrlEncoder().withoutPadding().encodeToString(key.encoded)

    fun keyFromString(value: String): SecretKey {
        val raw = Base64.getUrlDecoder().decode(value)
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val body = cipher.doFinal(plain)
        return iv + body
    }

    fun decrypt(key: SecretKey, payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES) { "Payload too short" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val body = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    fun sixDigitCode(): String {
        val n = SecureRandom().nextInt(1_000_000)
        return n.toString().padStart(6, '0')
    }

    fun fingerprint(key: SecretKey): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return digest.take(4).joinToString(" ") { "%02X".format(it) }
    }
}

data class SpeedSample(val bytesPerSecond: Long, val etaSeconds: Long)

fun estimateSpeed(bytesDone: Long, elapsedMs: Long, total: Long): SpeedSample {
    val elapsed = elapsedMs.coerceAtLeast(1)
    val bps = bytesDone * 1000L / elapsed
    val remain = (total - bytesDone).coerceAtLeast(0)
    val eta = if (bps <= 0L) 0L else remain / bps
    return SpeedSample(bps, eta)
}

enum class FileKind { APP, PHOTO, VIDEO, MUSIC, DOCUMENT, FILE, FOLDER, TEXT }

fun kindForMime(mime: String?, name: String): FileKind {
    val m = mime?.lowercase().orEmpty()
    val n = name.lowercase()
    return when {
        m.startsWith("image/") -> FileKind.PHOTO
        m.startsWith("video/") -> FileKind.VIDEO
        m.startsWith("audio/") -> FileKind.MUSIC
        m == "text/plain" && n.endsWith(".txt") && n.startsWith("clip") -> FileKind.TEXT
        m.startsWith("text/") || n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".xls") || n.endsWith(".ppt") -> FileKind.DOCUMENT
        n.endsWith(".apk") -> FileKind.APP
        else -> FileKind.FILE
    }
}

fun folderFor(kind: FileKind): String = when (kind) {
    FileKind.PHOTO -> "Photos"
    FileKind.VIDEO -> "Videos"
    FileKind.MUSIC -> "Music"
    FileKind.DOCUMENT -> "Documents"
    FileKind.APP -> "Apps"
    FileKind.TEXT -> "Clipboard"
    FileKind.FOLDER -> "Folders"
    FileKind.FILE -> "Files"
}
