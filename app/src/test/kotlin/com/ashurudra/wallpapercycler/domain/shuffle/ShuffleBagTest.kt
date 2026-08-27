package com.ashurudra.wallpapercycler.domain.shuffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleBagTest {

    @Test
    fun `create produces a permutation with index zero`() {
        val files = listOf("a", "b", "c", "d")
        val bag = ShuffleBag.create(files, seed = 11L)

        assertEquals(0, bag.index)
        assertEquals(files.size, bag.sequence.size)
        assertEquals(files.toSet(), bag.sequence.toSet())
    }

    @Test
    fun `create handles empty file list`() {
        val bag = ShuffleBag.create(emptyList(), seed = 1L)

        assertEquals(emptyList<String>(), bag.sequence)
        assertEquals(0, bag.index)
    }

    @Test
    fun `exhausts folder without repeating within a cycle over 1000 calls`() {
        val files = (1..7).map { "img$it" }
        var bag = ShuffleBag.create(files, seed = 42L)
        val seen = mutableSetOf(bag.current)

        for (i in 0 until 1000) {
            val nextBag = bag.next(newSeed = 1000L + i)
            if (nextBag.index == 0) {
                assertEquals(files.size, seen.size)
                seen.clear()
            } else {
                assertFalse(seen.contains(nextBag.current))
            }
            seen.add(nextBag.current)
            bag = nextBag
        }
    }

    @Test
    fun `never repeats across a reshuffle boundary over many cycles`() {
        val files = (1..5).map { "img$it" }
        var bag = ShuffleBag.create(files, seed = 7L)
        var seedCounter = 0L

        repeat(200) {
            var previousLast: String
            do {
                previousLast = bag.sequence.last()
                bag = bag.next(newSeed = seedCounter++)
            } while (bag.index != 0)
            assertNotEquals(previousLast, bag.sequence[0])
        }
    }

    @Test
    fun `single image bag repeats without crashing or looping forever`() {
        var bag = ShuffleBag.create(listOf("only"), seed = 1L)

        repeat(50) { i ->
            bag = bag.next(newSeed = i.toLong())
            assertEquals("only", bag.current)
            assertEquals(0, bag.index)
        }
    }

    @Test
    fun `previous returns null at index zero`() {
        val bag = ShuffleBag.create(listOf("a", "b", "c"), seed = 3L)

        assertNull(bag.previous())
    }

    @Test
    fun `previous steps back to the prior index and image`() {
        val bag = ShuffleBag.create(listOf("a", "b", "c"), seed = 3L)
        val advanced = bag.next(newSeed = 99L)

        val stepped = advanced.previous()

        assertEquals(bag.index, stepped!!.index)
        assertEquals(bag.current, stepped.current)
        assertEquals(bag.sequence, stepped.sequence)
    }

    @Test
    fun `reconcile removes id from unplayed remainder`() {
        val bag = ShuffleBag(sequence = listOf("A", "B", "C", "D", "E"), index = 2, seed = 1L)

        val result = bag.reconcile(listOf("A", "B", "C", "E"))

        assertEquals(listOf("A", "B", "C", "E"), result.sequence)
        assertEquals(2, result.index)
        assertEquals("C", result.current)
    }

    @Test
    fun `reconcile removes id from played prefix and shifts index`() {
        val bag = ShuffleBag(sequence = listOf("A", "B", "C", "D", "E"), index = 2, seed = 1L)

        val result = bag.reconcile(listOf("B", "C", "D", "E"))

        assertEquals(listOf("B", "C", "D", "E"), result.sequence)
        assertEquals(1, result.index)
        assertEquals("C", result.current)
    }

    @Test
    fun `reconcile advances past the current id when it is removed`() {
        val bag = ShuffleBag(sequence = listOf("A", "B", "C", "D", "E"), index = 2, seed = 1L)

        val result = bag.reconcile(listOf("A", "B", "D", "E"))

        assertEquals(listOf("A", "B", "D", "E"), result.sequence)
        assertEquals(2, result.index)
        assertEquals("D", result.current)
    }

    @Test
    fun `reconcile inserts a new id only into the unplayed remainder`() {
        val bag = ShuffleBag(sequence = listOf("A", "B", "C", "D", "E"), index = 2, seed = 1L)

        val result = bag.reconcile(listOf("A", "B", "C", "D", "E", "F"))

        assertEquals(listOf("A", "B", "C"), result.sequence.subList(0, 3))
        assertEquals(2, result.index)
        assertEquals("C", result.current)
        assertEquals(6, result.sequence.size)
        assertTrue(result.sequence.subList(3, result.sequence.size).contains("F"))
    }

    @Test
    fun `reconcile falls back to a fresh shuffle when every file is replaced`() {
        val bag = ShuffleBag(sequence = listOf("A", "B", "C"), index = 1, seed = 5L)
        val newFiles = listOf("X", "Y", "Z")

        val result = bag.reconcile(newFiles)

        assertEquals(ShuffleBag.create(newFiles, seed = 5L), result)
    }

    @Test
    fun `reconcile returns an empty bag when everything is removed and nothing replaces it`() {
        val bag = ShuffleBag(sequence = listOf("A", "B", "C"), index = 1, seed = 5L)

        val result = bag.reconcile(emptyList())

        assertEquals(emptyList<String>(), result.sequence)
        assertEquals(0, result.index)
    }

    @Test
    fun `reconcile reshuffles remaining files when current is removed and nothing is left unplayed`() {
        val bag = ShuffleBag(sequence = listOf("A", "B", "C", "D", "E"), index = 4, seed = 9L)
        val remaining = listOf("A", "B", "C", "D")

        val result = bag.reconcile(remaining)

        assertEquals(ShuffleBag.create(remaining, seed = 9L), result)
    }
}
