package com.bqsqwad.share.common

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Durable receiver-side checkpoint store for chunked transfers.
 *
 * Chunks are acknowledged as they arrive, while disk checkpoints are fsynced at
 * 1 MiB boundaries (and at file completion). If the process restarts, uncommitted
 * tail bytes are truncated and the sender resumes from the last durable checkpoint.
 */
class ResumableFileReceiver(
    private val root: File,
    private val maxPendingBytes: Long = Long.MAX_VALUE,
    private val minFreeBytes: Long = DEFAULT_MIN_FREE_BYTES,
    private val checkpointTtlMillis: Long = DEFAULT_CHECKPOINT_TTL_MILLIS,
) {
    init {
        require(maxPendingBytes > 0L)
        require(minFreeBytes >= 0L)
        require(checkpointTtlMillis > 0L)
    }
    data class Offer(
        val transferId: String,
        val displayName: String,
        val totalBytes: Long,
        val sha256: ByteArray,
    )

    private data class ActiveSession(
        var saved: Saved,
        var nextOffset: Long,
        var committedOffset: Long,
    )

    private val activeSessions = ConcurrentHashMap<String, ActiveSession>()

    fun cleanupExpired(nowMillis: Long = System.currentTimeMillis()) {
        if (root.isDirectory) expireStale(nowMillis)
    }

    fun begin(offer: Offer, nowMillis: Long = System.currentTimeMillis()): Long {
        validateOffer(offer)
        if (!root.exists()) check(root.mkdirs()) { "Unable to create transfer staging directory." }
        require(root.isDirectory) { "Transfer staging path is not a directory." }
        val previous = activeSessions.remove(offer.transferId)
        if (previous != null) synchronized(previous) { Unit }
        expireStale(nowMillis)

        val metadata = metadataFile(offer.transferId)
        val partial = partialFile(offer.transferId)
        if (metadata.exists()) {
            val saved = readMetadata(metadata)
            require(saved.displayName == safeName(offer.displayName)) { "Transfer ID is already assigned to another file." }
            require(saved.totalBytes == offer.totalBytes && saved.sha256.contentEquals(offer.sha256)) {
                "The resumed transfer does not match the original file."
            }
            require(partial.isFile) { "Transfer checkpoint exists but its partial file is missing." }
            require(partial.length() >= saved.committedOffset) { "Partial file is shorter than its committed checkpoint." }
            RandomAccessFile(partial, "rw").use { it.setLength(saved.committedOffset) }
            val resumed = saved.copy(lastActivityAtMillis = nowMillis)
            writeMetadata(metadata, resumed)
            activeSessions[offer.transferId] = ActiveSession(resumed, resumed.committedOffset, resumed.committedOffset)
            return resumed.committedOffset
        }

        // Reserve space for unfinished transfers before accepting another offer.
        val reservedBytes = pendingReservedBytes()
        val quotaRemaining = (maxPendingBytes - reservedBytes).coerceAtLeast(0L)
        val diskRemaining = (root.usableSpace - minFreeBytes).coerceAtLeast(0L)
        val unreservedDiskRemaining = (diskRemaining - reservedBytes).coerceAtLeast(0L)
        require(offer.totalBytes <= minOf(quotaRemaining, unreservedDiskRemaining)) {
            "Not enough reserved storage for this transfer. Finish or cancel another pending transfer first."
        }

        // A crash before the first metadata checkpoint can leave an untracked .part file.
        // It contains no durable progress, so discard it and start this transfer cleanly.
        if (partial.exists()) check(partial.delete()) { "Unable to remove an untracked partial transfer file." }
        check(partial.createNewFile()) { "Unable to create partial transfer file." }
        val initial = Saved(
            offer.transferId,
            safeName(offer.displayName),
            offer.totalBytes,
            offer.sha256.copyOf(),
            0L,
            nowMillis,
            nowMillis,
        )
        try {
            writeMetadata(metadata, initial)
        } catch (failure: Throwable) {
            partial.delete()
            throw failure
        }
        activeSessions[offer.transferId] = ActiveSession(initial, 0L, 0L)
        return 0L
    }

    fun resumeOffset(transferId: String, nowMillis: Long = System.currentTimeMillis()): Long {
        validateTransferId(transferId)
        val previous = activeSessions.remove(transferId)
        if (previous != null) synchronized(previous) { Unit }
        expireStale(nowMillis)
        val metadata = metadataFile(transferId)
        val saved = readMetadata(metadata)
        val partial = partialFile(transferId)
        require(partial.isFile && partial.length() >= saved.committedOffset) {
            "Transfer checkpoint is incomplete."
        }
        if (partial.length() > saved.committedOffset) {
            RandomAccessFile(partial, "rw").use { it.setLength(saved.committedOffset) }
        }
        val resumed = saved.copy(lastActivityAtMillis = nowMillis)
        writeMetadata(metadata, resumed)
        activeSessions[transferId] = ActiveSession(resumed, resumed.committedOffset, resumed.committedOffset)
        return resumed.committedOffset
    }

    fun appendChunk(transferId: String, offset: Long, bytes: ByteArray, expectedChunkSha256: ByteArray): Long {
        validateTransferId(transferId)
        require(bytes.isNotEmpty() && bytes.size <= TransferProtocol.MAX_CHUNK) { "Invalid transfer chunk size." }
        require(expectedChunkSha256.size == 32) { "Invalid chunk checksum." }
        require(TransferProtocol.sha256(bytes).contentEquals(expectedChunkSha256)) { "Chunk checksum mismatch." }
        val session = activeSessions[transferId]
            ?: throw IllegalStateException("Receive session was not initialized. Send the transfer offer again.")
        synchronized(session) {
            val saved = session.saved
            val metadata = metadataFile(transferId)
            val partial = partialFile(transferId)
            require(partial.isFile) { "Partial transfer file is missing." }
            require(partial.length() == session.nextOffset) { "Partial file does not match its active offset." }
            require(offset == session.nextOffset) { "Chunk offset does not match the next expected byte." }
            val next = Math.addExact(offset, bytes.size.toLong())
            require(next <= saved.totalBytes) { "Chunk exceeds the advertised file size." }

            RandomAccessFile(partial, "rw").use { output ->
                output.seek(offset)
                output.write(bytes)
                if (next - session.committedOffset >= CHECKPOINT_INTERVAL_BYTES || next == saved.totalBytes) {
                    output.fd.sync()
                    val checkpoint = saved.copy(committedOffset = next, lastActivityAtMillis = System.currentTimeMillis())
                    writeMetadata(metadata, checkpoint)
                    session.saved = checkpoint
                    session.committedOffset = next
                } else {
                    session.saved = saved.copy(lastActivityAtMillis = System.currentTimeMillis())
                }
            }
            session.nextOffset = next
            return next
        }
    }

    fun complete(transferId: String): File {
        validateTransferId(transferId)
        val session = activeSessions[transferId]
            ?: throw IllegalStateException("Receive session was not initialized. Send the transfer offer again.")
        synchronized(session) {
            var saved = session.saved
            val metadata = metadataFile(transferId)
            val partial = partialFile(transferId)
            require(
                partial.isFile &&
                    partial.length() == saved.totalBytes &&
                    session.nextOffset == saved.totalBytes,
            ) { "Transfer is not complete yet." }

            if (session.committedOffset < saved.totalBytes) {
                RandomAccessFile(partial, "rw").use { it.fd.sync() }
                saved = saved.copy(committedOffset = saved.totalBytes, lastActivityAtMillis = System.currentTimeMillis())
                writeMetadata(metadata, saved)
                session.saved = saved
                session.committedOffset = saved.totalBytes
            }
            val durable = readMetadata(metadata)
            require(durable.committedOffset == durable.totalBytes) { "Final transfer checkpoint was not committed." }
            require(sha256(partial).contentEquals(durable.sha256)) { "Completed file failed SHA-256 verification." }
            val destination = File(root, uniqueName(durable.displayName))
            check(partial.renameTo(destination)) { "Unable to finalize received file." }
            metadata.delete()
            activeSessions.remove(transferId, session)
            return destination
        }
    }

    fun discard(transferId: String) {
        validateTransferId(transferId)
        val session = activeSessions.remove(transferId)
        if (session != null) synchronized(session) { Unit }
        partialFile(transferId).delete()
        metadataFile(transferId).delete()
    }

    private data class Saved(
        val transferId: String,
        val displayName: String,
        val totalBytes: Long,
        val sha256: ByteArray,
        val committedOffset: Long,
        val createdAtMillis: Long,
        val lastActivityAtMillis: Long,
    )

    private fun validateOffer(offer: Offer) {
        require(offer.transferId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid transfer ID." }
        require(offer.displayName.isNotBlank() && offer.displayName.length <= 4096) { "Invalid file name." }
        require(offer.totalBytes >= 0L) { "Invalid file size." }
        require(offer.sha256.size == 32) { "Invalid file checksum." }
    }

    private fun safeName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001f]"), "_")
            .take(180).ifBlank { "received-file" }

    private fun uniqueName(name: String): String {
        val candidate = File(root, name)
        if (!candidate.exists()) return name
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        var index = 1
        while (File(root, "$base ($index)$extension").exists()) index++
        return "$base ($index)$extension"
    }

    private fun validateTransferId(id: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid transfer ID." }
    }

    private fun partialFile(id: String): File {
        validateTransferId(id)
        return File(root, "$id.part")
    }

    private fun metadataFile(id: String): File {
        validateTransferId(id)
        return File(root, "$id.meta")
    }

    private fun writeMetadata(file: File, saved: Saved) {
        val temporary = File(root, file.name + ".tmp")
        FileOutputStream(temporary).use { raw ->
            val output = DataOutputStream(BufferedOutputStream(raw))
            output.writeInt(MAGIC)
            output.writeUTF(saved.transferId)
            output.writeUTF(saved.displayName)
            output.writeLong(saved.totalBytes)
            output.write(saved.sha256)
            output.writeLong(saved.committedOffset)
            output.writeLong(saved.createdAtMillis)
            output.writeLong(saved.lastActivityAtMillis)
            output.flush()
            raw.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun readMetadata(file: File): Saved {
        require(file.isFile) { "Transfer checkpoint was not found." }
        return DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            require(input.readInt() == MAGIC) { "Unsupported transfer checkpoint." }
            val id = input.readUTF()
            val name = input.readUTF()
            val size = input.readLong()
            val hash = ByteArray(32).also(input::readFully)
            val offset = input.readLong()
            val timestamps = when (input.available()) {
                0 -> System.currentTimeMillis() to System.currentTimeMillis()
                16 -> input.readLong() to input.readLong()
                else -> error("Invalid transfer checkpoint timestamps.")
            }
            require(input.read() == -1) { "Invalid transfer checkpoint data." }
            require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && size >= 0L && offset in 0..size) {
                "Invalid transfer checkpoint values."
            }
            require(timestamps.first > 0L && timestamps.second >= timestamps.first) {
                "Invalid transfer checkpoint timestamps."
            }
            Saved(id, name, size, hash, offset, timestamps.first, timestamps.second)
        }
    }

    private fun metadataFiles(): List<File> =
        root.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".meta") }

    private fun pendingReservedBytes(): Long =
        metadataFiles().sumOf { metadata ->
            val saved = runCatching { readMetadata(metadata) }.getOrNull() ?: return@sumOf 0L
            val partial = partialFile(saved.transferId)
            (saved.totalBytes - partial.length().coerceAtMost(saved.totalBytes)).coerceAtLeast(0L)
        }

    private fun expireStale(nowMillis: Long) {
        metadataFiles().forEach { metadata ->
            val saved = runCatching { readMetadata(metadata) }.getOrNull()
            val id = saved?.transferId ?: metadata.name.removeSuffix(".meta")
            if (activeSessions.containsKey(id)) return@forEach
            if (saved == null || nowMillis - saved.lastActivityAtMillis >= checkpointTtlMillis) {
                partialFile(id).delete()
                metadata.delete()
            }
        }
        root.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".tmp") }.forEach { temporary ->
            if (nowMillis - temporary.lastModified() >= checkpointTtlMillis) temporary.delete()
        }
        root.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".part") }.forEach { partial ->
            val id = partial.name.removeSuffix(".part")
            if (!metadataFile(id).exists() && !activeSessions.containsKey(id)) partial.delete()
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    companion object {
        const val DEFAULT_MIN_FREE_BYTES = 128L * 1024L * 1024L
        const val DEFAULT_CHECKPOINT_TTL_MILLIS = 30L * 24L * 60L * 60L * 1000L
        private const val MAGIC = 0x42515231 // BQR1
        private const val CHECKPOINT_INTERVAL_BYTES = 1024L * 1024L
    }
}
