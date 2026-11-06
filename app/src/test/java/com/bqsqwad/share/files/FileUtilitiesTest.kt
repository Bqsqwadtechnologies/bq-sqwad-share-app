package com.bqsqwad.share.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileUtilitiesTest {
    @Test
    fun sha256ReturnsStableLowercaseDigest() {
        val directory = Files.createTempDirectory("bq-share-hash").toFile()
        val file = File(directory, "sample.txt").apply { writeText("abc") }
        try {
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                FileUtilities.sha256(file),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun zipRoundTripPreservesFileContentsAndAvoidsNameCollisions() {
        val directory = Files.createTempDirectory("bq-share-zip").toFile()
        val first = File(directory, "notes.txt").apply { writeText("first") }
        val secondDirectory = File(directory, "other").apply { mkdirs() }
        val second = File(secondDirectory, "notes.txt").apply { writeText("second") }
        val archive = File(directory, "bundle.zip")
        val destination = File(directory, "extracted")
        try {
            FileUtilities.createZip(listOf(first, second), archive)
            val files = FileUtilities.extractZip(archive, destination)
            assertEquals(2, files.size)
            assertEquals(setOf("notes.txt", "notes (2).txt"), files.map { it.name }.toSet())
            assertEquals(setOf("first", "second"), files.map { it.readText() }.toSet())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun extractionRejectsParentTraversal() {
        val directory = Files.createTempDirectory("bq-share-zip-traversal").toFile()
        val archive = File(directory, "unsafe.zip")
        val destination = File(directory, "out")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../escaped.txt"))
                zip.write("unsafe".toByteArray())
                zip.closeEntry()
            }
            val failure = runCatching { FileUtilities.extractZip(archive, destination) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertFalse(File(directory, "escaped.txt").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
