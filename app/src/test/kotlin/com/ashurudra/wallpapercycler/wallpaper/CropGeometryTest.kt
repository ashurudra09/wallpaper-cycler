package com.ashurudra.wallpapercycler.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val TARGET_W = 1080
private const val TARGET_H = 2400

class CropGeometryTest {

    // --- centerCropSourceRect (FILL) ---

    @Test
    fun centerCrop_landscapeSourceIntoPortraitTarget_cropsFullHeightNarrowWidth() {
        // A wide source has to lose most of its width to fill a tall target.
        val rect = CropGeometry.centerCropSourceRect(4000, 2000, TARGET_W, TARGET_H)
        assertWithinSourceBounds(rect, 4000, 2000)
        assertEquals(2000, rect.height) // full source height retained
        assertTrue("expected a narrow crop, was ${rect.width}", rect.width < 4000)
        assertCenteredHorizontally(rect, 4000)
    }

    @Test
    fun centerCrop_portraitSourceCloseToTargetAspect_cropsMinimally() {
        // Source is very slightly "wider" than the target (1080/2340 > 1080/2400), so FILL
        // scales to match height exactly and crops only a sliver off the width.
        val rect = CropGeometry.centerCropSourceRect(1080, 2340, TARGET_W, TARGET_H)
        assertWithinSourceBounds(rect, 1080, 2340)
        assertEquals(2340, rect.height)
        assertTrue(rect.width in 1000..1080)
        assertCenteredHorizontally(rect, 1080)
    }

    @Test
    fun centerCrop_squareSourceIntoPortraitTarget_cropsFullHeightNarrowWidth() {
        val rect = CropGeometry.centerCropSourceRect(1000, 1000, TARGET_W, TARGET_H)
        assertWithinSourceBounds(rect, 1000, 1000)
        assertEquals(1000, rect.height)
        assertTrue(rect.width < 1000)
        assertCenteredHorizontally(rect, 1000)
    }

    @Test
    fun centerCrop_extremeAspectSource_neverExceedsSourceBounds() {
        // A 40:1 panorama into a tall target — scale is dominated by height, crop width tiny.
        val rect = CropGeometry.centerCropSourceRect(4000, 100, TARGET_W, TARGET_H)
        assertWithinSourceBounds(rect, 4000, 100)
        assertEquals(100, rect.height)
        assertTrue(rect.width in 1..200)
    }

    @Test
    fun centerCrop_resultAspectMatchesTargetAspect() {
        val rect = CropGeometry.centerCropSourceRect(3000, 1500, TARGET_W, TARGET_H)
        val resultAspect = rect.width.toDouble() / rect.height
        val targetAspect = TARGET_W.toDouble() / TARGET_H
        assertTrue(abs(resultAspect - targetAspect) < 0.01)
    }

    // --- centerFitDestRect (FIT_BLUR / FIT_SOLID) ---

    @Test
    fun centerFit_landscapeSourceIntoPortraitTarget_lettersboxesTopAndBottom() {
        val rect = CropGeometry.centerFitDestRect(4000, 2000, TARGET_W, TARGET_H)
        assertWithinTargetBounds(rect, TARGET_W, TARGET_H)
        assertEquals(TARGET_W, rect.width) // width-constrained, so it spans the full target width
        assertTrue(rect.height < TARGET_H)
        assertCenteredVertically(rect, TARGET_H)
    }

    @Test
    fun centerFit_squareSourceIntoPortraitTarget_lettersboxesTopAndBottom() {
        val rect = CropGeometry.centerFitDestRect(1000, 1000, TARGET_W, TARGET_H)
        assertWithinTargetBounds(rect, TARGET_W, TARGET_H)
        assertEquals(TARGET_W, rect.width)
        assertTrue(rect.height < TARGET_H)
    }

    @Test
    fun centerFit_extremeAspectSource_producesThinSliver() {
        val rect = CropGeometry.centerFitDestRect(4000, 100, TARGET_W, TARGET_H)
        assertWithinTargetBounds(rect, TARGET_W, TARGET_H)
        assertEquals(TARGET_W, rect.width)
        assertTrue("expected a thin sliver, was ${rect.height}", rect.height in 1..50)
    }

    @Test
    fun centerFit_tallNarrowSourceIntoPortraitTarget_letterboxesLeftAndRight() {
        // A source taller/narrower than the target relative to its own aspect ratio.
        val rect = CropGeometry.centerFitDestRect(500, 2400, TARGET_W, TARGET_H)
        assertWithinTargetBounds(rect, TARGET_W, TARGET_H)
        assertEquals(TARGET_H, rect.height)
        assertTrue(rect.width < TARGET_W)
        assertCenteredHorizontallyInTarget(rect, TARGET_W)
    }

    @Test
    fun centerFit_resultAspectMatchesSourceAspect() {
        val rect = CropGeometry.centerFitDestRect(3000, 1500, TARGET_W, TARGET_H)
        val resultAspect = rect.width.toDouble() / rect.height
        val sourceAspect = 3000.0 / 1500
        assertTrue(abs(resultAspect - sourceAspect) < 0.01)
    }

    private fun assertWithinSourceBounds(rect: PixelRect, srcWidth: Int, srcHeight: Int) {
        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= srcWidth)
        assertTrue(rect.bottom <= srcHeight)
    }

    private fun assertWithinTargetBounds(rect: PixelRect, targetWidth: Int, targetHeight: Int) {
        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= targetWidth)
        assertTrue(rect.bottom <= targetHeight)
    }

    private fun assertCenteredHorizontally(rect: PixelRect, srcWidth: Int) {
        val leftMargin = rect.left
        val rightMargin = srcWidth - rect.right
        assertTrue(abs(leftMargin - rightMargin) <= 1)
    }

    private fun assertCenteredVertically(rect: PixelRect, targetHeight: Int) {
        val topMargin = rect.top
        val bottomMargin = targetHeight - rect.bottom
        assertTrue(abs(topMargin - bottomMargin) <= 1)
    }

    private fun assertCenteredHorizontallyInTarget(rect: PixelRect, targetWidth: Int) {
        val leftMargin = rect.left
        val rightMargin = targetWidth - rect.right
        assertTrue(abs(leftMargin - rightMargin) <= 1)
    }
}
