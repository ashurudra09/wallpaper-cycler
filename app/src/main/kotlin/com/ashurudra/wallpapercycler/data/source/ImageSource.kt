package com.ashurudra.wallpapercycler.data.source

import android.net.Uri

data class ImageRef(
    val id: String,
    val displayName: String,
    val uri: Uri,
    val lastModified: Long,
    val sizeBytes: Long,
)

interface ImageSource {
    suspend fun listImages(): List<ImageRef>
}

/**
 * GIF is dropped entirely since its whole purpose is animation. WebP is allowed even
 * though an animated WebP can't be told apart from a static one without decoding every
 * file — it just decodes to its first frame later, same as any other still viewer.
 */
val SUPPORTED_IMAGE_MIME_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
    "image/heif",
    "image/bmp",
)

val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
