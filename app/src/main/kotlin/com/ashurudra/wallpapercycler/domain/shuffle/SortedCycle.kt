package com.ashurudra.wallpapercycler.domain.shuffle

/**
 * Advance mechanism for the "shuffle OFF" case: a stable cyclic walk through a sorted id
 * list, with none of [ShuffleBag]'s reshuffle-avoid-repeat logic.
 */
object SortedCycle {

    fun reconcileIndex(currentId: String?, sortedIds: List<String>): Int {
        if (sortedIds.isEmpty() || currentId == null) return 0
        val found = sortedIds.indexOf(currentId)
        return if (found >= 0) found else 0
    }

    fun nextIndex(index: Int, size: Int): Int {
        if (size == 0) return 0
        return (index + 1) % size
    }

    fun previousIndex(index: Int, size: Int): Int {
        if (size == 0) return 0
        return (index - 1 + size) % size
    }
}
