package app.phototidy.checks.widget

import app.phototidy.core.*
var fails = 0
fun check(c: Boolean, m: String) { if (!c) { println("FAIL: $m"); fails++ } else println("ok: $m") }
fun h(pkg: String, label: String) = HomeApp("$pkg/.Main", pkg, label)
fun main() {
    val apps = listOf(h("com.whatsapp","WhatsApp"), h("org.telegram.messenger","Telegram"), h("org.thoughtcrime.securesms","Signal"),
        h("com.spotify.music","Spotify"), h("com.netflix.mediaclient","Netflix"), h("com.random.util","Flashlight"))
    val usage = mapOf("org.thoughtcrime.securesms" to 50L, "com.whatsapp" to 300L, "com.netflix.mediaclient" to 10L)
    val chats = HomeRules.folderApps(apps, HomeFolder.CHATS, usage)
    check(chats.map { it.label } == listOf("WhatsApp","Signal","Telegram"), "folder sorted by usage then name: ${chats.map{it.label}}")
    val rows = HomeRules.dashboardRows(apps, usage)
    println(rows.map { it.title + "=" + it.apps.map { a -> a.label } })
    check(rows[0].title == "Most used" && rows[0].apps.map { it.label } == listOf("WhatsApp","Signal","Netflix"), "most used row")
    check(rows.drop(1).flatMap { it.apps }.none { it.label in setOf("WhatsApp","Signal","Netflix") }, "no repeats")
    check(rows.drop(1).sumOf { it.apps.size } == 3, "rest all present")
    val noUsage = HomeRules.dashboardRows(apps, emptyMap())
    check(noUsage.none { it.title == "Most used" } && noUsage.sumOf { it.apps.size } == 6, "without usage: only folders")
    check(HomeRules.folderApps(apps, HomeFolder.CHATS, emptyMap(), mapOf("com.whatsapp" to HomeFolder.TOOLS)).none { it.label == "WhatsApp" }, "override respected")
    println(if (fails == 0) "ALL PASSED" else "$fails FAILED")
}
