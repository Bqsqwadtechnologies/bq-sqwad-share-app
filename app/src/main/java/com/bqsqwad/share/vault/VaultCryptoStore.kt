package com.bqsqwad.share.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM vault storage using a non-exportable, strong-biometric-authenticated Android Keystore key. */
class VaultCryptoStore {
    private val keyAlias = "com.bqsqwad.share.private-vault.aes"
    private val transformation = "AES/GCM/NoPadding"

    fun prepareEncryptCipher(): Cipher =
        Cipher.getInstance(transformation).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }

    fun prepareDecryptCipher(encryptedFile: File): Cipher {
        val iv = DataInputStream(BufferedInputStream(FileInputStream(encryptedFile))).use { input ->
            require(input.readInt() == MAGIC) { "This file is not a supported vault item." }
            ByteArray(IV_LENGTH).also(input::readFully)
        }
        return Cipher.getInstance(transformation).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        }
    }

    fun encryptFile(cipher: Cipher, source: File, destination: File): File {
        require(source.isFile && source.canRead()) { "A readable source file is required." }
        require(!destination.exists()) { "The vault destination already exists." }
        require(destination.parentFile?.let { it.exists() || it.mkdirs() } != false)
        val temporary = File(destination.parentFile, "." + destination.name + ".partial")
        try {
            FileOutputStream(temporary).use { raw ->
                raw.write(intBytes(MAGIC))
                raw.write(cipher.iv ?: error("Missing vault initialization vector."))
                CipherOutputStream(BufferedOutputStream(raw), cipher).use { encrypted ->
                    BufferedInputStream(FileInputStream(source)).use { it.copyTo(encrypted) }
                }
            }
            check(temporary.renameTo(destination)) { "Could not finalize encrypted vault item." }
            return destination
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    fun decryptFile(cipher: Cipher, encryptedFile: File, destination: File): File {
        require(encryptedFile.isFile && encryptedFile.canRead())
        require(!destination.exists()) { "The restore destination already exists." }
        require(destination.parentFile?.let { it.exists() || it.mkdirs() } != false)
        val temporary = File(destination.parentFile, "." + destination.name + ".partial")
        try {
            DataInputStream(BufferedInputStream(FileInputStream(encryptedFile))).use { input ->
                require(input.readInt() == MAGIC) { "This file is not a supported vault item." }
                val iv = ByteArray(IV_LENGTH).also(input::readFully)
                require(cipher.iv?.contentEquals(iv) == true) { "Vault authentication context mismatch." }
                CipherInputStream(input, cipher).use { decrypted ->
                    BufferedOutputStream(FileOutputStream(temporary)).use { decrypted.copyTo(it) }
                }
            }
            check(temporary.renameTo(destination)) { "Could not finalize restored file." }
            return destination
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun getOrCreateKey(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? java.security.Key)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }.build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
    )

    companion object {
        private const val MAGIC = 0x42515631
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
