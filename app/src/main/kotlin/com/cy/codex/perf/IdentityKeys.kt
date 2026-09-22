package com.cy.codex.perf

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Allocates a stable, unique key per object instance.
 *
 * A lazy-layout item key must be unique among the items it covers and must not change while an
 * item lives in the list. Content-derived keys have neither property: two notices that are equal
 * collide ("Key ... was already used"), and an index is not identity because the front of a
 * bounded diagnostics list is evicted as new notices arrive.
 *
 * [IdentityHashMap] supplies the identity, so an instance keeps its key for as long as it is in
 * the list. Stale entries are dropped once the map outgrows the live set, which keeps the memory
 * proportional to the (bounded) list rather than to the number of notices a session has ever
 * shown.
 */
class IdentityKeys<T : Any> {
    private val ids = IdentityHashMap<T, Int>()
    private var nextId = 0

    /**
     * The key of [value], allocated on first sight.
     *
     * [live] is the collection being mapped; it is used only to prune entries for instances that
     * can no longer be keys of a visible item.
     */
    fun keyOf(value: T, live: Collection<T>): Int {
        if (ids.size > live.size * 2 + PruneSlack) prune(live)
        return ids.getOrPut(value) { nextId++ }
    }

    private fun prune(live: Collection<T>) {
        if (live.isEmpty()) {
            ids.clear()
            return
        }
        val identities: MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
        identities.addAll(live)
        val iterator = ids.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() !in identities) iterator.remove()
        }
    }

    private companion object {
        /** Growth allowed past the live set before a prune is worth its walk. */
        const val PruneSlack = 8
    }
}
