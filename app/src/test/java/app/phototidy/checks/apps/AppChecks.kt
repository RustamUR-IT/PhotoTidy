package app.phototidy.checks.apps

import app.phototidy.core.*
var fails = 0
fun check(c: Boolean, m: String) { if (!c) { println("FAIL: $m"); fails++ } else println("ok: $m") }
fun main() {
    val now = 1_760_000_000_000L; val d = 86_400_000L
    fun a(pkg: String, label: String, sys: Boolean = false, role: String? = null, admin: Boolean = false, cat: Int = -1,
          inst: Long = now - 400*d, last: Long? = now - d, known: Boolean = true) =
        AppFacts(pkg, label, sys, cat, null, role, admin, inst, last, known)
    fun v(x: AppFacts) = AppRules.verdict(x)
    check(v(a("com.whatsapp","WhatsApp")).let { it.importance == Importance.IMPORTANT && it.reminder!!.contains("Back") }, "whatsapp important w/ backup reminder")
    check(v(a("com.spotify.music","Spotify")).let { it.importance == Importance.NORMAL && it.reminder == null && it.whatItDoes.contains("music") }, "spotify normal")
    check(v(a("at.erstebank.george","George")).importance == Importance.IMPORTANT, "george bank by keyword")
    check(v(a("com.example.mybank","My Bank")).whatItDoes.contains("banking"), "bank keyword description")
    check(v(a("com.foo.totp","Codes")).importance == Importance.IMPORTANT, "totp keyword")
    check(v(a("at.gv.oe.app","Digitales Amt")).importance == Importance.IMPORTANT, "government app")
    check(v(a("com.nothing.launcher","Launcher", sys = true, role = "home screen")).importance == Importance.ESSENTIAL, "default launcher essential")
    check(v(a("com.foo.find","Finder", admin = true)).importance == Importance.ESSENTIAL, "device admin essential")
    check(v(a("com.android.settings","Settings", sys = true)).importance == Importance.ESSENTIAL, "settings essential")
    check(v(a("com.android.foo","Foo", sys = true)).importance == Importance.ESSENTIAL, "android system pkg essential")
    check(v(a("com.vendor.weather","Weather", sys = true)).let { it.importance == Importance.NORMAL && it.reminder!!.contains("disabled") }, "preinstalled reminder about disabling")
    check(v(a("com.game.x","Fun Game", cat = 0)).whatItDoes == "Fun Game – a game", "category fallback")
    check(v(a("com.random.app","Random")).whatItDoes == "Random", "plain fallback")
    // unused
    val s = Sensitivity.NORMAL
    check(AppRules.unusedForDays(a("x","X", last = now - 100*d), s, now) == 100L, "unused 100 days")
    check(AppRules.unusedForDays(a("x","X", last = now - 10*d), s, now) == null, "recently used")
    check(AppRules.unusedForDays(a("x","X", last = null, inst = now - 200*d), s, now) == 200L, "never opened since install")
    check(AppRules.unusedForDays(a("x","X", last = null, inst = now - 20*d), s, now) == null, "new app not suggested")
    check(AppRules.unusedForDays(a("x","X", last = now - 100*d, known = false), s, now) == null, "no usage access -> no claim")
    check(AppRules.unusedForDays(a("x","X", sys = true, last = now - 300*d), s, now) == null, "system apps not suggested")
    println(if (fails == 0) "ALL PASSED" else "$fails FAILED")
}
