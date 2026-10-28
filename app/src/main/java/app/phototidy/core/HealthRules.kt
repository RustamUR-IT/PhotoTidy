package app.phototidy.core

// =====================================================================================
// C1 – WhatsApp clean-up
// =====================================================================================

enum class WaCategory(val title: String, val note: String, val defaultOn: Boolean) {
    STATUSES("Viewed statuses", "Stories you already watched. Safe to remove.", true),
    STICKERS("Stickers", "Received stickers. Favourite stickers come back when used again.", true),
    GIFS("GIFs", "Animated GIFs from chats.", true),
    SENT_IMAGES("Sent photos (copies)", "Copies of photos you sent – the originals are usually still in your camera.", true),
    SENT_VIDEOS("Sent videos (copies)", "Copies of videos you sent – the originals are usually still in your camera.", true),
    VIDEOS("Received videos", "Videos sent to you. After removing they're gone from the chat too.", false),
    IMAGES("Received photos", "Photos sent to you. After removing they're gone from the chat too.", false),
    VOICE("Voice messages", "Voice notes. After removing they can't be played in the chat any more.", false),
    AUDIO("Audio files", "Music and audio files from chats.", false),
    DOCUMENTS("Documents", "PDFs and other files from chats. Check before removing.", false),
}

data class WaFile(val path: String, val sizeBytes: Long, val modifiedMs: Long, val category: WaCategory)

data class WaGroup(val category: WaCategory, val files: List<WaFile>) {
    val bytes get() = files.sumOf { it.sizeBytes }
}

object WhatsAppRules {

    /**
     * [relative] is the path below ".../WhatsApp/Media/" (or "WhatsApp Business/Media/"),
     * e.g. "WhatsApp Images/Sent/IMG-1.jpg". Returns null for files we leave alone.
     */
    fun categorize(relative: String): WaCategory? {
        val parts = relative.split('/')
        if (parts.size < 2) return null
        val name = parts.last()
        if (name.startsWith(".") && name != ".nomedia") {
            // hidden files only inside the status folder
            if (parts.first() != ".Statuses") return null
        }
        if (name == ".nomedia") return null
        val top = parts.first().removePrefix("WhatsApp Business ").removePrefix("WhatsApp ")
        val sent = parts.size >= 3 && parts[1].equals("Sent", true)
        return when (top) {
            ".Statuses" -> WaCategory.STATUSES
            "Stickers" -> WaCategory.STICKERS
            "Animated Gifs" -> WaCategory.GIFS
            "Images" -> if (sent) WaCategory.SENT_IMAGES else if (parts.size == 2) WaCategory.IMAGES else null
            "Video" -> if (sent) WaCategory.SENT_VIDEOS else if (parts.size == 2) WaCategory.VIDEOS else null
            "Voice Notes" -> WaCategory.VOICE
            "Audio" -> if (parts.size == 2 || sent) WaCategory.AUDIO else null
            "Documents" -> if (parts.size == 2 || sent) WaCategory.DOCUMENTS else null
            else -> null // profile photos, wallpapers, backups: never touched
        }
    }

    /** Groups files by category, keeping only those older than [minAgeDays]. */
    fun group(files: List<WaFile>, minAgeDays: Int, nowMs: Long): List<WaGroup> {
        val cutoff = nowMs - minAgeDays * 86_400_000L
        return files.filter { minAgeDays == 0 || it.modifiedMs < cutoff }
            .groupBy { it.category }
            .map { (c, l) -> WaGroup(c, l.sortedByDescending { it.sizeBytes }) }
            .sortedBy { it.category.ordinal }
    }
}

// =====================================================================================
// E1 – Privacy check
// =====================================================================================

enum class PermGroup(val title: String, val sensitive: Int) {
    CAMERA("Camera", 3),
    MICROPHONE("Microphone", 3),
    LOCATION_BACKGROUND("Location (always)", 3),
    LOCATION("Location", 2),
    CONTACTS("Contacts", 2),
    SMS("SMS", 3),
    CALL_LOG("Call log", 3),
    PHONE("Phone calls", 1),
    PHOTOS("Photos & videos", 2),
    BODY("Body sensors", 2),
    CALENDAR("Calendar", 1),
    NEARBY("Nearby devices", 1);

    companion object {
        /** Maps an Android permission name to its group. */
        fun of(permission: String): PermGroup? = when (permission.removePrefix("android.permission.")) {
            "CAMERA" -> CAMERA
            "RECORD_AUDIO" -> MICROPHONE
            "ACCESS_BACKGROUND_LOCATION" -> LOCATION_BACKGROUND
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> LOCATION
            "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS" -> CONTACTS
            "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "RECEIVE_MMS" -> SMS
            "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS" -> CALL_LOG
            "CALL_PHONE", "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "ANSWER_PHONE_CALLS" -> PHONE
            "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_EXTERNAL_STORAGE" -> PHOTOS
            "BODY_SENSORS", "BODY_SENSORS_BACKGROUND" -> BODY
            "READ_CALENDAR", "WRITE_CALENDAR" -> CALENDAR
            "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "NEARBY_WIFI_DEVICES" -> NEARBY
            else -> null
        }
    }
}

data class PrivacyApp(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val granted: Set<PermGroup>,
    /** Days since last opened; null = unknown (no usage access). */
    val daysUnused: Long?,
)

data class PrivacyFinding(val app: PrivacyApp, val score: Int, val reasons: List<String>)

object PrivacyRules {

