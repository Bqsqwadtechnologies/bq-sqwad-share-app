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

/**
 * Durable receiver-side checkpoint store for chunked transfers.
 *
 * Each accepted chunk is fsynced before its committed offset is atomically recorded.
 * On restart, uncommitted tail bytes are discarded and the sender can resume at
 * [resumeOffset]. The transport must exchange RESUME/ACK frames and call appendChunk.
 */
class ResumableFileReceiver(private val root: File) {
    data class Offer(
        val transferId: String,
        val displayName: String,
        val totalBytes: Long,
        val sha256: ByteArray,
    )

    fun begin(offer: Offer): Long {
        validateOffer(offer)
        if (!root.exists()) check(root.mkdirs()) { "Unable to create transfer staging directory." }
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
            return saved.committedOffset
        }
        require(!partial.exists()) { "Untracked partial transfer file already exists." }
        writeMetadata(metadata, Saved(offer.transferId, safeName(offer.displayName), offer.totalBytes, offer.sha256.copyOf(), 0L))
        check(partial.createNewFile()) { "Unable to create partial transfer file." }
        return 0L
    }

    fun resumeOffset(transferId: String): Long {
        val saved = readMetadata(metadataFile(transferId))
        val partial = partialFile(transferId)
        require(partial.isFile && partial.length() >= saved.committedOffset) {
            "Transfer checkpoint is incomplete."
        }
        if (partial.length() > saved.committedOffset) {
            RandomAccessFile(partial, "rw").use { it.setLength(saved.committedOffset) }
        }
        return saved.committedOffset
    }

    fun appendChunk(transferId: String, offset: Long, bytes: ByteArray, expectedChunkSha256: ByteArray): Long {
        require(bytes.isNotEmpty() && bytes.size <= TransferProtocol.MAX_CHUNK) { "Invalid transfer chunk size." }
        require(expectedChunkSha256.size == 32) { "Invalid chunk checksum." }
        require(TransferProtocol.sha256(bytes).contentEquals(expectedChunkSha256)) { "Chunk checksum mismatch." }
        val metadata = metadataFile(transferId)
        val saved = readMetadata(metadata)
        val partial = partialFile(transferId)
        require(partial.isFile) { "Partial transfer file is missing." }
        require(partial.length() == saved.committedOffset) { "Partial file does not match its saved checkpoint." }
        require(offset == saved.committedOffset) { "Chunk offset does not match the next expected byte." }
        val next = Math.addExact(offset, bytes.size.toLong())
        require(next <= saved.totalBytes) { "Chunk exceeds the advertised file size." }
        RandomAccessFile(partial, "rw").use { output ->
            output.seek(offset)
            output.write(bytes)
            output.fd.sync()
        }
        writeMetadata(metadata, saved.copy(committedOffset = next))
        return next
    }

    fun complete(transferId: String): File {
        val metadata = metadataFile(transferId)
        val saved = readMetadata(metadata)
        val partial = partialFile(transferId)
        require(partial.isFile && partial.length() == saved.totalBytes && saved.committedOffset == saved.totalBytes) {
            "Transfer is not complete yet."
        }
        require(sha256(partial).contentEquals(saved.sha256)) { "Completed file failed SHA-256 verification." }
        val destination = File(root, uniqueName(saved.displayName))
        check(partial.renameTo(destination)) { "Unable to finalize received file." }
        check(metadata.delete()) { "File was received but its checkpoint could not be removed." }
        return destination
    }

    fun discard(transferId: String) {
        partialFile(transferId).delete()
        metadataFile(transferId).delete()
    }

    private data class Saved(
        val transferId: String,
        val displayName: String,
        val totalBytes: Long,
        val sha256: ByteArray,
        val committedOffset: Long,
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

    private fun partialFile(id: String) = File(root, "$id.part")
    private fun metadataFile(id: String) = File(root, "$id.meta")

    private fun writeMetadata(file: File, saved: Saved) {
        val temporary = File(root, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
            output.writeInt(MAGIC)
            output.writeUTF(saved.transferId)
            output.writeUTF(saved.displayName)
            output.writeLong(saved.totalBytes)
            output.write(saved.sha256)
            output.writeLong(saved.committedOffset)
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
            require(input.read() == -1) { "Invalid transfer checkpoint data." }
            require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && size >= 0L && offset in 0..size) {
                "Invalid transfer checkpoint values."
            }
            Saved(id, name, size, hash, offset)
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
        private const val MAGIC = 0x42515231 // BQR1
    }
}
