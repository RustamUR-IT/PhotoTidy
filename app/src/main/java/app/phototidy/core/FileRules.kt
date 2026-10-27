package app.phototidy.core

import java.text.Normalizer

/** One file found on the phone (pure data so the rules can be tested off-device). */
data class FileEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    /** SHA-256 of the content; only filled in when another file has the same size. */
    val contentHash: String? = null,
    /** Text read from the first page (PDFs only), lowercase. */
    val text: String = "",
    /** Names of folders next to this file (to spot already-extracted archives). */
    val siblingDirs: Set<String> = emptySet(),
    /** True for files already inside the Sorted folder. */
    val isSorted: Boolean = false,
) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val baseName: String get() = if ('.' in name) name.substringBeforeLast('.') else name
}

enum class FileCategory(val folder: String) {
    // Topic folders: picked from the file name and the text inside
    INVOICES("Invoices & Receipts"),
    TICKETS("Tickets & Travel"),
    OFFICIAL("Contracts & Official"),
    FINANCE("Bank & Insurance"),
    HEALTH("Health"),
    WORK("Work & School"),
    // Type folders: picked from the file type
    DOCUMENTS("Documents"),
    SPREADSHEETS("Spreadsheets"),
    PRESENTATIONS("Presentations"),
    EBOOKS("Ebooks"),
    ARCHIVES("Archives"),
    AUDIO("Audio"),
    VIDEOS("Videos"),
    INSTALLERS("Installers"),
    OTHER("Other");

    val isTopic get() = ordinal <= WORK.ordinal
}

enum class FileJunkReason(val title: String, val explanation: String) {
    DUPLICATE("Duplicate", "The exact same file exists elsewhere; one copy is kept"),
    PARTIAL("Unfinished download", "A download that never completed"),
    EMPTY("Empty", "The file is 0 bytes"),
    OLD_INSTALLER("Old installer", "An app installer (APK) – once installed, it's not needed"),
    EXTRACTED("Already unpacked", "A folder with the same name exists next to this archive"),
    OLD_INVITE("Old invite", "A calendar invite or contact card that was already imported long ago"),
}

object FileRules {

