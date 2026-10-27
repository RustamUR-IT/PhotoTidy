package app.phototidy.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/** One image in the phone's gallery. */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val dateTakenMs: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isFavorite: Boolean,
) {
    private val pathLower get() = relativePath.lowercase()

    val isScreenshot: Boolean
        get() = pathLower.contains("screenshot") || name.lowercase().startsWith("screenshot")

    val isCameraPhoto: Boolean
        get() = pathLower.startsWith("dcim/") && !isScreenshot

    val isAlreadySorted: Boolean
        get() = pathLower.startsWith(MediaRepo.SORTED_ROOT.lowercase())

    /**
     * Only photos from these places are moved. Images inside other apps'
     * own folders are left alone so those apps don't lose their files.
     */
    val isMovable: Boolean
        get() {
            if (isAlreadySorted || isScreenshot) return false
            val p = pathLower
            return p.startsWith("dcim/") ||
                p.startsWith("download/") ||
                p == "pictures/" ||
                MediaRepo.MESSENGER_FOLDERS.any { p.startsWith(it) }
        }
}

class MediaRepo(private val context: Context) {

    companion object {
        const val SORTED_ROOT = "Pictures/Sorted/"
        val MESSENGER_FOLDERS = listOf(
            "pictures/whatsapp", "pictures/telegram", "pictures/instagram",
            "pictures/messenger", "pictures/signal", "pictures/snapchat",
        )
        private const val TAG = "MediaRepo"
        val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }

    fun queryAll(): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.IS_FAVORITE,
        )
        val out = ArrayList<MediaItem>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val iFav = c.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val taken = if (c.isNull(iTaken)) 0L else c.getLong(iTaken)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(iName) ?: "",
                    relativePath = c.getString(iPath) ?: "",
                    dateTakenMs = if (taken > 0) taken else c.getLong(iAdded) * 1000L,
                    dateModified = c.getLong(iMod),
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    sizeBytes = c.getLong(iSize),
                    isFavorite = c.getInt(iFav) == 1,
                )
            }
        }
        return out
    }

    /**
     * Moves photos into [targetRelativePath] (e.g. "Pictures/Sorted/Food & Drinks/").
     * The caller must already have write consent (MediaStore.createWriteRequest).
     * Returns how many were moved.
     */
    fun move(uris: List<Uri>, targetRelativePath: String): Int {
        var ok = 0
        for (uri in uris) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
                }
                if (context.contentResolver.update(uri, values, null, null) > 0) ok++
            } catch (e: Exception) {
                Log.w(TAG, "Could not move $uri", e)
            }
        }
        return ok
    }
}
