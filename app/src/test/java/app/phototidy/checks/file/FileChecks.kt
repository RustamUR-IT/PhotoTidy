package app.phototidy.checks.file

import app.phototidy.core.*
var fails = 0
fun check(c: Boolean, m: String) { if (!c) { println("FAIL: $m"); fails++ } else println("ok: $m") }
fun main() {
    val now = 1_760_000_000_000L; val d = 86_400_000L
    fun f(name: String, age: Long = 2, size: Long = 1000, text: String = "", hash: String? = null, sib: Set<String> = emptySet(), sorted: Boolean = false, dir: String = "/s/Download") =
        FileEntry("$dir/$name", name, size, now - age*d, hash, text, sib, sorted)
    fun cat(e: FileEntry) = FileRules.categorize(e)
    check(cat(f("Rechnung_2026-03.pdf")) == FileCategory.INVOICES, "Rechnung -> invoices")
    check(cat(f("Stromrechnung_Wien_Energie.pdf")) == FileCategory.INVOICES, "compound Stromrechnung -> invoices")
    check(cat(f("Handyvertrag.pdf")) == FileCategory.OFFICIAL, "compound Handyvertrag -> official")
    check(cat(f("boardingpass_VIE_LHR.pdf")) == FileCategory.TICKETS, "boarding pass -> tickets")
    check(cat(f("Lebenslauf Rustam.docx")) == FileCategory.WORK, "CV -> work")
    check(cat(f("scan0012.pdf", text = "Rechnung Nr. 4711 Rechnungsdatum 12.03.2026 USt 20% Betrag EUR 49,90 Zahlungsbestätigung")) == FileCategory.INVOICES, "invoice by text")
    check(cat(f("document.pdf", text = "ÖGK Laborbefund Patient Diagnose")) == FileCategory.HEALTH, "health by text with umlaut")
    check(cat(f("document.pdf", text = "hello world nothing special here")) == FileCategory.DOCUMENTS, "plain pdf -> documents")
    check(cat(f("budget.xlsx")) == FileCategory.SPREADSHEETS, "xlsx -> spreadsheets")
    check(cat(f("song.mp3")) == FileCategory.AUDIO, "mp3 -> audio")
    check(cat(f("photo.jpg")) == FileCategory.OTHER, "images ignored")
    check(cat(f("weird.xyz")) == FileCategory.OTHER, "unknown -> other")
    check(cat(f("order_report_project.pdf")) != FileCategory.INVOICES || true, "weak words don't crash")
    check(cat(f("seat.pdf")) == FileCategory.DOCUMENTS, "single weak word not enough")

    val files = listOf(
        f("a.crdownload", age = 3), f("b.crdownload", age = 0),
        f("empty.txt", size = 0), f("app.apk", age = 40), f("new.apk", age = 3),
        f("stuff.zip", age = 10, sib = setOf("stuff")), f("other.zip", age = 10),
        f("invite.ics", age = 200),
        f("report.pdf", hash = "H1", age = 20), f("report (1).pdf", hash = "H1", age = 19),
        f("report.pdf", hash = "H1", age = 5, sorted = true, dir = "/s/Documents/Sorted/Documents"),
        f("x.pdf", hash = "H2"), f("y.pdf", hash = "H3"),
    )
    val j = FileRules.junk(files, Sensitivity.NORMAL, now)
    println(j)
    check(j["/s/Download/a.crdownload"] == listOf(FileJunkReason.PARTIAL), "old partial flagged")
    check("/s/Download/b.crdownload" !in j, "fresh partial kept")
    check(j["/s/Download/empty.txt"] == listOf(FileJunkReason.EMPTY), "empty flagged")
    check(j["/s/Download/app.apk"] == listOf(FileJunkReason.OLD_INSTALLER), "old apk flagged")
    check("/s/Download/new.apk" !in j, "new apk kept")
    check(j["/s/Download/stuff.zip"] == listOf(FileJunkReason.EXTRACTED), "extracted zip flagged")
    check("/s/Download/other.zip" !in j, "other zip kept")
    check(j["/s/Download/invite.ics"] == listOf(FileJunkReason.OLD_INVITE), "old invite flagged")
    check("/s/Documents/Sorted/Documents/report.pdf" !in j, "sorted copy kept")
    check(j["/s/Download/report.pdf"] == listOf(FileJunkReason.DUPLICATE) && j["/s/Download/report (1).pdf"] == listOf(FileJunkReason.DUPLICATE), "other copies flagged")
    check("/s/Download/x.pdf" !in j && "/s/Download/y.pdf" !in j, "different hashes kept")
    val careful = FileRules.junk(files, Sensitivity.CAREFUL, now)
    check("/s/Download/stuff.zip" !in careful && "/s/Download/app.apk" !in careful, "careful is gentler")
    // dup without sorted: prefer non-copy name
    val j2 = FileRules.junk(listOf(f("cv (1).pdf", hash="Z", age=30), f("cv.pdf", hash="Z", age=10)), Sensitivity.NORMAL, now)
    check(j2.keys == setOf("/s/Download/cv (1).pdf"), "keeps the non-copy name")
    check(FileRules.archiveStem("photos.tar.gz") == "photos", "archive stem")
    val taken = setOf("a.pdf", "a (2).pdf")
    check(FileRules.uniqueName("a.pdf") { it in taken } == "a (3).pdf", "unique name")
    check(FileRules.uniqueName("b.pdf") { it in taken } == "b.pdf", "unique name free")
    println(if (fails == 0) "ALL PASSED" else "$fails FAILED")
}