    /** Permissions that are normal for certain kinds of apps – not worth a warning. */
    private val expected: List<Pair<List<String>, Set<PermGroup>>> = listOf(
        listOf("camera") to setOf(PermGroup.CAMERA, PermGroup.MICROPHONE, PermGroup.LOCATION, PermGroup.PHOTOS),
        listOf("whatsapp", "telegram", "signal", "messenger", "viber", "discord") to
            setOf(PermGroup.CAMERA, PermGroup.MICROPHONE, PermGroup.CONTACTS, PermGroup.PHOTOS, PermGroup.PHONE, PermGroup.LOCATION, PermGroup.NEARBY),
        listOf("maps", "navigation", "waze", "oebb", "uber", "bolt") to setOf(PermGroup.LOCATION, PermGroup.LOCATION_BACKGROUND),
        listOf("dialer", "contacts", "phone") to setOf(PermGroup.CONTACTS, PermGroup.CALL_LOG, PermGroup.PHONE),
        listOf("messaging", "sms", "mms") to setOf(PermGroup.SMS, PermGroup.CONTACTS),
        listOf("photos", "gallery") to setOf(PermGroup.PHOTOS),
    )

    private fun expectedFor(a: PrivacyApp): Set<PermGroup> {
        val t = (a.pkg + " " + a.label).lowercase()
        return expected.filter { (words, _) -> words.any { t.contains(it) } }.flatMap { it.second }.toSet()
    }

    /**
     * Scores how much an app is worth checking: sensitive access you probably don't
     * need, especially in apps you don't use.
     */
    fun assess(a: PrivacyApp, unusedDaysLimit: Long = 30): PrivacyFinding? {
        if (a.isSystem || a.granted.isEmpty()) return null
        val unexpected = a.granted - expectedFor(a)
        val reasons = mutableListOf<String>()
        var score = 0
        val unused = a.daysUnused != null && a.daysUnused >= unusedDaysLimit
        if (unused) {
            val strong = a.granted.filter { it.sensitive >= 2 }
            if (strong.isNotEmpty()) {
                score += strong.sumOf { it.sensitive } * 2
                reasons += "Not opened in ${a.daysUnused} days, but can use: " + strong.joinToString(", ") { it.title.lowercase() }
            }
        }
        if (PermGroup.LOCATION_BACKGROUND in unexpected) {
            score += 6; reasons += "Can see your location even when you're not using it"
        }
        val odd = unexpected.filter { it in setOf(PermGroup.SMS, PermGroup.CALL_LOG, PermGroup.MICROPHONE, PermGroup.CAMERA) }
        if (odd.isNotEmpty() && !unused) {
            score += odd.sumOf { it.sensitive }
            reasons += "Has access to " + odd.joinToString(", ") { it.title.lowercase() } + " – does it need that?"
        }
        return if (score >= 3) PrivacyFinding(a, score, reasons) else null
    }

    fun findings(apps: List<PrivacyApp>, unusedDaysLimit: Long = 30) =
        apps.mapNotNull { assess(it, unusedDaysLimit) }.sortedByDescending { it.score }

    /** permission group -> apps that hold it (user apps only), most sensitive groups first. */
    fun byPermission(apps: List<PrivacyApp>): List<Pair<PermGroup, List<PrivacyApp>>> =
        PermGroup.entries.sortedByDescending { it.sensitive }
            .map { g -> g to apps.filter { !it.isSystem && g in it.granted }.sortedBy { it.label.lowercase() } }
            .filter { it.second.isNotEmpty() }
}

// =====================================================================================
// D1 – Battery drainers
// =====================================================================================

data class BatteryApp(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    /** Last 7 days, minutes. */
    val foregroundMin: Long,
    /** Minutes running a foreground service (= working while you don't look at it). */
    val backgroundServiceMin: Long,
    /** True if Android's battery saving doesn't apply to this app. */
    val unrestricted: Boolean,
)

data class BatteryFinding(val app: BatteryApp, val score: Double, val reason: String)

object BatteryRules {

    /** Apps where running in the background is their job – don't nag about them. */
    private val expectedBackground = listOf(
        "music", "spotify", "podcast", "audible", "radio", "navigation", "maps", "waze",
        "fitness", "health", "strava", "vpn", "authenticator", "wear", "watch", "buds", "earbuds",
        "keyboard", "inputmethod", "launcher", "alarm", "clock",
    )

    fun assess(a: BatteryApp): BatteryFinding? {
        if (a.isSystem) return null
        val t = (a.pkg + " " + a.label).lowercase()
        if (expectedBackground.any { t.contains(it) }) return null
        val bg = a.backgroundServiceMin
        if (bg < 60) return null // under 1 h in a week: fine
        // Background time that is out of proportion to how much you actually use it.
        val ratio = bg.toDouble() / maxOf(a.foregroundMin, 5)
        var score = bg / 60.0 * (if (ratio > 3) 2.0 else 1.0)
        if (a.unrestricted) score *= 1.5
        if (score < 2.0) return null
        val hours = bg / 60
        val reason = buildString {
            append("Ran ${hours} h in the background this week")
            if (a.foregroundMin < 30) append(", but you opened it for only ${a.foregroundMin} min")
            if (a.unrestricted) append(" · battery saving is off for it")
        }
        return BatteryFinding(a, score, reason)
    }

    fun findings(apps: List<BatteryApp>) = apps.mapNotNull { assess(it) }.sortedByDescending { it.score }
}
