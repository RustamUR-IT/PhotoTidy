package app.phototidy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import app.phototidy.core.FileCategory
import app.phototidy.core.FileEntry
import app.phototidy.core.FileJunkReason
import app.phototidy.core.FileRules
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where file tidy looks and what it leaves alone.
 *
 * Only LOOSE files are moved: files lying directly in Download, Documents or the
 * top of the phone storage. Folders you (or apps) made are never touched, so no
 * app loses its files.
 */
object FilePlaces {
    val storage: File get() = Environment.getExternalStorageDirectory()
    val sortedRoot: File get() = File(storage, "Documents/Sorted")
    val trashDir: File get() = File(storage, ".PhotoTidy Trash")

    fun looseDirs(): List<File> = listOf(
        File(storage, Environment.DIRECTORY_DOWNLOADS),
        File(storage, Environment.DIRECTORY_DOCUMENTS),
        storage,
    )

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()
}

data class FileItem(val file: File, val entry: FileEntry) {
    val path get() = file.absolutePath
    val name get() = file.name
    val size get() = entry.sizeBytes
    val modified get() = entry.modifiedMs
    val ext get() = entry.ext
    val folderLabel: String
        get() = file.parentFile?.absolutePath?.removePrefix(FilePlaces.storage.absolutePath)?.trimStart('/')
            ?.ifEmpty { "Phone storage" } ?: ""
}

data class FileGroup(val category: FileCategory, val targetDir: File, val items: List<FileItem>) {
    val key get() = category.name
}

data class FileJunk(val item: FileItem, val reasons: List<FileJunkReason>)

data class FilePlan(
    val hasAccess: Boolean,
    val totalFiles: Int,
    val groups: List<FileGroup>,
    val junk: List<FileJunk>,
    val trashCount: Int,
) {
    val sortCount get() = groups.sumOf { it.items.size }
    val junkBytes get() = junk.sumOf { it.item.size }

    companion object {
        val EMPTY = FilePlan(false, 0, emptyList(), emptyList(), 0)
    }
}

/** Remembers hashes and PDF text so each file is only read once. Key: path|size|modified. */
class FileCache(context: Context) {
    private val file = File(context.filesDir, "files.tsv")

    data class Rec(val hash: String?, val text: String?)

    fun load(): MutableMap<String, Rec> {
        val m = HashMap<String, Rec>()
        if (!file.exists()) return m
        file.forEachLine { line ->
            val p = line.split('\t')
            if (p.size >= 3) m[p[0]] = Rec(p[1].ifEmpty { null }, if (p[2] == "-") null else p[2])
        }
        return m
    }

    fun save(m: Map<String, Rec>) {
        val tmp = File(file.parentFile, "files.tsv.tmp")
        tmp.bufferedWriter().use { w ->
            m.forEach { (k, r) ->
                val text = r.text?.replace('\t', ' ')?.replace('\n', ' ') ?: "-"
                w.append(k.replace('\t', ' ')).append('\t').append(r.hash ?: "").append('\t').append(text).append('\n')
            }
        }
        tmp.renameTo(file)
    }

    companion object {
        fun key(f: File) = "${f.absolutePath}|${f.length()}|${f.lastModified()}"
    }
}

class FileRepo(private val context: Context) {

