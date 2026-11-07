package com.bqsqwad.share.common

/**
 * Finds a bounded route through explicitly trusted, currently reachable relay peers.
 * Identities are stable app identities, not Nearby endpoint IDs or display names.
 * A route is only a suggestion: the transport must authenticate the peer and
 * verify the signed recipient envelope before forwarding any bytes.
 */
object RelayRoutePlanner {
    data class Route(val destination: String, val hops: List<String>) {
        val firstHop: String get() = hops.first()
        val hopCount: Int get() = hops.size
    }

    fun findRoute(localIdentity: String, recipientIdentity: String, trustedAdjacency: Map<String, Set<String>>, maxHops: Int = 8): Route? {
        require(localIdentity.isNotBlank() && recipientIdentity.isNotBlank())
        require(maxHops in 1..32)
        if (localIdentity == recipientIdentity) return Route(recipientIdentity, emptyList())
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf(localIdentity)
        trustedAdjacency[localIdentity].orEmpty().sorted().forEach { if (it.isNotBlank() && visited.add(it)) queue.add(listOf(it)) }
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()
            if (path.size > maxHops) continue
            if (current == recipientIdentity) return Route(recipientIdentity, path)
            trustedAdjacency[current].orEmpty().sorted().forEach { if (it.isNotBlank() && visited.add(it)) queue.add(path + it) }
        }
        return null
    }
}