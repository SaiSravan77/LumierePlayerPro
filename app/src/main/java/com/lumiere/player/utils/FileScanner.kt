package com.lumiere.player.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─── FileScanner ─────────────────────────────────────────────

data class VideoFile(
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateAdded: Long,
    val mimeType: String
)

object FileScanner {

    private val VIDEO_MIME_TYPES = setOf(
        "video/mp4", "video/x-matroska", "video/avi", "video/quicktime",
        "video/webm", "video/3gpp", "video/x-flv", "video/mpeg"
    )

    suspend fun scanDevice(context: Context): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol)
                val mime = cursor.getString(mimeCol) ?: continue
                if (mime !in VIDEO_MIME_TYPES) continue
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                videos.add(VideoFile(
                    uri      = uri,
                    name     = cursor.getString(nameCol) ?: "Unknown",
                    path     = cursor.getString(pathCol) ?: "",
                    size     = cursor.getLong(sizeCol),
                    duration = cursor.getLong(durCol),
                    dateAdded= cursor.getLong(dateCol),
                    mimeType = mime
                ))
            }
        }
        videos
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824f)
        bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576f)
        else                    -> "%.0f KB".format(bytes / 1024f)
    }
}

// ─── ScreenshotHelper ─────────────────────────────────────────

object ScreenshotHelper {

    suspend fun saveFrame(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val filename = "Lumiere_${System.currentTimeMillis()}.jpg"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Lumiere")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Lumiere")
                dir.mkdirs()
                val file = File(dir, filename)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) { null }
    }
}
