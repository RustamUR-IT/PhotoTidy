package app.phototidy.core

/** Measurements of one photo that the junk detector looks at. */
data class Features(
    val id: Long,
    val dateTakenMs: Long,
    val width: Int,
    val height: Int,
    val sharpness: Double,
    val brightness: Double,
    val contrast: Double,
    val dHash: Long,
    val isScreenshot: Boolean,
    val isFavorite: Boolean,
)

enum class JunkReason(val title: String, val explanation: String) {
    DUPLICATE("Duplicate", "A near-identical shot exists; the sharpest one is kept"),
    BLURRY("Blurry", "Out of focus or shaken"),
    DARK("Too dark", "Almost black – likely taken in a pocket or with the lens covered"),
    BLANK("Blank", "Almost no detail – a wall, ceiling or the floor"),
    OLD_SCREENSHOT("Old screenshot", "Screenshots are usually only needed for a short time"),
    TINY("Tiny image", "Very small – usually a sticker, icon or thumbnail"),
}

enum class Sensitivity(val title: String) {
    CAREFUL("Careful"), NORMAL("Normal"), THOROUGH("Thorough");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

object JunkDetector {

    private data class Thresholds(
        val blur: Double,
        val dark: Double,
        val blankContrast: Double,
        val dupBits: Int,
        val dupWindowMs: Long,
        val oldScreenshotDays: Long,
    )

    private fun thresholds(s: Sensitivity) = when (s) {
        Sensitivity.CAREFUL -> Thresholds(20.0, 10.0, 4.0, 3, 5 * 60_000L, 365)
        Sensitivity.NORMAL -> Thresholds(40.0, 16.0, 6.0, 5, 15 * 60_000L, 180)
        Sensitivity.THOROUGH -> Thresholds(80.0, 24.0, 9.0, 8, 60 * 60_000L, 60)
    }

    /**
     * Returns, for each photo that looks like junk, the reasons why.
     * Favourites are never suggested. Within a group of duplicates the best
     * photo (sharpest x biggest) is always kept.
     */
    fun detect(
        items: List<Features>,
        sensitivity: Sensitivity,
        nowMs: Long,
    ): Map<Long, List<JunkReason>> {
        val t = thresholds(sensitivity)
        val reasons = HashMap<Long, MutableList<JunkReason>>()
        fun flag(id: Long, r: JunkReason) {
            val list = reasons.getOrPut(id) { mutableListOf() }
            if (r !in list) list.add(r)
        }

        // --- single-photo checks ---
        for (f in items) {
            if (f.isFavorite) continue
            if (f.isScreenshot) {
                val ageDays = (nowMs - f.dateTakenMs) / 86_400_000L
                if (ageDays >= t.oldScreenshotDays) flag(f.id, JunkReason.OLD_SCREENSHOT)
                continue // screenshots are flat by nature: skip blur/blank checks
            }
            if (f.width.toLong() * f.height < 160L * 160L) flag(f.id, JunkReason.TINY)
            if (f.brightness < t.dark) flag(f.id, JunkReason.DARK)
            else if (f.contrast < t.blankContrast) flag(f.id, JunkReason.BLANK)
            else if (f.sharpness < t.blur) flag(f.id, JunkReason.BLURRY)
        }

        // --- duplicates: group near-identical photos ---
        val photos = items.filter { !it.isScreenshot }.sortedBy { it.dateTakenMs }
        val parent = HashMap<Long, Long>()
        fun find(x: Long): Long {
            var r = x
            while (parent[r] != null && parent[r] != r) r = parent.getValue(r)
            var c = x
            while (c != r) { val n = parent.getValue(c); parent[c] = r; c = n }
            return r
        }
        fun union(a: Long, b: Long) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        for (f in photos) parent[f.id] = f.id

        // (a) bursts: compare with neighbours taken shortly before
        for (i in photos.indices) {
            var j = i - 1
            while (j >= 0 && photos[i].dateTakenMs - photos[j].dateTakenMs <= t.dupWindowMs) {
                if (ImageMath.hamming(photos[i].dHash, photos[j].dHash) <= t.dupBits) {
                    union(photos[i].id, photos[j].id)
                }
                j--
            }
        }
        // (b) exact copies anywhere in the library (same picture saved twice)
        photos.groupBy { it.dHash }.values.filter { it.size > 1 && it[0].dHash != 0L }.forEach { group ->
            group.drop(1).forEach { union(group[0].id, it.id) }
        }

        // Keep the best of each group (a favourite always wins), suggest the rest.
        photos.groupBy { find(it.id) }.values.filter { it.size > 1 }.forEach { group ->
            val keep = group.maxByOrNull { quality(it) }!!
            group.forEach { if (it.id != keep.id && !it.isFavorite) flag(it.id, JunkReason.DUPLICATE) }
        }
        return reasons
    }

    private fun quality(f: Features): Double {
        val favBonus = if (f.isFavorite) 1e12 else 0.0
        val okExposure = if (f.brightness < 16) 0.2 else 1.0
        return favBonus + f.sharpness * okExposure * kotlin.math.sqrt(f.width.toDouble() * f.height)
    }
}
