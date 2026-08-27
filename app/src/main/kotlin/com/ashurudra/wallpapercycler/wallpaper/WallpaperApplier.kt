package com.ashurudra.wallpapercycler.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget

class WallpaperApplier(private val context: Context) {

    fun apply(uri: Uri, fitMode: FitMode, targets: Set<ScreenTarget>) {
        require(targets.isNotEmpty()) { "apply() called with no targets" }

        val wallpaperManager = WallpaperManager.getInstance(context)
        val (targetWidth, targetHeight) = screenSize()

        // Per design: no parallax. WallpaperManager's own desiredMinimumWidth/Height is
        // commonly ~2x the real screen width (to support horizontal home-screen-swipe
        // panning) — using it as the crop target instead of the real screen size crops
        // and positions the image for a canvas we never actually render into, which is
        // what produced the zoomed-in/warped result. suggestDesiredDimensions tells the
        // system not to expect anything wider than what we're about to supply.
        wallpaperManager.suggestDesiredDimensions(targetWidth, targetHeight)

        val bitmap = WallpaperImageDecoder.decode(
            contentResolver = context.contentResolver,
            uri = uri,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            fitMode = fitMode,
        )

        val flags = targets.fold(0) { acc, target ->
            acc or when (target) {
                ScreenTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                ScreenTarget.LOCK -> WallpaperManager.FLAG_LOCK
            }
        }

        try {
            wallpaperManager.setBitmap(bitmap, null, true, flags)
        } finally {
            bitmap.recycle()
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
        return metrics.widthPixels to metrics.heightPixels
    }
}
