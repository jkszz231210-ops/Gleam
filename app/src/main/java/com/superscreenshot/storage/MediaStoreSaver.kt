package com.superscreenshot.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object MediaStoreSaver {
    private const val TAG = "MediaStoreSaver"

    fun saveJpeg(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                saveJpegApi29WithRetry(context, bitmap, displayName)
            } else {
                saveJpegLegacyWithRetry(context, bitmap, displayName)
            }
        }.onFailure {
            Log.w(TAG, "saveJpeg failed: ${it.message}", it)
        }.getOrNull()
    }

    private fun saveJpegApi29WithRetry(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val primary = saveJpegApi29(context, bitmap, displayName, quality = 92)
        if (primary != null) return primary
        val retryName = displayName.replace(".jpg", "_retry.jpg", ignoreCase = true)
        return saveJpegApi29(context, bitmap, retryName, quality = 90)
    }

    private fun saveJpegApi29(context: Context, bitmap: Bitmap, displayName: String, quality: Int): Uri? {
        val albumFolder = context.getString(com.superscreenshot.R.string.album_folder_name)
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$albumFolder")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

        val resolver = context.contentResolver
        val uri =
            try {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            } catch (se: SecurityException) {
                Log.w(TAG, "insert denied, check storage permission / scoped storage", se)
                null
            } ?: return null

        try {
            resolver.openOutputStream(uri)?.use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os)) {
                    Log.w(TAG, "compress failed for $displayName")
                    return null
                }
            } ?: return null

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            Log.w(TAG, "write failed for $displayName", t)
            try {
                resolver.delete(uri, null, null)
            } catch (_: Throwable) {
            }
            return null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveJpegLegacyWithRetry(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val primary = saveJpegLegacy(context, bitmap, displayName, quality = 92)
        if (primary != null) return primary
        val retryName = displayName.replace(".jpg", "_retry.jpg", ignoreCase = true)
        return saveJpegLegacy(context, bitmap, retryName, quality = 90)
    }

    @Suppress("DEPRECATION")
    private fun saveJpegLegacy(context: Context, bitmap: Bitmap, displayName: String, quality: Int): Uri? {
        val albumFolder = context.getString(com.superscreenshot.R.string.album_folder_name)
        val dir =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                albumFolder,
            )
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, displayName)
        return try {
            FileOutputStream(file).use { fos ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)) {
                    Log.w(TAG, "compress failed for legacy $displayName")
                    return null
                }
            }
            // 让相册立即可见
            context.sendBroadcast(
                android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(file)
                },
            )
            Uri.fromFile(file)
        } catch (_: Throwable) {
            null
        }
    }
}


