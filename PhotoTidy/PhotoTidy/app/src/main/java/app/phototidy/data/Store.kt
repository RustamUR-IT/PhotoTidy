package app.phototidy.data

import android.content.Context
import app.phototidy.core.Category
import app.phototidy.core.Label
import app.phototidy.core.Sensitivity
import java.io.File

/** What the scanner learned about one photo. Cached so each photo is analysed only once. */
data class AnalysisRecord(
    val id: Long,
    val dateModified: Long,
    val sharpness: Double,
    val brightness: Double,
    val contrast: Double,
    val dHash: Long,
    val ocrChars: Int,
    val category: Category,
    val labels: List<Label>,
)

/** Analysis cache in a small tab-separated file in the app's private storage. */
class AnalysisCache(context: Context) {
    private val file = File(context.filesDir, "analysis.tsv")
    private val lock = Any()

    fun load(): MutableMap<Long, AnalysisRecord> = synchronized(lock) {
        val map = HashMap<Long, AnalysisRecord>()
        if (!file.exists()) return map
        file.forEachLine { line ->
            val p = line.split('\t')
            if (p.size < 9) return@forEachLine
            try {
                val labels = if (p[8].isEmpty()) emptyList() else p[8].split('|').mapNotNull {
                    val i = it.lastIndexOf(':')
                    if (i <= 0) null else Label(it.substring(0, i), it.substring(i + 1).toFloat())
                }
                val r = AnalysisRecord(
                    id = p[0].toLong(),
                    dateModified = p[1].toLong(),
                    sharpness = p[2].toDouble(),
                    brightness = p[3].toDouble(),
                    contrast = p[4].toDouble(),
                    dHash = p[5].toLong(),
                    ocrChars = p[6].toInt(),
                    category = Category.fromName(p[7]),
                    labels = labels,
                )
                map[r.id] = r
            } catch (_: Exception) {
                // skip corrupt line
            }
        }
        map
    }

    fun save(records: Collection<AnalysisRecord>) = synchronized(lock) {
        val tmp = File(file.parentFile, "analysis.tsv.tmp")
        tmp.bufferedWriter().use { w ->
            for (r in records) {
                val labels = r.labels.joinToString("|") {
                    "${it.text.replace('|', ' ').replace('\t', ' ')}:${"%.2f".format(java.util.Locale.US, it.confidence)}"
                }
                w.append("${r.id}\t${r.dateModified}\t${"%.1f".format(java.util.Locale.US, r.sharpness)}\t")
                w.append("${"%.1f".format(java.util.Locale.US, r.brightness)}\t${"%.1f".format(java.util.Locale.US, r.contrast)}\t")
                w.append("${r.dHash}\t${r.ocrChars}\t${r.category.name}\t$labels\n")
            }
        }
        tmp.renameTo(file)
    }
}

/** User choices and settings. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var sensitivity: Sensitivity
        get() = Sensitivity.fromName(prefs.getString("sensitivity", null))
        set(v) = prefs.edit().putString("sensitivity", v.name).apply()

    var eventsEnabled: Boolean
        get() = prefs.getBoolean("events", true)
        set(v) = prefs.edit().putBoolean("events", v).apply()

    var nightlyEnabled: Boolean
        get() = prefs.getBoolean("nightly", true)
        set(v) = prefs.edit().putBoolean("nightly", v).apply()

    /** Photos the user said are not junk. */
    var keptIds: Set<Long>
        get() = prefs.getStringSet("kept", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("kept", v.map { it.toString() }.toSet()).apply()

    /** Photos the user doesn't want moved. */
    var noSortIds: Set<Long>
        get() = prefs.getStringSet("nosort", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("nosort", v.map { it.toString() }.toSet()).apply()

    fun eventName(key: String): String? = prefs.getString("event_$key", null)
    fun setEventName(key: String, name: String) = prefs.edit().putString("event_$key", name).apply()

    var lastNotified: String
        get() = prefs.getString("last_notified", "") ?: ""
        set(v) = prefs.edit().putString("last_notified", v).apply()

    var lastScanMs: Long
        get() = prefs.getLong("last_scan", 0L)
        set(v) = prefs.edit().putLong("last_scan", v).apply()

    fun resetDecisions() = prefs.edit().remove("kept").remove("nosort").apply()
}
