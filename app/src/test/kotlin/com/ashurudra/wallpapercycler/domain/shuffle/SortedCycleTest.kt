package com.ashurudra.wallpapercycler.domain.shuffle

import org.junit.Assert.assertEquals
import org.junit.Test

class SortedCycleTest {

    @Test
    fun `reconcileIndex finds the current id's position`() {
        val result = SortedCycle.reconcileIndex("b", listOf("a", "b", "c"))

        assertEquals(1, result)
    }

    @Test
    fun `reconcileIndex returns zero when current id is absent`() {
        val result = SortedCycle.reconcileIndex("z", listOf("a", "b", "c"))

        assertEquals(0, result)
    }

    @Test
    fun `reconcileIndex returns zero when current id is null`() {
        val result = SortedCycle.reconcileIndex(null, listOf("a", "b", "c"))

        assertEquals(0, result)
    }

    @Test
    fun `reconcileIndex returns zero for an empty list`() {
        val result = SortedCycle.reconcileIndex("a", emptyList())

        assertEquals(0, result)
    }

    @Test
    fun `nextIndex advances by one`() {
        val result = SortedCycle.nextIndex(1, 4)

        assertEquals(2, result)
    }

    @Test
    fun `nextIndex wraps from the last index back to zero`() {
        val result = SortedCycle.nextIndex(3, 4)

        assertEquals(0, result)
    }

    @Test
    fun `nextIndex on a size-one list stays at zero without throwing`() {
        val result = SortedCycle.nextIndex(0, 1)

        assertEquals(0, result)
    }

    @Test
    fun `nextIndex on an empty list returns zero without throwing`() {
        val result = SortedCycle.nextIndex(0, 0)

        assertEquals(0, result)
    }

    @Test
    fun `previousIndex steps back by one`() {
        val result = SortedCycle.previousIndex(2, 4)

        assertEquals(1, result)
    }

    @Test
    fun `previousIndex wraps from index zero back to the last index`() {
        val result = SortedCycle.previousIndex(0, 4)

        assertEquals(3, result)
    }

    @Test
    fun `previousIndex on a size-one list stays at zero without throwing`() {
        val result = SortedCycle.previousIndex(0, 1)

        assertEquals(0, result)
    }

    @Test
    fun `previousIndex on an empty list returns zero without throwing`() {
        val result = SortedCycle.previousIndex(0, 0)

        assertEquals(0, result)
    }
}
