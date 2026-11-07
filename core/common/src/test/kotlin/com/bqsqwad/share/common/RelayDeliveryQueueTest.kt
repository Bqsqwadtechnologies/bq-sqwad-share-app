package com.bqsqwad.share.common

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RelayDeliveryQueueTest {
    @Test fun persistsOpaqueDeliveryAndRequiresAuthenticatedAcknowledgement() {
        val dir = Files.createTempDirectory("bq-relay-queue").toFile()
        val queue = RelayDeliveryQueue(dir, quotaBytes = 1024, maxEnvelopeBytes = 512)
        val bytes = byteArrayOf(3, 1, 4, 1, 5)
        val id = queue.enqueue("recipient_123", bytes, 20_000L, nowMillis = 10_000L)
        assertEquals(bytes.toList(), queue.nextEligible(setOf("trusted-hop"), 10_000L)!!.envelope.toList())
        assertFalse(queue.acknowledge(id, authenticatedAck = false))
        assertEquals(1, queue.pendingCount(10_000L))
        assertTrue(queue.acknowledge(id, authenticatedAck = true))
        assertEquals(0, queue.pendingCount(10_000L))
        dir.deleteRecursively()
    }

    @Test fun retriesUseBackoffAndExpiredPackagesAreRemoved() {
        val dir = Files.createTempDirectory("bq-relay-backoff").toFile()
        val queue = RelayDeliveryQueue(dir, quotaBytes = 1024, maxEnvelopeBytes = 512)
        val id = queue.enqueue("recipient", byteArrayOf(8, 9), 30_000L, nowMillis = 1_000L)
        assertNotNull(queue.nextEligible(setOf("trusted-hop"), 1_000L))
        assertTrue(queue.recordAttempt(id, 1_000L))
        assertNull(queue.nextEligible(setOf("trusted-hop"), 2_000L))
        assertNotNull(queue.nextEligible(setOf("trusted-hop"), 6_000L))
        assertEquals(1, queue.discardExpired(30_000L))
        assertEquals(0, queue.pendingCount(30_000L))
        dir.deleteRecursively()
    }

    @Test fun rejectsHopOverflowAndUntrustedNoHop() {
        val dir = Files.createTempDirectory("bq-relay-hop").toFile()
        val queue = RelayDeliveryQueue(dir, maxHops = 2)
        assertTrue(runCatching { queue.enqueue("recipient", byteArrayOf(1), 20_000L, hopCount = 2, nowMillis = 1_000L) }.isFailure)
        val id = queue.enqueue("recipient", byteArrayOf(2), 20_000L, nowMillis = 1_000L)
        assertNull(queue.nextEligible(emptySet(), 1_000L))
        assertFalse(queue.advanceHop(id, authenticatedForwardingAck = false))
        dir.deleteRecursively()
    }
}
