package com.bqsqwad.share.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class OfflineRelayStoreTest {
    @Test
    fun storesOpaqueChunksAndOnlyDeletesAfterVerifiedAcknowledgement() {
        val root = Files.createTempDirectory("bq-relay-test").toFile()
        val store = OfflineRelayStore(root, quotaBytes = 1024, maxChunkBytes = 512)
        val encrypted = byteArrayOf(9, 4, 2, 8, 1)
        val id = store.enqueue("recipient_123", encrypted, 20_000L, nowMillis = 10_000L)

        assertArrayEquals(encrypted, store.readOpaqueChunk(id))
        assertFalse(store.acknowledge(id, verifiedNextHop = false))
        assertArrayEquals(encrypted, store.readOpaqueChunk(id))
        assertTrue(store.acknowledge(id, verifiedNextHop = true))
        assertNull(store.readOpaqueChunk(id))
        root.deleteRecursively()
    }

    @Test
    fun expiresPackagesAndSuppressesDuplicates() {
        val root = Files.createTempDirectory("bq-relay-expiry").toFile()
        val store = OfflineRelayStore(root, quotaBytes = 1024, maxChunkBytes = 512)
        val bytes = byteArrayOf(1, 2, 3)
        val first = store.enqueue("recipient", bytes, 15_000L, nowMillis = 10_000L)
        val duplicate = store.enqueue("recipient", bytes, 16_000L, nowMillis = 10_000L)

        assertEquals(first, duplicate)
        assertEquals(1, store.pendingCount(nowMillis = 10_000L))
        assertEquals(1, store.discardExpired(nowMillis = 15_000L))
        assertEquals(0, store.pendingCount(nowMillis = 15_000L))
        root.deleteRecursively()
    }

    @Test
    fun enforcesQuotaAndRejectsUnsafeRoutes() {
        val root = Files.createTempDirectory("bq-relay-quota").toFile()
        val store = OfflineRelayStore(root, quotaBytes = 4, maxChunkBytes = 4)
        store.enqueue("recipient", byteArrayOf(1, 2, 3, 4), 20_000L, nowMillis = 10_000L)
        assertTrue(runCatching {
            store.enqueue("other", byteArrayOf(5), 20_000L, nowMillis = 10_000L)
        }.isFailure)
        assertTrue(runCatching {
            store.enqueue("../bad", byteArrayOf(1), 20_000L, nowMillis = 10_000L)
        }.isFailure)
        root.deleteRecursively()
    }
}
