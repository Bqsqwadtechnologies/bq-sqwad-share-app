package com.bqsqwad.share.common

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Recipient-only relay envelope primitives.
 *
 * The sender signs the complete envelope and encrypts the content to an ephemeral
 * ECDH key agreement with the recipient's P-256 public key. Relays can store and
 * forward the serialized bytes but cannot decrypt them. Callers MUST pin and
 * compare the origin and recipient fingerprints against the explicitly trusted
 * key records; an embedded public key alone is not a trust decision.
 *
 * Key persistence is deliberately left to platform secure storage (Android
 * Keystore / Windows certificate or DPAPI-backed storage), never this class.
 */
object RelayEnvelopeCrypto {
    private const val MAGIC = 0x42515245 // BQRE
    private const val VERSION = 1
    private const val MAX_KEY_BYTES = 1024
    private const val MAX_CIPHER_BYTES = 8 * 1024 * 1024 + 64
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    data class IdentityKeys(val signing: KeyPair, val agreement: KeyPair) {
        val signingPublicKey: ByteArray get() = signing.public.encoded.copyOf()
        val agreementPublicKey: ByteArray get() = agreement.public.encoded.copyOf()
        val fingerprint: String get() = fingerprint(signingPublicKey)
    }

    data class OpenedEnvelope(
        val senderFingerprint: String,
        val recipientFingerprint: String,
        val plaintext: ByteArray,
    )

    fun generateIdentity(): IdentityKeys = IdentityKeys(generateEcKeyPair(), generateEcKeyPair())

