package com.ashurudra.wallpapercycler.wallpaper

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.ashurudra.wallpapercycler.domain.model.FitMode
import java.io.IOException

private data class ExifOrientation(val rotationDegrees: Int, val isFlipped: Boolean)

/**
 * Two-pass downsample decode (bounds-only pass to size the sample rate, then the real
 * decode), EXIF-correct orientation, then one of the three fit-mode compositions. No
 * network/UI concerns here — Coil stays reserved for list/preview thumbnails elsewhere.
 */
object WallpaperImageDecoder {

    fun decode(
        contentResolver: ContentResolver,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        fitMode: FitMode,
    ): Bitmap {
        val orientation = readOrientation(contentResolver, uri)
        val (storedWidth, storedHeight) = decodeBounds(contentResolver, uri)

        // BitmapFactory always decodes in stored pixel layout, ignoring EXIF — a 90/270
        // rotation means the stored width ends up mapping to the target's height (and
        // vice versa) once rotated, so swap which target dimension we sample against.
        val needsSwap = orientation.rotationDegrees == 90 || orientation.rotationDegrees == 270
        val compareWidth = if (needsSwap) targetHeight else targetWidth
        val compareHeight = if (needsSwap) targetWidth else targetHeight
        val sampleSize = computeInSampleSize(storedWidth, storedHeight, compareWidth, compareHeight)

        val rawBitmap = decodeSampled(contentResolver, uri, sampleSize)
        val orientedBitmap = applyOrientation(rawBitmap, orientation.rotationDegrees, orientation.isFlipped)

        val result = when (fitMode) {
            FitMode.FILL -> composeFill(orientedBitmap, targetWidth, targetHeight)
            FitMode.FIT_BLUR -> composeFit(orientedBitmap, targetWidth, targetHeight, useBlur = true)
            FitMode.FIT_SOLID -> composeFit(orientedBitmap, targetWidth, targetHeight, useBlur = false)
        }
        if (result !== orientedBitmap) orientedBitmap.recycle()
        return result
    }

    private fun readOrientation(contentResolver: ContentResolver, uri: Uri): ExifOrientation =
        contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            ExifOrientation(exif.rotationDegrees, exif.isFlipped)
        } ?: ExifOrientation(0, false)

    private fun decodeBounds(contentResolver: ContentResolver, uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return options.outWidth to options.outHeight
    }

    private fun computeInSampleSize(rawWidth: Int, rawHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        if (rawWidth > targetWidth || rawHeight > targetHeight) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while (halfWidth / sampleSize >= targetWidth && halfHeight / sampleSize >= targetHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun decodeSampled(contentResolver: ContentResolver, uri: Uri, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not decode $uri")
    }

    private fun applyOrientation(bitmap: Bitmap, rotationDegrees: Int, isFlipped: Boolean): Bitmap {
        if (rotationDegrees == 0 && !isFlipped) return bitmap
        val matrix = Matrix().apply {
            if (isFlipped) postScale(-1f, 1f)
            postRotate(rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Never touches [source] — recycling it is the caller's responsibility. */
    private fun composeFill(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val cropRect = CropGeometry.centerCropSourceRect(source.width, source.height, targetWidth, targetHeight)
        val cropped = Bitmap.createBitmap(source, cropRect.left, cropRect.top, cropRect.width, cropRect.height)
        if (cropped.width == targetWidth && cropped.height == targetHeight) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    /** Never touches [source] — recycling it is the caller's responsibility. */
    private fun composeFit(source: Bitmap, targetWidth: Int, targetHeight: Int, useBlur: Boolean): Bitmap {
        val destRect = CropGeometry.centerFitDestRect(source.width, source.height, targetWidth, targetHeight)
        val canvasBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)

        if (useBlur) {
            val backdrop = cheapBlurredFill(source, targetWidth, targetHeight)
            canvas.drawBitmap(backdrop, 0f, 0f, null)
            backdrop.recycle()
        } else {
            canvas.drawColor(averageColor(source))
        }

        val foreground = Bitmap.createScaledBitmap(source, destRect.width, destRect.height, true)
        canvas.drawBitmap(foreground, destRect.left.toFloat(), destRect.top.toFloat(), null)
        if (foreground !== source) foreground.recycle()

        return canvasBitmap
    }

    /** Downscale-then-upscale is a cheap, dependency-free blur — no RenderScript (deprecated) or minSdk-gated RenderEffect (API 31+) needed. */
    private fun cheapBlurredFill(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val filled = composeFill(source, targetWidth, targetHeight)
        val smallWidth = maxOf(1, (targetWidth * BLUR_DOWNSCALE).toInt())
        val smallHeight = maxOf(1, (targetHeight * BLUR_DOWNSCALE).toInt())
        val small = Bitmap.createScaledBitmap(filled, smallWidth, smallHeight, true)
        val blurred = Bitmap.createScaledBitmap(small, targetWidth, targetHeight, true)
        if (small !== filled) small.recycle()
        filled.recycle()
        return blurred
    }

    private fun averageColor(bitmap: Bitmap): Int {
        val sample = Bitmap.createScaledBitmap(bitmap, AVERAGE_SAMPLE_SIZE, AVERAGE_SAMPLE_SIZE, true)
        val pixels = IntArray(AVERAGE_SAMPLE_SIZE * AVERAGE_SAMPLE_SIZE)
        sample.getPixels(pixels, 0, AVERAGE_SAMPLE_SIZE, 0, 0, AVERAGE_SAMPLE_SIZE, AVERAGE_SAMPLE_SIZE)
        if (sample !== bitmap) sample.recycle()

        var red = 0L
        var green = 0L
        var blue = 0L
        for (pixel in pixels) {
            red += Color.red(pixel)
            green += Color.green(pixel)
            blue += Color.blue(pixel)
        }
        val count = pixels.size
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private const val BLUR_DOWNSCALE = 0.1f
    private const val AVERAGE_SAMPLE_SIZE = 16
}