    /** Files that are candidates for sorting (loose files) plus already-sorted ones (for duplicates). */
    fun listFiles(): List<Pair<File, Boolean>> {
        val out = ArrayList<Pair<File, Boolean>>()
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        for (dir in FilePlaces.looseDirs()) {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && usable(f, now) && seen.add(f.absolutePath)) out += f to false
            }
        }
        FilePlaces.sortedRoot.walkTopDown().maxDepth(3).forEach { f ->
            if (f.isFile && usable(f, now) && seen.add(f.absolutePath)) out += f to true
        }
        return out
    }

    private fun usable(f: File, now: Long): Boolean {
        if (f.name.startsWith(".")) return false
        if (f.extension.lowercase() in FileRules.IMAGE_EXT) return false // photo part handles these
        if (now - f.lastModified() < 10 * 60_000L) return false // might still be downloading
        return true
    }

    /**
     * Reads what's needed: content hashes for files that share a size with another file,
     * and the first-page text of PDFs. Cached.
     */
    fun scan(onProgress: (Int, Int) -> Unit, isStopped: () -> Boolean) {
        val files = listFiles()
        val cache = FileCache(context)
        val old = cache.load()
        val fresh = HashMap<String, FileCache.Rec>()

        val sizeCounts = files.groupingBy { it.first.length() }.eachCount()
        val todo = files.filter { (f, _) ->
            val needsHash = f.length() > 0 && (sizeCounts[f.length()] ?: 0) > 1
            val needsText = f.extension.equals("pdf", true)
            val r = old[FileCache.key(f)]
            if (r != null) fresh[FileCache.key(f)] = r
            (needsHash && r?.hash == null) || (needsText && r?.text == null)
        }

        onProgress(0, todo.size)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            todo.forEachIndexed { i, (f, _) ->
                if (isStopped()) return@forEachIndexed
                val k = FileCache.key(f)
                val prev = fresh[k]
                val needsHash = f.length() > 0 && (sizeCounts[f.length()] ?: 0) > 1
                val hash = prev?.hash ?: if (needsHash) sha256(f) else null
                val text = prev?.text ?: if (f.extension.equals("pdf", true)) pdfText(f, recognizer) else null
                fresh[k] = FileCache.Rec(hash, text)
                onProgress(i + 1, todo.size)
            }
        } finally {
            recognizer.close()
            cache.save(fresh)
        }
        FileTrash(context).purgeOld()
    }

    private fun sha256(f: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().buffered(1 shl 16).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "hash failed $f", e); null
    }

    /** Renders page 1 of a PDF and reads its text on-device. Returns "" if there's none. */
    private fun pdfText(f: File, recognizer: com.google.mlkit.vision.text.TextRecognizer): String {
        if (f.length() > 50L * 1024 * 1024) return ""
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var bmp: Bitmap? = null
        return try {
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) return ""
            page = renderer.openPage(0)
            val scale = 1400f / max(page.width, page.height)
            val w = max(1, (page.width * scale).roundToInt())
            val h = max(1, (page.height * scale).roundToInt())
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 20, TimeUnit.SECONDS)
                .text.lowercase().take(3000)
        } catch (e: Exception) {
            Log.w(TAG, "pdf read failed $f", e)
            "" // encrypted or broken PDF – sort by name only
        } finally {
            bmp?.recycle()
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun buildEntries(): List<FileItem> {
        val cache = FileCache(context).load()
        return listFiles().map { (f, sorted) ->
            val r = cache[FileCache.key(f)]
            val siblings = if (f.extension.lowercase() in setOf("zip", "rar", "7z", "tar", "gz", "tgz")) {
                f.parentFile?.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()
            } else emptySet()
            FileItem(
                f,
                FileEntry(
                    path = f.absolutePath,
                    name = f.name,
                    sizeBytes = f.length(),
                    modifiedMs = f.lastModified(),
                    contentHash = r?.hash,
                    text = r?.text ?: "",
                    siblingDirs = siblings,
                    isSorted = sorted,
                ),
            )
        }
    }

    /** Moves files into folders. Returns how many moved. */
    fun move(moves: Map<File, List<File>>): Int {
        var ok = 0
        val scan = ArrayList<String>()
        for ((dir, files) in moves) {
            dir.mkdirs()
            for (f in files) {
                if (!f.exists()) continue
                val name = FileRules.uniqueName(f.name) { File(dir, it).exists() }
                val target = File(dir, name)
                if (f.renameTo(target)) {
                    ok++
                    scan += f.absolutePath
                    scan += target.absolutePath
                } else Log.w(TAG, "move failed $f -> $target")
            }
        }
        rescan(scan)
        return ok
    }

    fun rescan(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    companion object {
        private const val TAG = "FileRepo"
    }
}

/**
 * The app's own trash for files: deleted files are parked in a hidden folder for
 * 30 days and can be put back exactly where they were.
 */
class FileTrash(private val context: Context) {

    data class Entry(val stored: File, val originalPath: String, val trashedAt: Long) {
        val name get() = File(originalPath).name
    }

    private val dir get() = FilePlaces.trashDir
    private val index get() = File(dir, ".index.tsv")

    fun list(): List<Entry> {
        if (!index.exists()) return emptyList()
        return index.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) null else Entry(File(dir, p[0]), p[1], p[2].toLongOrNull() ?: 0L)
        }.filter { it.stored.exists() }.sortedByDescending { it.trashedAt }
    }

    private fun write(entries: List<Entry>) {
        dir.mkdirs()
        index.writeText(entries.joinToString("") { "${it.stored.name}\t${it.originalPath}\t${it.trashedAt}\n" })
    }

    fun trash(files: List<File>): Int {
        dir.mkdirs()
        File(dir, ".nomedia").let { if (!it.exists()) it.createNewFile() }
        val entries = list().toMutableList()
        val now = System.currentTimeMillis()
        var ok = 0
        val scan = ArrayList<String>()
        for (f in files) {
            if (!f.exists()) continue
            val stored = File(dir, FileRules.uniqueName(f.name) { File(dir, it).exists() })
            if (f.renameTo(stored)) {
                entries += Entry(stored, f.absolutePath, now)
                scan += f.absolutePath
                ok++
            }
        }
        write(entries)
        FileRepo(context).rescan(scan)
        return ok
    }

    fun restore(toRestore: List<Entry>): Int {
        val all = list().toMutableList()
        var ok = 0
        val scan = ArrayList<String>()
        for (e in toRestore) {
            val orig = File(e.originalPath)
            orig.parentFile?.mkdirs()
            val target = File(orig.parentFile, FileRules.uniqueName(orig.name) { File(orig.parentFile, it).exists() })
            if (e.stored.renameTo(target)) {
                all.removeAll { it.stored == e.stored }
                scan += target.absolutePath
                ok++
            }
        }
        write(all)
        FileRepo(context).rescan(scan)
        return ok
    }

    fun deleteForever(entries: List<Entry>): Int {
        val all = list().toMutableList()
        var ok = 0
        for (e in entries) {
            if (e.stored.delete()) {
                all.removeAll { it.stored == e.stored }
                ok++
            }
        }
        write(all)
        return ok
    }

    fun purgeOld(maxAgeMs: Long = 30L * 86_400_000L) {
        val now = System.currentTimeMillis()
        deleteForever(list().filter { now - it.trashedAt > maxAgeMs })
    }
}

