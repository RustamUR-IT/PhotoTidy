package app.phototidy

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers crashes on the phone so you can send them to whoever fixes the app.
 * Nothing is sent automatically – you share the report yourself from Settings.
 */
object CrashLog {
    private const val MAX_BYTES = 24_000

    private fun file(c: Context) = File(c.filesDir, "crash.log")

    fun install(c: Context) {
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { record(app, thread.name, error) } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(c: Context, where: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val version = try {
            c.packageManager.getPackageInfo(c.packageName, 0).versionName
        } catch (_: Exception) { "?" }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "=== $time · v$version · Android ${Build.VERSION.RELEASE} · ${Build.MODEL} · $where\n$sw\n"
        val f = file(c)
        val old = if (f.exists()) f.readText() else ""
        // newest first, keep the file small
        f.writeText((entry + old).take(MAX_BYTES))
    }

    fun read(c: Context): String? = file(c).takeIf { it.exists() && it.length() > 0 }?.readText()

    fun count(c: Context): Int = read(c)?.split("\n=== ")?.size ?: 0

    fun clear(c: Context) { file(c).delete() }

    /** Opens the share sheet (WhatsApp, mail…) with the report as text. */
    fun share(c: Context) {
        val text = read(c) ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "System Optimizer crash report")
            .putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(send, "Send crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