    fun fingerprint(encodedPublicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
            .joinToString("") { "%02x".format(it) }

    fun seal(
        plaintext: ByteArray,
        sender: IdentityKeys,
        recipientSigningPublicKey: ByteArray,
        recipientAgreementPublicKey: ByteArray,
        createdAtMillis: Long = System.currentTimeMillis(),
        expiresAtMillis: Long = createdAtMillis + 24L * 60L * 60L * 1000L,
    ): ByteArray {
        require(plaintext.isNotEmpty() && plaintext.size <= 8 * 1024 * 1024)
        require(createdAtMillis > 0L && expiresAtMillis > createdAtMillis)
        val recipientSigning = decodePublic(recipientSigningPublicKey)
        val recipientAgreement = decodePublic(recipientAgreementPublicKey)
        val recipientFingerprint = fingerprint(recipientSigning.encoded)
        val ephemeral = generateEcKeyPair()
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val shared = agree(ephemeral.private, recipientAgreement)
        val aesKey = deriveKey(shared, nonce, sender.fingerprint, recipientFingerprint)
        val aad = associatedData(
            sender.signing.public.encoded,
            recipientSigning.encoded,
            ephemeral.public.encoded,
            nonce,
            createdAtMillis,
            expiresAtMillis,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }.doFinal(plaintext)
        val unsigned = serializeUnsigned(
            sender.signing.public.encoded,
            recipientSigning.encoded,
            ephemeral.public.encoded,
            nonce,
            createdAtMillis,
            expiresAtMillis,
            cipher,
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(sender.signing.private)
            update(unsigned)
            sign()
        }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(unsigned)
                out.writeShort(signature.size)
                out.write(signature)
            }
            bytes.toByteArray()
        }
    }

    fun open(
        encodedEnvelope: ByteArray,
        recipientAgreementPrivateKey: PrivateKey,
        expectedRecipientFingerprint: String,
        trustedSenderFingerprint: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): OpenedEnvelope {
        require(encodedEnvelope.size in 64..MAX_CIPHER_BYTES + 4096) { "Invalid relay envelope size." }
        val parsed = parse(encodedEnvelope)
        val senderKey = decodePublic(parsed.senderSigningKey)
        val recipientKey = decodePublic(parsed.recipientSigningKey)
        val senderFingerprint = fingerprint(senderKey.encoded)
        val recipientFingerprint = fingerprint(recipientKey.encoded)
        require(constantTimeEquals(senderFingerprint, trustedSenderFingerprint)) { "Sender key is not trusted." }
        require(constantTimeEquals(recipientFingerprint, expectedRecipientFingerprint)) { "Envelope targets a different recipient key." }
        require(parsed.expiresAtMillis > nowMillis && parsed.createdAtMillis <= nowMillis + 5 * 60_000L) { "Relay envelope is expired or not yet valid." }

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(senderKey)
        verifier.update(parsed.unsigned)
        require(verifier.verify(parsed.signature)) { "Relay envelope signature is invalid." }

        val ephemeralKey = decodePublic(parsed.ephemeralAgreementKey)
        val shared = agree(recipientAgreementPrivateKey, ephemeralKey)
        val aesKey = deriveKey(shared, parsed.nonce, senderFingerprint, recipientFingerprint)
        val aad = associatedData(
            parsed.senderSigningKey,
            parsed.recipientSigningKey,
            parsed.ephemeralAgreementKey,
            parsed.nonce,
            parsed.createdAtMillis,
            parsed.expiresAtMillis,
        )
        val plaintext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(TAG_BITS, parsed.nonce))
            updateAAD(aad)
            doFinal(parsed.ciphertext)
        }
        return OpenedEnvelope(senderFingerprint, recipientFingerprint, plaintext)
    }

    private data class Parsed(
        val senderSigningKey: ByteArray,
        val recipientSigningKey: ByteArray,
        val ephemeralAgreementKey: ByteArray,
        val nonce: ByteArray,
        val createdAtMillis: Long,
        val expiresAtMillis: Long,
        val ciphertext: ByteArray,
        val unsigned: ByteArray,
        val signature: ByteArray,
    )

    private fun serializeUnsigned(
        sender: ByteArray,
        recipient: ByteArray,
        ephemeral: ByteArray,
        nonce: ByteArray,
        created: Long,
        expires: Long,
        ciphertext: ByteArray,
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeByte(VERSION)
            writeSized(out, sender)
            writeSized(out, recipient)
            writeSized(out, ephemeral)
            out.writeByte(nonce.size)
            out.write(nonce)
            out.writeLong(created)
            out.writeLong(expires)
            writeSized(out, ciphertext)
        }
        bytes.toByteArray()
    }

    private fun parse(bytes: ByteArray): Parsed {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC && input.readUnsignedByte() == VERSION) { "Unsupported relay envelope." }
        val sender = readSized(input, MAX_KEY_BYTES)
        val recipient = readSized(input, MAX_KEY_BYTES)
        val ephemeral = readSized(input, MAX_KEY_BYTES)
        val nonceLength = input.readUnsignedByte()
        require(nonceLength == NONCE_BYTES)
        val nonce = ByteArray(nonceLength).also(input::readFully)
        val created = input.readLong()
        val expires = input.readLong()
        val ciphertext = readSized(input, MAX_CIPHER_BYTES)
        val unsignedLength = bytes.size - input.available()
        val unsigned = bytes.copyOfRange(0, unsignedLength)
        val signatureLength = input.readUnsignedShort()
        require(signatureLength in 8..144 && signatureLength == input.available())
        val signature = ByteArray(signatureLength).also(input::readFully)
        require(input.available() == 0)
        return Parsed(sender, recipient, ephemeral, nonce, created, expires, ciphertext, unsigned, signature)
    }

    private fun associatedData(
        sender: ByteArray,
        recipient: ByteArray,
        ephemeral: ByteArray,
        nonce: ByteArray,
        created: Long,
        expires: Long,
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            writeSized(out, sender); writeSized(out, recipient); writeSized(out, ephemeral)
            out.write(nonce); out.writeLong(created); out.writeLong(expires)
        }
        bytes.toByteArray()
    }

    private fun writeSized(out: DataOutputStream, value: ByteArray) {
        require(value.isNotEmpty() && value.size <= MAX_CIPHER_BYTES)
        out.writeInt(value.size)
        out.write(value)
    }

    private fun readSized(input: DataInputStream, max: Int): ByteArray {
        val length = input.readInt()
        require(length in 1..max && length <= input.available())
        return ByteArray(length).also(input::readFully)
    }

    private fun generateEcKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun decodePublic(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    private fun agree(privateKey: PrivateKey, publicKey: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }

    private fun deriveKey(shared: ByteArray, nonce: ByteArray, sender: String, recipient: String): SecretKeySpec {
        val salt = MessageDigest.getInstance("SHA-256").digest(nonce + sender.toByteArray() + recipient.toByteArray())
        val extract = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(salt, "HmacSHA256"))
            doFinal(shared)
        }
        val expand = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(extract, "HmacSHA256"))
            doFinal("BQ Sqwad Share relay envelope v1".toByteArray() + byteArrayOf(1))
        }
        shared.fill(0)
        extract.fill(0)
        return SecretKeySpec(expand.copyOf(32), "AES")
    }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.US_ASCII), right.toByteArray(Charsets.US_ASCII))
}
