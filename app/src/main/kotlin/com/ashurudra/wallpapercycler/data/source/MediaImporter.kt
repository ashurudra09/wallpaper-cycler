package com.ashurudra.wallpapercycler.data.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

data class ImportResult(
    val imported: List<String>,
    val duplicates: List<String>,
    val failed: List<Pair<String, String>>,
)

private data class SourceMeta(val displayName: String, val size: Long)

/**
 * Copies gallery-picked photos byte-for-byte into a schedule's managed-set directory —
 * never re-encoded, so EXIF and original quality are preserved. Runs in repeatable
 * "Add photos" rounds into the same set (a single system picker round can be capped by
 * the device, commonly around 100), so re-picking an already-imported photo must be a
 * safe no-op rather than a duplicate.
 */
class MediaImporter(private val context: Context) {

    suspend fun importInto(setId: String, uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val setDir = File(context.filesDir, "sets/$setId").apply { mkdirs() }
        val existingFiles = setDir.listFiles()?.toList().orEmpty()
        val existingNames = existingFiles.mapTo(mutableSetOf()) { it.name }
        val existingKeys = existingFiles.mapNotNullTo(mutableSetOf()) { file ->
            runCatching { dedupeKey(file.inputStream().use(::readSample), file.length()) }.getOrNull()
        }

        val imported = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()

        for (uri in uris) {
            val meta = queryMeta(uri)
            var targetFile: File? = null
            try {
                val sample = context.contentResolver.openInputStream(uri)
                    ?.use(::readSample)
                    ?: throw IOException("Could not open input stream for $uri")
                val key = dedupeKey(sample, meta.size)
                if (key in existingKeys) {
                    duplicates += meta.displayName
                    continue
                }

                val targetName = resolveCollision(meta.displayName, existingNames)
                targetFile = File(setDir, targetName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Could not open input stream for $uri")

                existingNames += targetName
                existingKeys += key
                imported += targetName
            } catch (e: Exception) {
                targetFile?.takeIf { it.exists() }?.delete()
                failed += meta.displayName to (e.message ?: e.javaClass.simpleName)
            }
        }

        ImportResult(imported, duplicates, failed)
    }

    private fun queryMeta(uri: Uri): SourceMeta {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = (if (nameIndex >= 0) cursor.getString(nameIndex) else null)
                    ?: uri.lastPathSegment
                    ?: "image"
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                return SourceMeta(name, size)
            }
        }
        return SourceMeta(uri.lastPathSegment ?: "image", -1L)
    }

    /** Does not close [input] — closing is the caller's responsibility. */
    private fun readSample(input: InputStream, maxBytes: Int = DEDUPE_SAMPLE_BYTES): ByteArray {
        val sample = ByteArray(maxBytes)
        var offset = 0
        while (offset < sample.size) {
            val read = input.read(sample, offset, sample.size - offset)
            if (read == -1) break
            offset += read
        }
        return sample.copyOf(offset)
    }

    companion object {
        private const val DEDUPE_SAMPLE_BYTES = 64 * 1024

        /** Pure — SHA-256 of a leading byte sample plus total size; testable with no device. */
        fun dedupeKey(sampleBytes: ByteArray, totalSize: Long): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(sampleBytes)
            val hex = digest.joinToString("") { "%02x".format(it) }
            return "$hex:$totalSize"
        }

        /** Pure — appends "-1", "-2", ... before the extension until the name is free. */
        fun resolveCollision(desiredName: String, existingNames: Set<String>): String {
            if (desiredName !in existingNames) return desiredName
            val dotIndex = desiredName.lastIndexOf('.')
            val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
            val extension = if (dotIndex > 0) desiredName.substring(dotIndex) else ""
            var counter = 1
            var candidate: String
            do {
                candidate = "$base-$counter$extension"
                counter++
            } while (candidate in existingNames)
            return candidate
        }
    }
}
