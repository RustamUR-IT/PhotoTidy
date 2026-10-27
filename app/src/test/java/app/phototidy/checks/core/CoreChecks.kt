package app.phototidy.checks.core

import app.phototidy.core.*
import kotlin.random.Random

fun check(c: Boolean, msg: String) { if (!c) { println("FAIL: $msg"); failures++ } else println("ok: $msg") }
var failures = 0

fun blur(g: IntArray, w: Int, h: Int, r: Int): IntArray {
    val o = IntArray(g.size)
    for (y in 0 until h) for (x in 0 until w) { var s=0; var c=0
        for (dy in -r..r) for (dx in -r..r) { val yy=y+dy; val xx=x+dx; if (yy in 0 until h && xx in 0 until w) { s+=g[yy*w+xx]; c++ } }
        o[y*w+x]=s/c }
    return o
}

fun main() {
    val w = 256; val h = 192; val rnd = Random(1)
    // synthetic "photo": shapes + texture
    val sharp = IntArray(w*h) { i -> val x=i%w; val y=i/w; ((if ((x/16+y/16)%2==0) 200 else 60) + rnd.nextInt(-20,20)).coerceIn(0,255) }
    val blurry = blur(sharp, w, h, 4)
    val ls = ImageMath.sharpness(sharp,w,h); val lb = ImageMath.sharpness(blurry,w,h)
    val veryBlurry = blur(sharp,w,h,6); val lv = ImageMath.sharpness(veryBlurry,w,h)
    // portrait: sharp subject in centre, heavy blur elsewhere
    val portrait = veryBlurry.copyOf(); for (y in 70 until 120) for (x in 100 until 160) portrait[y*w+x]=sharp[y*w+x]
    val lp = ImageMath.sharpness(portrait,w,h)
    println("sharp=$ls blurry=$lb veryBlurry=$lv portrait=$lp")
    check(ls > 1000, "sharp scores high")
    check(lv < 40, "heavily blurred scores under normal threshold")
    check(lp > 200, "portrait with sharp subject not considered blurry")
    val dark = IntArray(w*h) { rnd.nextInt(0,8) }
    val (m, s) = ImageMath.meanAndStd(dark)
    check(m < 10 && s < 5, "dark frame measured dark")
    val hA = ImageMath.dHash(sharp,w,h)
    val noisy = sharp.map { (it + rnd.nextInt(-6,6)).coerceIn(0,255) }.toIntArray()
    val hB = ImageMath.dHash(noisy,w,h)
    val other = IntArray(w*h) { i -> (i%w) }  // gradient
    val hC = ImageMath.dHash(other,w,h)
    println("dist same=${ImageMath.hamming(hA,hB)} diff=${ImageMath.hamming(hA,hC)}")
    check(ImageMath.hamming(hA,hB) <= 5, "near duplicate hashes close")
    check(ImageMath.hamming(hA,hC) > 10, "different images hash far")
    check(ImageMath.toGray(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())).contentEquals(intArrayOf(255,0)), "toGray")

    // Categorizer
    check(Categorizer.categorize(listOf(Label("Dog",0.9f), Label("Pet",0.8f)),0,false)==Category.PETS, "dog -> pets")
    check(Categorizer.categorize(listOf(Label("Pizza",0.85f)),0,false)==Category.FOOD, "pizza -> food")
    check(Categorizer.categorize(listOf(Label("Sky",0.6f)),0,false)==Category.OTHER, "weak sky -> other")
    check(Categorizer.categorize(listOf(Label("Sky",0.8f), Label("Mountain",0.7f)),0,false)==Category.NATURE, "sky+mountain -> nature")
    check(Categorizer.categorize(emptyList(),300,false)==Category.DOCUMENTS, "lots of text -> documents")
    check(Categorizer.categorize(listOf(Label("Paper",0.8f)),50,false)==Category.DOCUMENTS, "paper + some text -> documents")
    check(Categorizer.categorize(listOf(Label("Font",0.9f)),0,false)==Category.OTHER, "font label alone not documents")
    check(Categorizer.categorize(listOf(Label("Dog",0.9f)),0,true)==Category.SCREENSHOTS, "screenshot wins")

    // Junk
    val now = 1_760_000_000_000L
    fun f(id: Long, t: Long, sharp: Double=150.0, br: Double=120.0, con: Double=50.0, hash: Long=id*0x9E3779B97F4A7C15uL.toLong(), ss: Boolean=false, fav: Boolean=false, wd:Int=4000, ht:Int=3000) =
        Features(id, t, wd, ht, sharp, br, con, hash, ss, fav)
    val items = listOf(
        f(1, now-1000_000), // good
        f(2, now-900_000, sharp=10.0), // blurry
        f(3, now-800_000, br=5.0), // dark
        f(4, now-700_000, con=2.0), // blank
        f(5, now-400*86_400_000L, ss=true), // old screenshot
        f(6, now-5*86_400_000L, ss=true), // fresh screenshot
        f(7, now-100_000, hash=0x0F0F0F0F0F0F0F0FL, sharp=200.0), // burst keep
        f(8, now-95_000, hash=0x0F0F0F0F0F0F0F0EL, sharp=120.0), // burst dup
        f(9, now-90_000, hash=0x0F0F0F0F0F0F0F0CL, sharp=90.0, fav=true), // fav never junk
        f(10, now-50*86_400_000L, hash=0x123456789ABCDEFL), // exact copy far apart
        f(11, now-10*86_400_000L, hash=0x123456789ABCDEFL, wd=1000, ht=750),
        f(12, now-2*86_400_000L, sharp=10.0, fav=true), // blurry fav
        f(13, now-3*86_400_000L, wd=100, ht=100), // tiny
    )
    val j = JunkDetector.detect(items, Sensitivity.NORMAL, now)
    println(j)
    check(1L !in j, "good photo kept")
    check(j[2]==listOf(JunkReason.BLURRY), "blurry flagged")
    check(j[3]==listOf(JunkReason.DARK), "dark flagged")
    check(j[4]==listOf(JunkReason.BLANK), "blank flagged")
    check(j[5]==listOf(JunkReason.OLD_SCREENSHOT), "old screenshot flagged")
    check(6L !in j, "fresh screenshot kept")
    check(9L !in j, "favourite never flagged")
    check(7L in j && 8L in j, "with favourite in burst, non-favourites flagged")
    check(10L !in j && j[11]==listOf(JunkReason.DUPLICATE), "exact copy: bigger kept, smaller flagged")
    check(12L !in j, "blurry favourite kept")
    check(j[13]==listOf(JunkReason.TINY), "tiny flagged")
    val careful = JunkDetector.detect(items, Sensitivity.CAREFUL, now)
    check(careful.size <= j.size, "careful flags no more than normal")

    // Burst without favourite: keep sharpest
    val b = JunkDetector.detect(listOf(f(20, now-10_000, hash=0x7L, sharp=300.0), f(21, now-5_000, hash=0x6L, sharp=100.0)), Sensitivity.NORMAL, now)
    check(20L !in b && b[21]==listOf(JunkReason.DUPLICATE), "burst keeps sharpest")

    // Events
    val base = 1_755_000_000_000L
    val shots = (0 until 20).map { EventGrouper.Shot(it.toLong(), base + it*600_000L) } +
        (0 until 5).map { EventGrouper.Shot(100L+it, base + 86_400_000L*3 + it*60_000L) }
    val ev = EventGrouper.group(shots, zone = java.time.ZoneId.of("Europe/Vienna"))
    println(ev.map { it.defaultName + " " + it.photoIds.size })
    check(ev.size==1 && ev[0].photoIds.size==20, "one event of 20, small run ignored")
    check(EventGrouper.safeFolderName("a/b:c  d")=="a b c d", "safe folder name")
    println(if (failures==0) "ALL PASSED" else "$failures FAILED")
}
