package org.futo.inputmethod.latin.uix.actions

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.permissions.PermissionsManager
import org.futo.inputmethod.latin.permissions.PermissionsUtil
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction

private fun requiredMediaPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyArray()
    }
}

private suspend fun ensureMediaPermission(context: Context): Boolean {
    val perms = requiredMediaPermissions()
    if (perms.isEmpty()) return true
    if (PermissionsUtil.checkAllPermissionsGranted(context, *perms)) return true

    return withContext(Dispatchers.Main) {
        PermissionsManager.get(context).requestPermissions(
            { _ -> },
            null,
            *perms
        )
        Toast.makeText(
            context,
            context.getString(R.string.action_insert_last_screenshot_no_permission),
            Toast.LENGTH_SHORT
        ).show()
        false
    }
}

private fun findLastScreenshot(context: Context): Pair<Uri, String>? {
    val contentResolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val projection = mutableListOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.MIME_TYPE
    )

    val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    if (hasRelativePath) {
        projection.add(MediaStore.Images.Media.RELATIVE_PATH)
    } else {
        @Suppress("DEPRECATION")
        projection.add(MediaStore.Images.Media.DATA)
    }

    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    contentResolver.query(
        collection,
        projection.toTypedArray(),
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val pathCol = if (hasRelativePath) {
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        }

        var checked = 0
        while (cursor.moveToNext() && checked < 50) {
            checked++
            val path = cursor.getString(pathCol) ?: ""
            if (!path.contains("screenshot", ignoreCase = true) &&
                !path.contains("screenshots", ignoreCase = true)
            ) {
                continue
            }

            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(collection, id)
            val mime = cursor.getString(mimeCol) ?: "image/*"
            return uri to mime
        }
    }
    return null
}

val InsertLastScreenshotAction = Action(
    icon = R.drawable.image,
    name = R.string.action_insert_last_screenshot_title,
    simplePressImpl = { manager: KeyboardManagerForAction, _ ->
        val context = manager.getContext()
        val scope = manager.getLifecycleScope()

        scope.launch {
            if (!ensureMediaPermission(context)) return@launch

            val result = withContext(Dispatchers.IO) {
                findLastScreenshot(context)
            }

            if (result == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.action_insert_last_screenshot_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val (uri, mime) = result

            withContext(Dispatchers.Main) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        val clip = ClipData.newUri(context.contentResolver, "Screenshot", uri)
                        clipboard.setPrimaryClip(clip)
                    }
                } catch (_: Exception) {
                }

                manager.typeUri(uri, listOf(mime.ifBlank { "image/*" }))
            }
        }
    },
    windowImpl = null
)
