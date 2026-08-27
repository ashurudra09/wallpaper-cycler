package com.ashurudra.wallpapercycler.wallpaper

import kotlin.math.roundToInt

/** Pure pixel rect — deliberately not android.graphics.Rect, so this stays JVM-testable with no device. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Pure geometry for the two ways a source image can be placed into a target frame — no
 * Bitmap, no Android imports, so it's fully unit-testable. Actual pixel compositing
 * (scaling, cropping, blur/solid backdrops) happens in WallpaperImageDecoder using the
 * rects computed here.
 */
object CropGeometry {

    /**
     * FILL mode: the source-space rect to crop so that scaling it up to exactly
     * targetWidth x targetHeight covers the frame with no distortion and no letterboxing.
     */
    fun centerCropSourceRect(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): PixelRect {
        require(srcWidth > 0 && srcHeight > 0 && targetWidth > 0 && targetHeight > 0)

        val scale = maxOf(targetWidth.toDouble() / srcWidth, targetHeight.toDouble() / srcHeight)
        val cropWidth = (targetWidth / scale).coerceAtMost(srcWidth.toDouble())
        val cropHeight = (targetHeight / scale).coerceAtMost(srcHeight.toDouble())
        val left = (srcWidth - cropWidth) / 2.0
        val top = (srcHeight - cropHeight) / 2.0

        return PixelRect(
            left = left.roundToInt(),
            top = top.roundToInt(),
            right = (left + cropWidth).roundToInt(),
            bottom = (top + cropHeight).roundToInt(),
        )
    }

    /**
     * FIT modes: the destination-space rect (within a targetWidth x targetHeight canvas)
     * where the whole, uniformly-scaled source image lands — the remaining canvas area is
     * the letterbox, filled by the caller (blurred copy or solid color).
     */
    fun centerFitDestRect(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): PixelRect {
        require(srcWidth > 0 && srcHeight > 0 && targetWidth > 0 && targetHeight > 0)

        val scale = minOf(targetWidth.toDouble() / srcWidth, targetHeight.toDouble() / srcHeight)
        val destWidth = srcWidth * scale
        val destHeight = srcHeight * scale
        val left = (targetWidth - destWidth) / 2.0
        val top = (targetHeight - destHeight) / 2.0

        return PixelRect(
            left = left.roundToInt(),
            top = top.roundToInt(),
            right = (left + destWidth).roundToInt(),
            bottom = (top + destHeight).roundToInt(),
        )
    }
}
