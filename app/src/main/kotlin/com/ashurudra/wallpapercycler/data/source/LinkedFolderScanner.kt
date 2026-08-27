package com.ashurudra.wallpapercycler.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Top-level only, no subfolder recursion — matches the "one folder" schedule design. */
class LinkedFolderScanner(
    private val context: Context,
    private val treeUri: Uri,
) : ImageSource {

    override suspend fun listImages(): List<ImageRef> {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        val results = mutableListOf<ImageRef>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeIndex)
                if (mime !in SUPPORTED_IMAGE_MIME_TYPES) continue
                val id = cursor.getString(idIndex)
                results += ImageRef(
                    id = id,
                    displayName = cursor.getString(nameIndex) ?: id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    lastModified = cursor.getLong(modifiedIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                )
            }
        }
        return results
    }
}
