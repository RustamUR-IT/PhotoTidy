package app.phototidy.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import app.phototidy.core.Categorizer
import app.phototidy.core.ImageMath
import app.phototidy.core.Label
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Looks at one photo entirely on the phone: focus, brightness, a visual
 * fingerprint for duplicates, what's in it (ML Kit labels) and whether it's
 * mostly text (ML Kit OCR). Nothing leaves the device.
 */
class Analyzer(private val context: Context) : Closeable {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun analyze(item: MediaItem): AnalysisRecord? {
        val thumb = loadBitmap(item, 512) ?: return null
        try {
            // Normalise size so focus scores are comparable between photos.
            val metricBmp = scaleTo(thumb, 384)
            val w = metricBmp.width
            val h = metricBmp.height
            val px = IntArray(w * h)
            metricBmp.getPixels(px, 0, w, 0, 0, w, h)
            val gray = ImageMath.toGray(px)
            val sharpness = ImageMath.sharpness(gray, w, h)
            val (brightness, contrast) = ImageMath.meanAndStd(gray)
            val hash = ImageMath.dHash(gray, w, h)
            if (metricBmp !== thumb) metricBmp.recycle()

            val labels = try {
                Tasks.await(labeler.process(InputImage.fromBitmap(thumb, 0)), 20, TimeUnit.SECONDS)
                    .map { Label(it.text, it.confidence) }
                    .sortedByDescending { it.confidence }
                    .take(8)
            } catch (e: Exception) {
                Log.w(TAG, "labeling failed for ${item.uri}", e)
                emptyList()
            }

            // OCR only when the photo isn't obviously a person/pet/meal – saves a lot of time.
            var ocrChars = 0
            if (!item.isScreenshot && !Categorizer.isClearlyVisualSubject(labels)) {
                val big = loadBitmap(item, 1024)
                if (big != null) {
                    ocrChars = try {
                        Tasks.await(textRecognizer.process(InputImage.fromBitmap(big, 0)), 20, TimeUnit.SECONDS)
                            .text.count { !it.isWhitespace() }
                    } catch (e: Exception) {
                        Log.w(TAG, "ocr failed for ${item.uri}", e)
                        0
                    } finally {
                        big.recycle()
                    }
                }
            }

            return AnalysisRecord(
                id = item.id,
                dateModified = item.dateModified,
                sharpness = sharpness,
                brightness = brightness,
                contrast = contrast,
                dHash = hash,
                ocrChars = ocrChars,
                category = Categorizer.categorize(labels, ocrChars, item.isScreenshot),
                labels = labels,
            )
        } finally {
            thumb.recycle()
        }
    }

    private fun loadBitmap(item: MediaItem, maxSide: Int): Bitmap? = try {
        val b = context.contentResolver.loadThumbnail(item.uri, Size(maxSide, maxSide), null)
        if (b.config == Bitmap.Config.HARDWARE) {
            val copy = b.copy(Bitmap.Config.ARGB_8888, false)
            b.recycle()
            copy
        } else b
    } catch (e: Exception) {
        Log.w(TAG, "could not load ${item.uri}", e)
        null
    }

    private fun scaleTo(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * f).roundToInt()),
            max(1, (src.height * f).roundToInt()),
            true
        )
    }

    override fun close() {
        labeler.close()
        textRecognizer.close()
    }

    companion object {
        private const val TAG = "Analyzer"
    }
}
