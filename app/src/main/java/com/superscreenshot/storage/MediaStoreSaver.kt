package com.superscreenshot.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object MediaStoreSaver {

    fun saveJpeg(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= 29) {
            saveJpegApi29(context, bitmap, displayName)
        } else {
            saveJpegLegacy(context, bitmap, displayName)
        }
    }

    private fun saveJpegApi29(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SuperScreenshot")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)) return null
            } ?: return null

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (_: Throwable) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Throwable) {
            }
            return null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveJpegLegacy(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val dir =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "SuperScreenshot",
            )
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, displayName)
        return try {
            FileOutputStream(file).use { fos ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)) return null
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


