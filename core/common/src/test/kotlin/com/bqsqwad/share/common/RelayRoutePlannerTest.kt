package com.bqsqwad.share.common

import org.junit.Assert.*
import org.junit.Test

class RelayRoutePlannerTest {
    @Test fun choosesShortestPathAndStableFirstHop() {
        val graph = mapOf("origin" to setOf("z-hop", "a-hop"), "a-hop" to setOf("origin", "destination"), "z-hop" to setOf("origin", "destination"), "destination" to emptySet())
        val route = RelayRoutePlanner.findRoute("origin", "destination", graph)!!
        assertEquals(listOf("a-hop", "destination"), route.hops)
        assertEquals("a-hop", route.firstHop)
        assertEquals(2, route.hopCount)
    }
    @Test fun refusesUnreachableOrOverlongRoutes() {
        val graph = mapOf("a" to setOf("b"), "b" to setOf("a", "c"), "c" to setOf("b", "d"), "d" to emptySet())
        assertNull(RelayRoutePlanner.findRoute("a", "d", graph, maxHops = 2))
        assertNull(RelayRoutePlanner.findRoute("a", "missing", graph))
    }
    @Test fun localDestinationDoesNotForward() {
        val route = RelayRoutePlanner.findRoute("same", "same", emptyMap())!!
        assertTrue(route.hops.isEmpty())
    }
}