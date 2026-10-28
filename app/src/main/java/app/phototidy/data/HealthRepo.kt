package app.phototidy.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import app.phototidy.core.BatteryApp
import app.phototidy.core.BatteryFinding
import app.phototidy.core.BatteryRules
import app.phototidy.core.PermGroup
import app.phototidy.core.PrivacyApp
import app.phototidy.core.PrivacyFinding
import app.phototidy.core.PrivacyRules
import app.phototidy.core.WaFile
import app.phototidy.core.WhatsAppRules
import java.io.File

// ------------------------------------------------------------------ C1 WhatsApp

data class WhatsAppPlan(val found: Boolean, val files: List<WaFile>) {
    val totalBytes get() = files.sumOf { it.sizeBytes }

    companion object {
        val EMPTY = WhatsAppPlan(false, emptyList())
    }
}

object WhatsAppScanner {

    /** Where WhatsApp (and WhatsApp Business) keep their media, newest layout first. */
    private fun roots(): List<File> {
        val s = FilePlaces.storage
        return listOf(
            File(s, "Android/media/com.whatsapp/WhatsApp/Media"),
            File(s, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"),
            File(s, "WhatsApp/Media"),
            File(s, "WhatsApp Business/Media"),
        ).filter { it.isDirectory }
    }

    fun scan(): WhatsAppPlan {
        if (!FilePlaces.hasAccess()) return WhatsAppPlan.EMPTY
        val roots = roots()
        if (roots.isEmpty()) return WhatsAppPlan(false, emptyList())
        val out = ArrayList<WaFile>()
        for (root in roots) {
            val base = root.absolutePath.length + 1
            root.walkTopDown().maxDepth(4).forEach { f ->
                if (!f.isFile) return@forEach
                val cat = WhatsAppRules.categorize(f.absolutePath.substring(base)) ?: return@forEach
                out += WaFile(f.absolutePath, f.length(), f.lastModified(), cat)
            }
        }
        return WhatsAppPlan(true, out)
    }

    /** Deletes for good (frees space now). Returns bytes freed. */
    fun deleteNow(context: Context, files: List<WaFile>): Long {
        var freed = 0L
        val scanned = ArrayList<String>()
        files.forEach {
            val f = File(it.path)
            val size = f.length()
            if (f.delete()) { freed += size; scanned += it.path }
        }
        FileRepo(context).rescan(scanned)
        return freed
    }
}

// ------------------------------------------------------------------ E1 + D1

data class HealthPlan(
    val usageAccess: Boolean,
    val privacyApps: List<PrivacyApp>,
    val privacy: List<PrivacyFinding>,
    val battery: List<BatteryFinding>,
) {
    companion object {
        val EMPTY = HealthPlan(false, emptyList(), emptyList(), emptyList())
    }
}

class HealthRepo(private val context: Context) {

    private val pm = context.packageManager

    fun load(): HealthPlan {
        val usage = AppRepo(context).hasUsageAccess()
        val now = System.currentTimeMillis()
        val usm = context.getSystemService(UsageStatsManager::class.java)

        val lastUsed: Map<String, Long> = if (!usage) emptyMap() else try {
            usm.queryAndAggregateUsageStats(now - 365L * 86_400_000L, now)
                .mapValues { it.value.lastTimeUsed }.filterValues { it > 0 }
        } catch (_: Exception) { emptyMap() }

        val week = if (!usage) emptyMap() else try {
            usm.queryAndAggregateUsageStats(now - 7L * 86_400_000L, now)
        } catch (_: Exception) { emptyMap() }

        @Suppress("DEPRECATION")
        val packages: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { it.packageName != context.packageName && it.applicationInfo != null }

        val privacyApps = packages.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            val req = p.requestedPermissions ?: return@mapNotNull null
            val flags = p.requestedPermissionsFlags ?: return@mapNotNull null
            val granted = req.indices
                .filter { flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
                .mapNotNull { PermGroup.of(req[it]) }
                .toSet()
            if (granted.isEmpty()) return@mapNotNull null
            val since = lastUsed[p.packageName] ?: p.firstInstallTime
            PrivacyApp(
                pkg = p.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                isSystem = system,
                granted = granted,
                daysUnused = if (usage) (now - since) / 86_400_000L else null,
            )
        }

        val power = context.getSystemService(PowerManager::class.java)
        val batteryApps = if (!usage) emptyList() else packages.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            val s = week[p.packageName] ?: return@mapNotNull null
            BatteryApp(
                pkg = p.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                foregroundMin = s.totalTimeVisible / 60_000L,
                backgroundServiceMin = s.totalTimeForegroundServiceUsed / 60_000L,
                unrestricted = try { power.isIgnoringBatteryOptimizations(p.packageName) } catch (_: Exception) { false },
            )
        }

        return HealthPlan(
            usageAccess = usage,
            privacyApps = privacyApps.sortedBy { it.label.lowercase() },
            privacy = PrivacyRules.findings(privacyApps),
            battery = BatteryRules.findings(batteryApps),
        )
    }
}
