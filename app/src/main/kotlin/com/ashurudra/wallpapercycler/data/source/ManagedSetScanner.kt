package com.ashurudra.wallpapercycler.data.source

import android.net.Uri
import java.io.File

/**
 * Scans a gallery-imported set directory. Plain [File.listFiles] rather than SAF — no
 * IPC, no permission re-checks, no risk of a revoked grant, since this is app-private
 * storage the app fully owns.
 */
class ManagedSetScanner(private val setDir: File) : ImageSource {

    override suspend fun listImages(): List<ImageRef> {
        val files = setDir.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS }
            .map { file ->
                ImageRef(
                    id = file.name,
                    displayName = file.name,
                    uri = Uri.fromFile(file),
                    lastModified = file.lastModified(),
                    sizeBytes = file.length(),
                )
            }
    }
}