/** Builds file suggestions from what's on disk + the cache + user decisions. */
class FilePlanner(private val context: Context) {
    fun build(): FilePlan {
        if (!FilePlaces.hasAccess()) return FilePlan.EMPTY
        val settings = Settings(context)
        val items = FileRepo(context).buildEntries()
        val trashCount = FileTrash(context).list().size

        val junkMap = FileRules.junk(items.map { it.entry }, settings.sensitivity, System.currentTimeMillis())
        val kept = settings.keptFiles
        val byPath = items.associateBy { it.path }
        val junk = junkMap.filterKeys { it !in kept }
            .mapNotNull { (p, r) -> byPath[p]?.let { FileJunk(it, r) } }
            .sortedByDescending { it.item.size }

        val noSort = settings.noSortFiles
        val groups = items
            .filter { !it.entry.isSorted && it.path !in junkMap.keys && it.path !in noSort }
            .groupBy { FileRules.categorize(it.entry) }
            .filterKeys { it != FileCategory.OTHER }
            .map { (cat, list) ->
                FileGroup(cat, File(FilePlaces.sortedRoot, cat.folder), list.sortedByDescending { it.modified })
            }
            .sortedWith(compareByDescending<FileGroup> { it.category.isTopic }.thenByDescending { it.items.size })

        return FilePlan(true, items.size, groups, junk, trashCount)
    }
}
