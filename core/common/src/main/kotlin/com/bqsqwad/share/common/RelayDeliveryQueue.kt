package com.bqsqwad.share.common

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/**
 * Durable store-and-forward queue for recipient-encrypted relay envelopes.
 * Payload bytes are treated as opaque. This queue does not provide encryption,
 * peer authentication, or transport; callers must enforce those at the protocol boundary.
 */
class RelayDeliveryQueue(
    private val directory: File,
    private val quotaBytes: Long = 256L * 1024L * 1024L,
    private val maxEnvelopeBytes: Int = 8 * 1024 * 1024,
    private val maxHops: Int = 8,
    private val maxLifetimeMillis: Long = 7L * 24L * 60L * 60L * 1000L,
) {
    init {
        require(quotaBytes > 0 && maxEnvelopeBytes > 0 && maxHops in 1..32 && maxLifetimeMillis > 0)
        require((directory.exists() || directory.mkdirs()) && directory.isDirectory)
    }

    data class Delivery(
        val id: String,
        val recipientId: String,
        val envelope: ByteArray,
        val expiresAtMillis: Long,
        val hopCount: Int,
        val attempts: Int,
        val nextAttemptAtMillis: Long,
    )

    @Synchronized
    fun enqueue(
        recipientId: String,
        opaqueEnvelope: ByteArray,
        expiresAtMillis: Long,
        hopCount: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        require(recipientId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(opaqueEnvelope.isNotEmpty() && opaqueEnvelope.size <= maxEnvelopeBytes)
        require(hopCount in 0 until maxHops)
        require(expiresAtMillis > nowMillis && expiresAtMillis - nowMillis <= maxLifetimeMillis)
        discardExpired(nowMillis)
        val digest = sha256(opaqueEnvelope)
        metadataFiles().firstOrNull {
            val p = read(it)
            p.getProperty("recipient") == recipientId && p.getProperty("digest") == digest
        }?.let { return it.name.removeSuffix(".properties") }
        require(usedBytes() + opaqueEnvelope.size <= quotaBytes) { "Relay queue quota is full." }
        val id = UUID.randomUUID().toString()
        val payload = File(directory, "$id.envelope")
        val meta = File(directory, "$id.properties")
        val tempPayload = File(directory, "$id.envelope.tmp")
        val tempMeta = File(directory, "$id.properties.tmp")
        try {
            tempPayload.writeBytes(opaqueEnvelope)
            check(tempPayload.renameTo(payload))
            val p = Properties().apply {
                setProperty("id", id)
                setProperty("recipient", recipientId)
                setProperty("expires", expiresAtMillis.toString())
                setProperty("hop", hopCount.toString())
                setProperty("attempts", "0")
                setProperty("next", nowMillis.toString())
                setProperty("digest", digest)
            }
            tempMeta.outputStream().use { p.store(it, "BQ Sqwad opaque relay queue") }
            check(tempMeta.renameTo(meta))
            return id
        } catch (failure: Throwable) {
            tempPayload.delete(); payload.delete(); tempMeta.delete(); meta.delete()
            throw failure
        }
    }

    @Synchronized
    fun nextEligible(connectedTrustedHopIds: Set<String>, nowMillis: Long = System.currentTimeMillis()): Delivery? {
        discardExpired(nowMillis)
        return metadataFiles().asSequence()
            .mapNotNull { meta ->
                val p = read(meta)
                val recipient = p.getProperty("recipient") ?: return@mapNotNull null
                val next = p.getProperty("next")?.toLongOrNull() ?: Long.MAX_VALUE
                val hop = p.getProperty("hop")?.toIntOrNull() ?: maxHops
                if (next > nowMillis || hop >= maxHops || connectedTrustedHopIds.isEmpty()) return@mapNotNull null
                val bytes = File(directory, meta.name.removeSuffix(".properties") + ".envelope").takeIf { it.isFile }?.readBytes()
                    ?: return@mapNotNull null
                if (p.getProperty("digest") != sha256(bytes)) return@mapNotNull null
                Delivery(
                    id = p.getProperty("id") ?: return@mapNotNull null,
                    recipientId = recipient,
                    envelope = bytes,
                    expiresAtMillis = p.getProperty("expires")?.toLongOrNull() ?: 0L,
                    hopCount = hop,
                    attempts = p.getProperty("attempts")?.toIntOrNull() ?: 0,
                    nextAttemptAtMillis = next,
                )
            }.minByOrNull { it.nextAttemptAtMillis }
    }

    /** Call only after an authenticated next-hop attempt has actually been made. */
    @Synchronized
    fun recordAttempt(id: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val meta = metadata(id)
        if (!meta.isFile) return false
        val p = read(meta)
        val attempts = (p.getProperty("attempts")?.toIntOrNull() ?: 0) + 1
        val backoff = (5_000L shl (attempts - 1).coerceIn(0, 10)).coerceAtMost(60L * 60L * 1000L)
        p.setProperty("attempts", attempts.toString())
        p.setProperty("next", (nowMillis + backoff).toString())
        writeAtomic(meta, p)
        return true
    }

    /** A package is deleted only after the caller validates the recipient/next-hop ACK. */
    @Synchronized
    fun acknowledge(id: String, authenticatedAck: Boolean): Boolean {
        if (!authenticatedAck) return false
        val meta = metadata(id)
        val payload = envelope(id)
        if (!meta.exists() && !payload.exists()) return false
        val payloadOk = !payload.exists() || payload.delete()
        val metaOk = !meta.exists() || meta.delete()
        return payloadOk && metaOk
    }

    @Synchronized
    fun advanceHop(id: String, authenticatedForwardingAck: Boolean): Boolean {
        if (!authenticatedForwardingAck) return false
        val meta = metadata(id)
        if (!meta.isFile) return false
        val p = read(meta)
        val hop = p.getProperty("hop")?.toIntOrNull() ?: return false
        if (hop + 1 >= maxHops) return false
        p.setProperty("hop", (hop + 1).toString())
        p.setProperty("attempts", "0")
        p.setProperty("next", System.currentTimeMillis().toString())
        writeAtomic(meta, p)
        return true
    }

    @Synchronized
    fun discardExpired(nowMillis: Long = System.currentTimeMillis()): Int {
        var removed = 0
        metadataFiles().forEach { meta ->
            val expiry = read(meta).getProperty("expires")?.toLongOrNull() ?: 0L
            if (expiry <= nowMillis) {
                val payload = envelope(meta.name.removeSuffix(".properties"))
                if (meta.delete()) removed++
                payload.delete()
            }
        }
        directory.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        return removed
    }

    @Synchronized
    fun pendingCount(nowMillis: Long = System.currentTimeMillis()): Int {
        discardExpired(nowMillis)
        return metadataFiles().count()
    }

    private fun metadata(id: String): File {
        require(id.matches(Regex("[A-Fa-f0-9-]{36}")))
        return File(directory, "$id.properties")
    }
    private fun envelope(id: String) = File(directory, "$id.envelope")
    private fun metadataFiles() = directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".properties") }
    private fun usedBytes() = directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".envelope") }.sumOf { it.length() }
    private fun read(file: File) = Properties().also { p -> file.inputStream().use(p::load) }
    private fun writeAtomic(file: File, p: Properties) {
        val temp = File(directory, file.name + ".tmp")
        temp.outputStream().use { p.store(it, "BQ Sqwad relay state") }
        check(temp.renameTo(file) || (file.delete() && temp.renameTo(file)))
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