    val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "avif")
    private val DOC_EXT = setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "pages")
    private val SHEET_EXT = setOf("xls", "xlsx", "ods", "csv", "numbers")
    private val SLIDE_EXT = setOf("ppt", "pptx", "odp", "key")
    private val EBOOK_EXT = setOf("epub", "mobi", "azw", "azw3", "fb2", "djvu", "cbz", "cbr")
    private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "wma")
    private val VIDEO_EXT = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "wmv")
    private val INSTALLER_EXT = setOf("apk", "apks", "xapk", "apkm")
    private val PARTIAL_EXT = setOf("crdownload", "part", "partial", "download", "opdownload", "tmp")
    private val INVITE_EXT = setOf("ics", "vcs", "vcf")

    /** Topic keywords, German + English. Matched against whole words (umlauts folded). */
    private val topics: Map<FileCategory, Set<String>> = mapOf(
        FileCategory.INVOICES to setOf(
            "rechnung", "invoice", "receipt", "quittung", "beleg", "kassenbon", "kassabon",
            "zahlungsbestaetigung", "bestellbestaetigung", "bestellung", "order", "lieferschein",
            "gutschrift", "rechnungsnummer", "ust", "mwst", "vat", "billing", "bill", "faktura",
            "zahlungsbeleg", "payment",
        ),
        FileCategory.TICKETS to setOf(
            "ticket", "tickets", "boarding", "boardingpass", "bordkarte", "fahrkarte", "eintrittskarte",
            "flug", "flight", "booking", "buchung", "buchungsbestaetigung", "reservation",
            "reservierung", "itinerary", "reiseplan", "hotel", "airbnb", "oebb", "westbahn",
            "railjet", "zugticket", "gate", "seat", "sitzplatz", "check", "visa", "reisepass",
        ),
        FileCategory.OFFICIAL to setOf(
            "vertrag", "contract", "agreement", "vereinbarung", "bescheid", "finanzamt",
            "magistrat", "meldezettel", "antrag", "formular", "application", "certificate",
            "urkunde", "zeugnis", "mietvertrag", "kuendigung", "vollmacht", "behoerde",
            "bezirkshauptmannschaft", "aufenthaltstitel", "terms", "agb", "lease",
        ),
        FileCategory.FINANCE to setOf(
            "kontoauszug", "statement", "bank", "iban", "bic", "versicherung", "insurance",
            "polizze", "police", "kreditkarte", "lohnzettel", "gehaltsabrechnung", "payslip",
            "salary", "steuer", "tax", "depot", "kredit", "loan", "pension", "lohn",
            "jahreslohnzettel", "arbeitnehmerveranlagung",
        ),
        FileCategory.HEALTH to setOf(
            "arzt", "befund", "rezept", "prescription", "medical", "krankenkasse", "oegk",
            "impfung", "impfpass", "vaccination", "labor", "laborbefund", "diagnose", "diagnosis",
            "krankenstand", "ueberweisung", "zahnarzt", "spital", "hospital", "therapie",
        ),
        FileCategory.WORK to setOf(
            "lebenslauf", "cv", "resume", "bewerbung", "anschreiben", "cover", "praesentation",
            "protokoll", "meeting", "homework", "hausuebung", "assignment", "lecture",
            "vorlesung", "skriptum", "seminar", "thesis", "bachelorarbeit", "masterarbeit",
            "uni", "university", "schule", "school", "projekt", "project", "report", "bericht",
        ),
    )

    /** Words that are strong enough on their own in a file NAME. */
    private val strongNameWords = setOf(
        "rechnung", "invoice", "receipt", "quittung", "kassenbon", "ticket", "tickets",
        "boardingpass", "bordkarte", "fahrkarte", "vertrag", "contract", "kontoauszug",
        "lohnzettel", "payslip", "befund", "rezept", "lebenslauf", "cv", "resume", "bewerbung",
        "polizze", "meldezettel", "bescheid", "mietvertrag", "eintrittskarte", "impfpass",
        "gehaltsabrechnung", "buchungsbestaetigung", "zeugnis", "urkunde", "laborbefund",
    )

    /** Splits text into lowercase words with umlauts folded (ä->ae, ß->ss). */
    fun words(text: String): List<String> {
        val folded = text.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        val plain = Normalizer.normalize(folded, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
    }

    fun categorize(f: FileEntry): FileCategory {
        val ext = f.ext
        if (ext in IMAGE_EXT) return FileCategory.OTHER // photos are handled by the photo part

        // Topics only make sense for document-like files
        if (ext in DOC_EXT || ext in SHEET_EXT || ext in SLIDE_EXT) {
            topicOf(f)?.let { return it }
        }
        return when (ext) {
            in DOC_EXT -> FileCategory.DOCUMENTS
            in SHEET_EXT -> FileCategory.SPREADSHEETS
            in SLIDE_EXT -> FileCategory.PRESENTATIONS
            in EBOOK_EXT -> FileCategory.EBOOKS
            in ARCHIVE_EXT -> FileCategory.ARCHIVES
            in AUDIO_EXT -> FileCategory.AUDIO
            in VIDEO_EXT -> FileCategory.VIDEOS
            in INSTALLER_EXT -> FileCategory.INSTALLERS
            else -> FileCategory.OTHER
        }
    }

    private fun topicOf(f: FileEntry): FileCategory? {
        val nameWords = words(f.baseName).toSet()
        // Also catch compound words in names like "Stromrechnung_2026" or "Handyvertrag"
        val nameJoined = words(f.baseName).joinToString("")
        val textWords = words(f.text)

        val scores = HashMap<FileCategory, Double>()
        for ((cat, keys) in topics) {
            var s = 0.0
            for (k in keys) {
                if (k in nameWords) s += if (k in strongNameWords) 3.0 else 1.0
                else if (k.length >= 7 && k in strongNameWords && nameJoined.contains(k)) s += 3.0
            }
            if (textWords.isNotEmpty()) {
                val hits = textWords.count { it in keys }
                val distinct = textWords.filter { it in keys }.toSet().size
                s += minOf(hits, 6) * 0.5 + distinct * 0.5
            }
            if (s > 0) scores[cat] = s
        }
        val best = scores.maxByOrNull { it.value } ?: return null
        return if (best.value >= 3.0) best.key else null
    }

    private val copySuffix = Regex("""(\s?\(\d+\)|[-_ ]copy|[-_ ]kopie|-\d)$""", RegexOption.IGNORE_CASE)

    fun looksLikeCopy(f: FileEntry) = copySuffix.containsMatchIn(f.baseName)

    /**
     * Junk suggestions. Thresholds follow the same Careful/Normal/Thorough setting as photos.
     */
    fun junk(files: List<FileEntry>, sensitivity: Sensitivity, nowMs: Long): Map<String, List<FileJunkReason>> {
        val day = 86_400_000L
        val apkDays = when (sensitivity) { Sensitivity.CAREFUL -> 90L; Sensitivity.NORMAL -> 30L; Sensitivity.THOROUGH -> 7L }
        val inviteDays = when (sensitivity) { Sensitivity.CAREFUL -> 365L; Sensitivity.NORMAL -> 180L; Sensitivity.THOROUGH -> 60L }

        val out = HashMap<String, MutableList<FileJunkReason>>()
        fun flag(p: String, r: FileJunkReason) { out.getOrPut(p) { mutableListOf() }.let { if (r !in it) it += r } }

        for (f in files) {
            val ageDays = (nowMs - f.modifiedMs) / day
            val ext = f.ext
            when {
                ext in PARTIAL_EXT && ageDays >= 1 -> flag(f.path, FileJunkReason.PARTIAL)
                f.sizeBytes == 0L && ageDays >= 1 -> flag(f.path, FileJunkReason.EMPTY)
                ext in INSTALLER_EXT && ageDays >= apkDays -> flag(f.path, FileJunkReason.OLD_INSTALLER)
                ext in INVITE_EXT && ageDays >= inviteDays -> flag(f.path, FileJunkReason.OLD_INVITE)
                ext in ARCHIVE_EXT && sensitivity != Sensitivity.CAREFUL && ageDays >= 7 &&
                    f.siblingDirs.any { it.equals(archiveStem(f.name), ignoreCase = true) } ->
                    flag(f.path, FileJunkReason.EXTRACTED)
            }
        }

        // Exact duplicates: same content hash. Keep the best copy.
        files.filter { it.contentHash != null && it.sizeBytes > 0 }
            .groupBy { it.contentHash }
            .values.filter { it.size > 1 }
            .forEach { group ->
                val keep = group.sortedWith(
                    compareByDescending<FileEntry> { it.isSorted }
                        .thenBy { looksLikeCopy(it) }
                        .thenBy { it.modifiedMs }
                        .thenBy { it.name.length }
                ).first()
                group.filter { it !== keep }.forEach { flag(it.path, FileJunkReason.DUPLICATE) }
            }
        return out
    }

    /** "photos.tar.gz" -> "photos", "stuff.zip" -> "stuff" */
    fun archiveStem(name: String): String {
        var n = name
        repeat(2) { if ('.' in n && n.substringAfterLast('.').lowercase() in ARCHIVE_EXT) n = n.substringBeforeLast('.') }
        return n
    }

    /** Puts "file.pdf" into a folder without overwriting: "file (2).pdf", "file (3).pdf"… */
    fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val base = if ('.' in name) name.substringBeforeLast('.') else name
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var i = 2
        while (exists("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }
}
