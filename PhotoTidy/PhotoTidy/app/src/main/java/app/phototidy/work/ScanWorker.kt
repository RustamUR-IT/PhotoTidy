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

        var lastShown = -1
        try { Scanner(applicationContext).scan(
            onProgress = { done, total ->
                runBlocking { setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total)) }
                if (manual && done != lastShown) {
                    lastShown = done
                    notify(PROGRESS_ID, progressNotification(done, total))
                }
            },
            isStopped = { isStopped },
        ) } catch (e: SecurityException) {
            // Photo access was taken away – nothing we can do until the user allows it again.
            NotificationManagerCompat.from(applicationContext).cancel(PROGRESS_ID)
            return@withContext Result.failure()
        }

        if (manual) NotificationManagerCompat.from(applicationContext).cancel(PROGRESS_ID)
        else maybeNotifySuggestions()
        Result.success()
    }

    private fun maybeNotifySuggestions() {
        val plan = Planner(applicationContext).build()
        val settings = Settings(applicationContext)
        val signature = "${plan.sortCount}/${plan.junk.size}"
        if ((plan.sortCount == 0 && plan.junk.isEmpty()) || signature == settings.lastNotified) return
        settings.lastNotified = signature

        val parts = mutableListOf<String>()
        if (plan.sortCount > 0) parts += "${plan.sortCount} photos ready to sort"
        if (plan.junk.isNotEmpty()) parts += "${plan.junk.size} possible junk"
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_SUGGESTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your photos are ready for review")
            .setContentText(parts.joinToString(" · "))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notify(SUGGESTION_ID, n)
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val n = progressNotification(done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(PROGRESS_ID, n)
    }

    private fun progressNotification(done: Int, total: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Looking through your photos")
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
