package app.relay.companion.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class StatusItem(
    val uri: String,
    val name: String,
    val mime: String,
    val lastModified: Long,
    val isVideo: Boolean,
)

class MediaRepository(private val context: Context) {

    fun list(treeUri: Uri): List<StatusItem> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val folder = findStatusesFolder(root) ?: root
        return folder.listFiles()
            .asSequence()
            .filter { it.isFile && !it.name.isNullOrBlank() }
            .mapNotNull { file ->
                val mime = file.type.orEmpty()
                val isImage = mime.startsWith("image/")
                val isVideo = mime.startsWith("video/")
                if (!isImage && !isVideo) return@mapNotNull null
                StatusItem(
                    uri = file.uri.toString(),
                    name = file.name ?: "status",
                    mime = mime,
                    lastModified = file.lastModified(),
                    isVideo = isVideo,
                )
            }
            .sortedByDescending { it.lastModified }
            .toList()
    }

    private fun findStatusesFolder(root: DocumentFile): DocumentFile? {
        if (root.name?.contains("Statuses", ignoreCase = true) == true) return root
        root.listFiles().forEach { child ->
            if (child.isDirectory) {
                findStatusesFolder(child)?.let { return it }
            }
        }
        return null
    }

    suspend fun save(item: StatusItem): Boolean = withContext(Dispatchers.IO) {
        val source = Uri.parse(item.uri)
        val collection = if (item.isVideo) {
            if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            if (Build.VERSION.SDK_INT >= 29) {
                val dir = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/WhatsApp")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val dest = resolver.insert(collection, values) ?: return@withContext false
        runCatching {
            resolver.openInputStream(source).use { input ->
                resolver.openOutputStream(dest).use { output ->
                    requireNotNull(input)
                    requireNotNull(output)
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(dest, done, null, null)
            }
            true
        }.getOrElse {
            resolver.delete(dest, null, null)
            false
        }
    }

    suspend fun saveBitmap(bitmap: Bitmap, fileName: String = "relay-qr.png"): Boolean = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WhatsApp")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val dest = resolver.insert(collection, values) ?: return@withContext false
        runCatching {
            resolver.openOutputStream(dest).use { output ->
                requireNotNull(output)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(dest, done, null, null)
            }
            true
        }.getOrElse {
            resolver.delete(dest, null, null)
            false
        }
    }
}
