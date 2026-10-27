package app.phototidy.data

import android.content.Context
import app.phototidy.core.Category
import app.phototidy.core.EventGrouper
import app.phototidy.core.Features
import app.phototidy.core.JunkDetector
import app.phototidy.core.JunkReason

data class SortGroup(
    val key: String,
    val title: String,
    val isEvent: Boolean,
    val targetPath: String,
    val items: List<MediaItem>,
)

data class JunkItem(val item: MediaItem, val reasons: List<JunkReason>)

data class Plan(
    val totalPhotos: Int,
    val analyzed: Int,
    val sortGroups: List<SortGroup>,
    val junk: List<JunkItem>,
) {
    val sortCount get() = sortGroups.sumOf { it.items.size }
    val junkBytes get() = junk.sumOf { it.item.sizeBytes }

    companion object {
        val EMPTY = Plan(0, 0, emptyList(), emptyList())
    }
}

/** Scans the gallery (only new/changed photos) and keeps the analysis cache up to date. */
class Scanner(private val context: Context) {

    /** @return number of newly analysed photos */
    fun scan(onProgress: (done: Int, total: Int) -> Unit, isStopped: () -> Boolean): Int {
        val repo = MediaRepo(context)
        val cache = AnalysisCache(context)
        val items = repo.queryAll()
        val records = cache.load()

        // forget photos that no longer exist
        val existing = items.map { it.id }.toHashSet()
        records.keys.retainAll(existing)

        val todo = items.filter { records[it.id]?.dateModified != it.dateModified }
        var done = 0
        onProgress(0, todo.size)
        Analyzer(context).use { analyzer ->
            for (item in todo) {
                if (isStopped()) break
                analyzer.analyze(item)?.let { records[item.id] = it }
                done++
                if (done % 25 == 0) onProgress(done, todo.size)
                if (done % 200 == 0) cache.save(records.values)
            }
        }
        cache.save(records.values)
        Settings(context).lastScanMs = System.currentTimeMillis()
        onProgress(done, todo.size)
        return done
    }
}

/** Turns analysis results + user decisions into concrete suggestions. Cheap; no image work. */
class Planner(private val context: Context) {

    fun build(): Plan {
        val settings = Settings(context)
        val items = MediaRepo(context).queryAll()
        val records = AnalysisCache(context).load()
        val kept = settings.keptIds
        val noSort = settings.noSortIds

        val analyzedItems = items.filter { records.containsKey(it.id) }

        // ---------- junk ----------
        val features = analyzedItems.map {
            val r = records.getValue(it.id)
            Features(
                id = it.id, dateTakenMs = it.dateTakenMs, width = it.width, height = it.height,
                sharpness = r.sharpness, brightness = r.brightness, contrast = r.contrast,
                dHash = r.dHash, isScreenshot = it.isScreenshot, isFavorite = it.isFavorite,
            )
        }
        val junkMap = JunkDetector.detect(features, settings.sensitivity, System.currentTimeMillis())
        val byId = analyzedItems.associateBy { it.id }
        val junk = junkMap
            .filterKeys { it !in kept }
            .mapNotNull { (id, reasons) -> byId[id]?.let { JunkItem(it, reasons) } }
            .sortedByDescending { it.item.dateTakenMs }

        // ---------- sorting ----------
        val junkIds = junkMap.keys
        val candidates = analyzedItems.filter { it.isMovable && it.id !in noSort && it.id !in junkIds }

        val groups = mutableListOf<SortGroup>()
        val assigned = HashSet<Long>()

        if (settings.eventsEnabled) {
            val shots = candidates
                .filter { it.isCameraPhoto && records.getValue(it.id).category != Category.DOCUMENTS }
                .map { EventGrouper.Shot(it.id, it.dateTakenMs) }
            for (event in EventGrouper.group(shots)) {
                val name = EventGrouper.safeFolderName(settings.eventName(event.key) ?: event.defaultName)
                val target = "${MediaRepo.SORTED_ROOT}Events/$name/"
                val members = event.photoIds.mapNotNull { byId[it] }.filter { it.relativePath != target }
                if (members.isEmpty()) continue
                assigned += members.map { it.id }
                groups += SortGroup(event.key, name, true, target, members.sortedBy { it.dateTakenMs })
            }
        }

        val byCategory = candidates
            .filter { it.id !in assigned }
            .groupBy { records.getValue(it.id).category }
        for (cat in Category.entries) {
            if (cat == Category.OTHER || cat == Category.SCREENSHOTS) continue
            val list = byCategory[cat] ?: continue
            val target = "${MediaRepo.SORTED_ROOT}${cat.folder}/"
            groups += SortGroup("cat:${cat.name}", cat.title, false, target,
                list.sortedByDescending { it.dateTakenMs })
        }

        groups.sortWith(compareByDescending<SortGroup> { it.isEvent }.thenByDescending { it.items.size })
        return Plan(items.size, analyzedItems.size, groups, junk)
    }
}
