package app.phototidy.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin image measurements. Everything works on a grayscale buffer
 * (values 0..255, row-major, width * height), so it can be tested off-device.
 */
object ImageMath {

    /** Converts ARGB pixels (as returned by Bitmap.getPixels) to 0..255 luminance. */
    fun toGray(argb: IntArray): IntArray {
        val out = IntArray(argb.size)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma, integer arithmetic
            out[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return out
    }

    /**
     * Variance of the Laplacian: the classic "is this photo in focus" score.
     * Sharp photos have strong edges -> high variance. Blurry ones -> low.
     */
    fun laplacianVariance(gray: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = (gray[i - w] + gray[i + w] + gray[i - 1] + gray[i + 1] - 4 * gray[i]).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /**
     * Focus score that forgives intentionally soft backgrounds (portrait mode,
     * bokeh): the image is split into a 4x4 grid and the sharpest tile counts.
     * A photo is only "blurry" if nothing in it is sharp.
     */
    fun sharpness(gray: IntArray, w: Int, h: Int, grid: Int = 4): Double {
        val tw = w / grid
        val th = h / grid
        if (tw < 8 || th < 8) return laplacianVariance(gray, w, h)
        var best = 0.0
        val tile = IntArray(tw * th)
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                for (y in 0 until th) {
                    System.arraycopy(gray, (gy * th + y) * w + gx * tw, tile, y * tw, tw)
                }
                best = max(best, laplacianVariance(tile, tw, th))
            }
        }
        return best
    }

    /** Mean brightness and standard deviation (contrast) of the image. */
    fun meanAndStd(gray: IntArray): Pair<Double, Double> {
        if (gray.isEmpty()) return 0.0 to 0.0
        var sum = 0.0
        var sumSq = 0.0
        for (v in gray) {
            sum += v
            sumSq += v.toDouble() * v
        }
        val mean = sum / gray.size
        val variance = max(0.0, sumSq / gray.size - mean * mean)
        return mean to sqrt(variance)
    }

    /** Box-average downscale of a grayscale buffer to tw x th. */
    fun resize(gray: IntArray, w: Int, h: Int, tw: Int, th: Int): IntArray {
        val out = IntArray(tw * th)
        for (ty in 0 until th) {
            val y0 = ty * h / th
            val y1 = max(y0 + 1, min(h, (ty + 1) * h / th))
            for (tx in 0 until tw) {
                val x0 = tx * w / tw
                val x1 = max(x0 + 1, min(w, (tx + 1) * w / tw))
                var s = 0L
                var c = 0
                for (y in y0 until y1) {
                    val row = y * w
                    for (x in x0 until x1) {
                        s += gray[row + x]
                        c++
                    }
                }
                out[ty * tw + tx] = if (c == 0) 0 else (s / c).toInt()
            }
        }
        return out
    }

    /**
     * 64-bit difference hash. Visually near-identical photos (same scene,
     * re-saved, slightly re-compressed) end up a few bits apart.
     */
    fun dHash(gray: IntArray, w: Int, h: Int): Long {
        val small = resize(gray, w, h, 9, 8)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (small[y * 9 + x] > small[y * 9 + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
