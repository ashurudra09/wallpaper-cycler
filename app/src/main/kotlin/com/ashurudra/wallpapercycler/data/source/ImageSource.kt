package com.ashurudra.wallpapercycler.data.source

import android.content.Context
import android.net.Uri
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import java.io.File

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

/** The "shuffle OFF" sort order - a stable ordering for [SortedCycle][com.ashurudra.wallpapercycler.domain.shuffle.SortedCycle] to walk. */
fun List<ImageRef>.sortedFor(sortOrder: SortOrder): List<ImageRef> = when (sortOrder) {
    SortOrder.NAME_ASC -> sortedBy { it.displayName }
    SortOrder.NAME_DESC -> sortedByDescending { it.displayName }
    SortOrder.DATE_ASC -> sortedBy { it.lastModified }
    SortOrder.DATE_DESC -> sortedByDescending { it.lastModified }
}

fun ImageSourceConfig.toImageSource(context: Context): ImageSource = when (this) {
    is ImageSourceConfig.LinkedFolder -> LinkedFolderScanner(context, Uri.parse(treeUri))
    is ImageSourceConfig.ManagedSet -> ManagedSetScanner(File(context.filesDir, "sets/$setId"))
}
