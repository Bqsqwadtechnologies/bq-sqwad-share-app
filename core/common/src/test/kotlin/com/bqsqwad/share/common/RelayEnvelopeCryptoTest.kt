package com.bqsqwad.share.common

import org.junit.Assert.*
import org.junit.Test

class RelayEnvelopeCryptoTest {
    @Test fun onlyIntendedRecipientCanOpenAndSenderSignatureIsVerified() {
        val sender = RelayEnvelopeCrypto.generateIdentity()
        val recipient = RelayEnvelopeCrypto.generateIdentity()
        val other = RelayEnvelopeCrypto.generateIdentity()
        val plaintext = "private relay payload".toByteArray()
        val envelope = RelayEnvelopeCrypto.seal(
            plaintext,
            sender,
            recipient.signingPublicKey,
            recipient.agreementPublicKey,
            createdAtMillis = 10_000L,
            expiresAtMillis = 20_000L,
        )
        val opened = RelayEnvelopeCrypto.open(
            envelope,
            recipient.agreement.private,
            recipient.recipientFingerprint,
            sender.fingerprint,
            nowMillis = 15_000L,
        )
        assertArrayEquals(plaintext, opened.plaintext)
        assertTrue(runCatching {
            RelayEnvelopeCrypto.open(envelope, other.agreement.private, other.recipientFingerprint, sender.fingerprint, 15_000L)
        }.isFailure)
    }

    @Test fun rejectsWrongPinnedSenderAndTampering() {
        val sender = RelayEnvelopeCrypto.generateIdentity()
        val recipient = RelayEnvelopeCrypto.generateIdentity()
        val stranger = RelayEnvelopeCrypto.generateIdentity()
        val envelope = RelayEnvelopeCrypto.seal(
            byteArrayOf(1, 2, 3),
            sender,
            recipient.signingPublicKey,
            recipient.agreementPublicKey,
            10_000L,
            20_000L,
        )
        assertTrue(runCatching {
            RelayEnvelopeCrypto.open(envelope, recipient.agreement.private, recipient.recipientFingerprint, stranger.fingerprint, 15_000L)
        }.isFailure)
        val tampered = envelope.copyOf().also { it[it.lastIndex - 4] = (it[it.lastIndex - 4].toInt() xor 1).toByte() }
        assertTrue(runCatching {
            RelayEnvelopeCrypto.open(tampered, recipient.agreement.private, recipient.recipientFingerprint, sender.fingerprint, 15_000L)
        }.isFailure)
    }

    @Test fun refusesExpiredEnvelope() {
        val sender = RelayEnvelopeCrypto.generateIdentity()
        val recipient = RelayEnvelopeCrypto.generateIdentity()
        val envelope = RelayEnvelopeCrypto.seal(byteArrayOf(9), sender, recipient.signingPublicKey, recipient.agreementPublicKey, 10_000L, 20_000L)
        assertTrue(runCatching {
            RelayEnvelopeCrypto.open(envelope, recipient.agreement.private, recipient.recipientFingerprint, sender.fingerprint, 20_000L)
        }.isFailure)
    }
}
