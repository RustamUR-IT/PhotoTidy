package app.phototidy.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.phototidy.MainActivity
import app.phototidy.R
import app.phototidy.data.FilePlaces
import app.phototidy.data.FilePlan
import app.phototidy.data.FilePlanner
import app.phototidy.data.FileRepo
import app.phototidy.data.Plan
import app.phototidy.data.Planner
import app.phototidy.data.Scanner
import app.phototidy.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Scans the gallery. Started either by the "Scan" button (runs as a visible
 * foreground job so a long first scan isn't killed) or nightly while charging.
 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (manual) {
            try {
                setForeground(foregroundInfo(0, 0))
            } catch (_: Exception) {
                // Not allowed right now (e.g. app went to background) – just run normally.
            }
        }

        val ctx = applicationContext
        val photoAccess = hasPhotoAccess(ctx)
        val fileAccess = Settings(ctx).filesEnabled && FilePlaces.hasAccess()

        var lastShown = -1
        fun progress(phase: String) = { done: Int, total: Int ->
            runBlocking { setProgress(workDataOf(KEY_PHASE to phase, KEY_DONE to done, KEY_TOTAL to total)) }
            if (manual && done != lastShown) {
                lastShown = done
                notify(PROGRESS_ID, progressNotification(phase, done, total))
            }
        }

        try {
            if (photoAccess) Scanner(ctx).scan(onProgress = progress(PHASE_PHOTOS), isStopped = { isStopped })
            lastShown = -1
            if (fileAccess && !isStopped) FileRepo(ctx).scan(onProgress = progress(PHASE_FILES), isStopped = { isStopped })
        } catch (e: SecurityException) {
            // Access was taken away – nothing to do until the user allows it again.
        }

        try { app.phototidy.home.SmartWidgets.refreshAll(ctx) } catch (_: Exception) {}
        if (manual) NotificationManagerCompat.from(ctx).cancel(PROGRESS_ID)
        else maybeNotifySuggestions(photoAccess, fileAccess)
        Result.success()
    }

    private fun maybeNotifySuggestions(photoAccess: Boolean, fileAccess: Boolean) {
        val ctx = applicationContext
        val plan = if (photoAccess) Planner(ctx).build() else Plan.EMPTY
        val files = if (fileAccess) FilePlanner(ctx).build() else FilePlan.EMPTY
        val settings = Settings(ctx)
        val sort = plan.sortCount + files.sortCount
        val junk = plan.junk.size + files.junk.size
        val signature = "${plan.sortCount}/${plan.junk.size}/${files.sortCount}/${files.junk.size}"
        if ((sort == 0 && junk == 0) || signature == settings.lastNotified) return
        settings.lastNotified = signature

        val parts = mutableListOf<String>()
        if (plan.sortCount > 0) parts += "${plan.sortCount} photos to sort"
        if (files.sortCount > 0) parts += "${files.sortCount} files to sort"
        if (junk > 0) parts += "$junk possible junk"
        val n = NotificationCompat.Builder(ctx, CHANNEL_SUGGESTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Ready for a tidy-up")
            .setContentText(parts.joinToString(" · "))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notify(SUGGESTION_ID, n)
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val n = progressNotification(PHASE_PHOTOS, done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(PROGRESS_ID, n)
    }

    private fun progressNotification(phase: String, done: Int, total: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (phase == PHASE_FILES) "Looking through your files" else "Looking through your photos")
            .setContentText(if (total > 0) "$done of $total" else "Getting ready…")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @SuppressLint("MissingPermission") // checked just below
    private fun notify(id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val KEY_MANUAL = "manual"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val PHASE_PHOTOS = "photos"
        const val PHASE_FILES = "files"

        fun hasPhotoAccess(ctx: Context): Boolean {
            fun g(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            return if (Build.VERSION.SDK_INT >= 33) {
                g(Manifest.permission.READ_MEDIA_IMAGES) ||
                    (Build.VERSION.SDK_INT >= 34 && g(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
            } else g(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        const val MANUAL_WORK = "scan-now"
        private const val NIGHTLY_WORK = "scan-nightly"
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_SUGGESTIONS = "suggestions"
        private const val PROGRESS_ID = 1
        private const val SUGGESTION_ID = 2

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Scanning progress", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SUGGESTIONS, "Suggestions ready", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        fun scanNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<ScanWorker>()
                .setInputData(workDataOf(KEY_MANUAL to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, req)
        }

        fun cancelScan(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(MANUAL_WORK)
        }

        fun setNightly(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NIGHTLY_WORK)
                return
            }
            val req = PeriodicWorkRequestBuilder<ScanWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(NIGHTLY_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
