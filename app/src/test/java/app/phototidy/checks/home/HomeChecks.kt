package app.phototidy.checks.home

import app.phototidy.core.*
var fails = 0
fun check(c: Boolean, m: String) { if (!c) { println("FAIL: $m"); fails++ } else println("ok: $m") }
fun h(pkg: String, label: String, cat: Int = -1, sys: Boolean = false) = HomeApp("$pkg/.Main", pkg, label, cat, sys)
fun f(pkg: String, label: String, cat: Int = -1, sys: Boolean = false) = HomeRules.folderFor(h(pkg, label, cat, sys))
fun main() {
    check(f("com.whatsapp","WhatsApp") == HomeFolder.CHATS, "whatsapp chats")
    check(f("at.erstebank.george","George") == HomeFolder.MONEY, "george money")
    check(f("at.oebb.ts","ÖBB") == HomeFolder.TRAVEL, "oebb travel")
    check(f("at.willhaben","willhaben") == HomeFolder.SHOPPING, "willhaben shopping")
    check(f("com.nothing.camera","Camera", sys = true) == HomeFolder.PHOTOS, "camera photos")
    check(f("com.x8bit.bitwarden","Bitwarden") == HomeFolder.TOOLS, "password manager not work")
    check(f("com.facebook.lite","Facebook Lite") == HomeFolder.SOCIAL, "fb lite social")
    check(f("com.king.candy","Candy Crush", cat = 0) == HomeFolder.GAMES, "game by category")
    check(f("com.google.android.apps.fitness","Fit") == HomeFolder.HEALTH, "google fit health")
    check(f("com.google.android.googlequicksearchbox","Google") == HomeFolder.GOOGLE, "google app")
    check(f("com.nothing.weird","Essential Space", sys = true) == HomeFolder.SYSTEM, "nothing system")
    check(f("com.random.util","Flashlight") == HomeFolder.TOOLS, "fallback tools")
    check(f("com.android.dialer","Phone", sys = true) == HomeFolder.SYSTEM, "dialer system")
    check(HomeRules.folderFor(h("com.spotify.music","Spotify"), mapOf("com.spotify.music" to HomeFolder.TOOLS)) == HomeFolder.TOOLS, "manual override wins")
    val apps = listOf(h("a.dialer","Phone"), h("b.sms","Messages"), h("com.whatsapp","WhatsApp"), h("com.spotify.music","Spotify"), h("c.browser","Chrome"))
    val dock = HomeRules.pickDock(apps, mapOf("com.whatsapp" to 300L, "com.spotify.music" to 100L), listOf("a.dialer","b.sms","c.browser"))
    check(dock == listOf("com.whatsapp/.Main","com.spotify.music/.Main","a.dialer/.Main","b.sms/.Main"), "dock = most used + defaults")
    val arr = HomeRules.arrange(apps, dock)
    check(arr.values.flatten().none { it.key in dock } && arr.values.flatten().size == 1, "dock apps not in folders")
    println(if (fails == 0) "ALL PASSED" else "$fails FAILED")
}
