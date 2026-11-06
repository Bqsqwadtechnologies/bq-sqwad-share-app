package com.bqsqwad.share.files

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Safe archive and integrity helpers for user-selected files.
 *
 * Extraction is restricted to the chosen destination directory. Absolute paths,
 * parent traversal, symbolic-link entries and excessive expansion are rejected.
 */
object FileUtilities {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_ENTRIES = 100_000
    private const val MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024

    fun sha256(file: File): String {
        require(file.isFile && file.canRead()) { "A readable file is required." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun createZip(files: List<File>, destination: File): File {
        require(files.isNotEmpty()) { "Select at least one file." }
        val destinationCanonical = destination.canonicalFile
        require(files.none { it.canonicalFile == destinationCanonical }) {
            "The archive cannot include itself."
        }
        destination.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
            val usedNames = mutableSetOf<String>()
            files.forEach { file ->
                require(file.isFile && file.canRead()) { "A selected item is not a readable file." }
                val baseName = file.name.ifBlank { "file" }
                var entryName = baseName
                var suffix = 2
                while (!usedNames.add(entryName)) {
                    val dot = baseName.lastIndexOf('.')
                    entryName = if (dot > 0) {
                        baseName.substring(0, dot) + " ($suffix)" + baseName.substring(dot)
                    } else {
                        "$baseName ($suffix)"
                    }
                    suffix++
                }
                zip.putNextEntry(ZipEntry(entryName))
                BufferedInputStream(FileInputStream(file)).use { input ->
                    input.copyTo(zip, BUFFER_SIZE)
                }
                zip.closeEntry()
            }
        }
        return destination
    }

    fun extractZip(archive: File, destinationDirectory: File): List<File> {
        require(archive.isFile && archive.canRead()) { "A readable ZIP archive is required." }
        require(destinationDirectory.exists() || destinationDirectory.mkdirs()) {
            "The destination folder could not be created."
        }
        val root = destinationDirectory.canonicalFile
        val extracted = mutableListOf<File>()
        var totalBytes = 0L
        var entryCount = 0

        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRIES) { "The archive contains too many entries." }
                val name = entry.name.replace('\\', '/')
                require(name.isNotBlank() && !name.startsWith("/") && !name.startsWith("../")) {
                    "The archive contains an unsafe path."
                }
                val output = File(root, name).canonicalFile
                require(output.toPath().startsWith(root.toPath()) && output != root) {
                    "The archive contains an unsafe path."
                }
                if (entry.isDirectory) {
                    require(output.exists() || output.mkdirs()) { "A folder could not be created." }
                } else {
                    output.parentFile?.let { parent ->
                        require(parent.exists() || parent.mkdirs()) { "A folder could not be created." }
                    }
                    require(!output.exists()) { "An extracted file would overwrite an existing file: ${output.name}" }
                    output.outputStream().buffered(BUFFER_SIZE).use { target ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= MAX_UNCOMPRESSED_BYTES) {
                                "The archive expands beyond the 2 GB safety limit."
                            }
                            target.write(buffer, 0, count)
                        }
                    }
                    extracted += output
                }
                zip.closeEntry()
            }
        }
        return extracted
    }
}
