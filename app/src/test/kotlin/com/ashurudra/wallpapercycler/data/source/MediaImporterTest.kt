package com.ashurudra.wallpapercycler.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaImporterTest {

    @Test
    fun dedupeKey_sameBytesAndSize_produceTheSameKey() {
        val bytes = "hello world".toByteArray()
        val key1 = MediaImporter.dedupeKey(bytes, 12345L)
        val key2 = MediaImporter.dedupeKey(bytes.copyOf(), 12345L)
        assertEquals(key1, key2)
    }

    @Test
    fun dedupeKey_differentBytes_produceDifferentKeys() {
        val key1 = MediaImporter.dedupeKey("hello world".toByteArray(), 100L)
        val key2 = MediaImporter.dedupeKey("hello there".toByteArray(), 100L)
        assertNotEquals(key1, key2)
    }

    @Test
    fun dedupeKey_sameBytesDifferentSize_produceDifferentKeys() {
        val bytes = "identical sample bytes".toByteArray()
        val key1 = MediaImporter.dedupeKey(bytes, 1000L)
        val key2 = MediaImporter.dedupeKey(bytes, 2000L)
        assertNotEquals(key1, key2)
    }

    @Test
    fun dedupeKey_emptySample_doesNotThrow() {
        MediaImporter.dedupeKey(ByteArray(0), 0L)
    }

    @Test
    fun resolveCollision_noExistingMatch_returnsNameUnchanged() {
        val result = MediaImporter.resolveCollision("sunset.jpg", setOf("other.jpg"))
        assertEquals("sunset.jpg", result)
    }

    @Test
    fun resolveCollision_singleCollision_appendsDashOne() {
        val result = MediaImporter.resolveCollision("sunset.jpg", setOf("sunset.jpg"))
        assertEquals("sunset-1.jpg", result)
    }

    @Test
    fun resolveCollision_multipleCollisions_incrementsUntilFree() {
        val existing = setOf("sunset.jpg", "sunset-1.jpg", "sunset-2.jpg")
        val result = MediaImporter.resolveCollision("sunset.jpg", existing)
        assertEquals("sunset-3.jpg", result)
    }

    @Test
    fun resolveCollision_nameWithNoExtension_stillSuffixesCorrectly() {
        val result = MediaImporter.resolveCollision("IMG_001", setOf("IMG_001"))
        assertEquals("IMG_001-1", result)
    }

    @Test
    fun resolveCollision_nameWithMultipleDots_usesLastDotAsExtension() {
        val result = MediaImporter.resolveCollision("vacation.2026.jpg", setOf("vacation.2026.jpg"))
        assertEquals("vacation.2026-1.jpg", result)
    }

    @Test
    fun resolveCollision_dotOnlyAtStart_treatsWholeNameAsBase() {
        // A leading dot (dotfile-style name) shouldn't be mistaken for an extension separator.
        val result = MediaImporter.resolveCollision(".hidden", setOf(".hidden"))
        assertEquals(".hidden-1", result)
    }
}
