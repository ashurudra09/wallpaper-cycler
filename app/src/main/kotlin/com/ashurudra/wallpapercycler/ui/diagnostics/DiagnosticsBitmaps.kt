package com.ashurudra.wallpapercycler.ui.diagnostics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Generates an unambiguous, unmistakably-labelled full-screen bitmap for the on-device
 * lock/home wallpaper spike — a solid fill plus large centered text, so a glance at the
 * screen tells you exactly which test produced it.
 */
object DiagnosticsBitmaps {

    fun labeled(width: Int, height: Int, backgroundColor: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = width / 10f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val timestampPaint = Paint(textPaint).apply {
            textSize = width / 24f
        }

        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText(label, centerX, centerY, textPaint)
        canvas.drawText(
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
            centerX,
            centerY + textPaint.textSize * 1.4f,
            timestampPaint,
        )
        return bitmap
    }
}
