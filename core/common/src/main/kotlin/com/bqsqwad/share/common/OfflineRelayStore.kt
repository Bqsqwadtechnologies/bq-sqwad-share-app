package com.bqsqwad.share.common

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/**
 * Disk-backed staging for opaque, recipient-encrypted relay chunks.
 *
 * This class deliberately has no network transport and never decrypts, previews,
 * or indexes payload content. A caller must authenticate routing metadata and
 * verify a next-hop acknowledgement before passing verifiedNextHop=true.
 */
class OfflineRelayStore(
    private val directory: File,
    private val quotaBytes: Long = DEFAULT_QUOTA_BYTES,
    private val maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES,
    private val maxLifetimeMillis: Long = DEFAULT_MAX_LIFETIME_MILLIS,
) {
    init {
        require(quotaBytes > 0L)
        require(maxChunkBytes > 0)
        require(maxLifetimeMillis > 0L)
        require(directory.exists() || directory.mkdirs())
        require(directory.isDirectory)
    }

    @Synchronized
    fun enqueue(
        recipientRoute: String,
        opaqueEncryptedChunk: ByteArray,
        expiresAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        require(recipientRoute.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid recipient route." }
        require(opaqueEncryptedChunk.isNotEmpty() && opaqueEncryptedChunk.size <= maxChunkBytes) {
            "Relay chunk size is outside the allowed limit."
        }
        require(expiresAtMillis > nowMillis && expiresAtMillis - nowMillis <= maxLifetimeMillis) {
            "Relay package expiry is invalid."
        }
        discardExpired(nowMillis)
        val digest = sha256(opaqueEncryptedChunk)
        val duplicate = metadataFiles().firstOrNull { meta ->
            val values = readMetadata(meta)
            values.getProperty("recipient") == recipientRoute && values.getProperty("digest") == digest
        }
        if (duplicate != null) return duplicate.name.removeSuffix(".properties")
        require(usedBytes() + opaqueEncryptedChunk.size <= quotaBytes) { "Offline relay storage quota is full." }

        val id = UUID.randomUUID().toString()
        val payload = File(directory, "$id.chunk")
        val payloadTemp = File(directory, "$id.chunk.tmp")
        val metadata = File(directory, "$id.properties")
        val metadataTemp = File(directory, "$id.properties.tmp")
        try {
            payloadTemp.writeBytes(opaqueEncryptedChunk)
            check(payloadTemp.renameTo(payload)) { "Could not commit relay chunk." }
            val properties = Properties().apply {
                setProperty("id", id)
                setProperty("recipient", recipientRoute)
                setProperty("expiresAt", expiresAtMillis.toString())
                setProperty("size", opaqueEncryptedChunk.size.toString())
                setProperty("digest", digest)
            }
            metadataTemp.outputStream().use { properties.store(it, "Opaque offline relay metadata") }
            check(metadataTemp.renameTo(metadata)) { "Could not commit relay metadata." }
            return id
        } catch (failure: Throwable) {
            payloadTemp.delete()
            payload.delete()
            metadataTemp.delete()
            metadata.delete()
            throw failure
        }
    }

    @Synchronized
    fun readOpaqueChunk(id: String, nowMillis: Long = System.currentTimeMillis()): ByteArray? {
        val safeId = safeId(id)
        val metadata = File(directory, "$safeId.properties")
        val payload = File(directory, "$safeId.chunk")
        if (!metadata.isFile || !payload.isFile) return null
        val values = readMetadata(metadata)
        val expiry = values.getProperty("expiresAt")?.toLongOrNull() ?: 0L
        if (expiry <= nowMillis) {
            metadata.delete()
            payload.delete()
            return null
        }
        val bytes = payload.readBytes()
        if (values.getProperty("digest") != sha256(bytes)) return null
        return bytes
    }

    @Synchronized
    fun pendingCount(nowMillis: Long = System.currentTimeMillis()): Int {
        discardExpired(nowMillis)
        return metadataFiles().count { File(directory, it.name.removeSuffix(".properties") + ".chunk").isFile }
    }

    @Synchronized
    fun acknowledge(id: String, verifiedNextHop: Boolean): Boolean {
        if (!verifiedNextHop) return false
        val safeId = safeId(id)
        val metadata = File(directory, "$safeId.properties")
        val payload = File(directory, "$safeId.chunk")
        if (!metadata.exists() && !payload.exists()) return false
        val payloadRemoved = !payload.exists() || payload.delete()
        val metadataRemoved = !metadata.exists() || metadata.delete()
        return metadataRemoved && payloadRemoved
    }

    @Synchronized
    fun discardExpired(nowMillis: Long = System.currentTimeMillis()): Int {
        var removed = 0
        metadataFiles().forEach { metadata ->
            val expiry = readMetadata(metadata).getProperty("expiresAt")?.toLongOrNull() ?: 0L
            if (expiry <= nowMillis) {
                val id = metadata.name.removeSuffix(".properties")
                val payload = File(directory, "$id.chunk")
                if (metadata.delete()) removed++
                payload.delete()
            }
        }
        directory.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".chunk") }.forEach { chunk ->
            val id = chunk.name.removeSuffix(".chunk")
            if (!File(directory, "$id.properties").exists() && chunk.delete()) removed++
        }
        return removed
    }

    private fun usedBytes(): Long =
        directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".chunk") }.sumOf { it.length() }

    private fun metadataFiles(): List<File> =
        directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".properties") }

    private fun readMetadata(file: File): Properties =
        Properties().also { properties -> file.inputStream().use(properties::load) }

    private fun safeId(id: String): String {
        require(id.matches(Regex("[A-Fa-f0-9-]{36}"))) { "Invalid relay package ID." }
        return id
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_QUOTA_BYTES = 256L * 1024L * 1024L
        const val DEFAULT_MAX_CHUNK_BYTES = 8 * 1024 * 1024
        const val DEFAULT_MAX_LIFETIME_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
