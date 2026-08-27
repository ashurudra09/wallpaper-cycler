package com.ashurudra.wallpapercycler.data.source

import android.content.Context
import android.content.Intent
import android.net.Uri

class UriPermissionManager(private val context: Context) {

    fun persist(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Grants can be revoked outside the app (e.g. the folder was un-shared in system settings). */
    fun isPersisted(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    fun release(uri: Uri) {
        context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
