package app.phototidy.checks.health

import app.phototidy.core.*

var fails = 0
fun check(c: Boolean, m: String) { if (!c) { println("FAIL: $m"); fails++ } else println("ok: $m") }

fun main() {
    // ---- WhatsApp
    val c = WhatsAppRules::categorize
    check(c("WhatsApp Images/IMG-2026.jpg") == WaCategory.IMAGES, "received image")
    check(c("WhatsApp Images/Sent/IMG-2026.jpg") == WaCategory.SENT_IMAGES, "sent image")
    check(c("WhatsApp Images/Private/x.jpg") == null, "private folder untouched")
    check(c("WhatsApp Video/Sent/v.mp4") == WaCategory.SENT_VIDEOS, "sent video")
    check(c("WhatsApp Voice Notes/202642/PTT-1.opus") == WaCategory.VOICE, "voice note in week folder")
    check(c("WhatsApp Stickers/a.webp") == WaCategory.STICKERS, "sticker")
    check(c(".Statuses/abc.mp4") == WaCategory.STATUSES, "status")
    check(c("WhatsApp Profile Photos/p.jpg") == null, "profile photos untouched")
    check(c("WhatsApp Images/.nomedia") == null, "nomedia untouched")
    check(c("WhatsApp Business Images/IMG.jpg") == WaCategory.IMAGES, "business image")
    check(c("WhatsApp Animated Gifs/g.mp4") == WaCategory.GIFS, "gif")
    check(c("WhatsApp Documents/r.pdf") == WaCategory.DOCUMENTS, "document")
    val now = 1_760_000_000_000L; val d = 86_400_000L
    val files = listOf(
        WaFile("a", 100, now - 200 * d, WaCategory.VOICE), WaFile("b", 50, now - 2 * d, WaCategory.VOICE),
        WaFile("c", 500, now - 100 * d, WaCategory.STICKERS),
    )
    val g = WhatsAppRules.group(files, 90, now)
    check(g.size == 2 && g.first { it.category == WaCategory.VOICE }.files.size == 1, "age filter keeps old only")
    check(WhatsAppRules.group(files, 0, now).sumOf { it.bytes } == 650L, "no age filter = all")

    // ---- Privacy
    check(PermGroup.of("android.permission.RECORD_AUDIO") == PermGroup.MICROPHONE, "perm mapping")
    fun p(pkg: String, label: String, perms: Set<PermGroup>, unused: Long? = 1, sys: Boolean = false) =
        PrivacyApp(pkg, label, sys, perms, unused)
    val wa = p("com.whatsapp", "WhatsApp", setOf(PermGroup.CAMERA, PermGroup.MICROPHONE, PermGroup.CONTACTS))
    check(PrivacyRules.assess(wa) == null, "whatsapp normal permissions -> no finding")
    val torch = p("com.torch.light", "Flashlight", setOf(PermGroup.MICROPHONE, PermGroup.CONTACTS))
    check(PrivacyRules.assess(torch)?.reasons?.any { it.contains("microphone") } == true, "flashlight with mic flagged")
    val oldGame = p("com.game.x", "Old Game", setOf(PermGroup.LOCATION, PermGroup.PHOTOS), unused = 120)
    check(PrivacyRules.assess(oldGame)?.reasons?.first()?.contains("120 days") == true, "unused app with location flagged")
    val bgLoc = p("com.shop.x", "Shop", setOf(PermGroup.LOCATION_BACKGROUND, PermGroup.LOCATION))
    check(PrivacyRules.assess(bgLoc) != null, "background location flagged")
    val maps = p("com.google.android.apps.maps", "Maps", setOf(PermGroup.LOCATION_BACKGROUND, PermGroup.LOCATION))
    check(PrivacyRules.assess(maps) == null, "maps background location expected")
    check(PrivacyRules.assess(p("android.x", "Sys", setOf(PermGroup.CAMERA), 999, sys = true)) == null, "system apps skipped")
    val byP = PrivacyRules.byPermission(listOf(wa, torch))
    check(byP.first { it.first == PermGroup.MICROPHONE }.second.size == 2, "by permission groups")
    check(PrivacyRules.findings(listOf(wa, torch, oldGame, bgLoc)).size == 3, "findings list")

    // ---- Battery
    fun b(pkg: String, label: String, fg: Long, bg: Long, unr: Boolean = false, sys: Boolean = false) =
        BatteryApp(pkg, label, sys, fg, bg, unr)
    check(BatteryRules.assess(b("com.shop", "Shop", 10, 600, unr = true))?.reason?.contains("10 h") == true, "heavy bg flagged")
    check(BatteryRules.assess(b("com.spotify.music", "Spotify", 10, 900)) == null, "music bg expected")
    check(BatteryRules.assess(b("com.chat", "Chat", 600, 30)) == null, "little bg fine")
    check(BatteryRules.assess(b("com.sys", "Sys", 0, 999, sys = true)) == null, "system skipped")
    val list = BatteryRules.findings(listOf(b("a", "A", 10, 300), b("b", "B", 10, 900, unr = true)))
    check(list.map { it.app.pkg } == listOf("b", "a"), "ranked worst first")
    println(if (fails == 0) "ALL PASSED" else "$fails FAILED")
}
