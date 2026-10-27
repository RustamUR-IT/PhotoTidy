package app.phototidy.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings as AndroidSettings
import android.provider.Telephony
import android.util.Log
import app.phototidy.core.AppFacts
import app.phototidy.core.AppRules
import app.phototidy.core.AppVerdict
import app.phototidy.core.Importance

data class AppEntry(
    val facts: AppFacts,
    val verdict: AppVerdict,
    val sizeBytes: Long,
    val isEnabled: Boolean,
    val canUninstall: Boolean,
    val unusedDays: Long?,
) {
    val pkg get() = facts.pkg
    val label get() = facts.label
    val isSystem get() = facts.isSystem
    val importance get() = verdict.importance
}

data class AppPlan(
    val usageAccess: Boolean,
    val apps: List<AppEntry>,
) {
    val userApps get() = apps.filter { !it.isSystem }
    val unused get() = apps.filter { it.unusedDays != null }.sortedByDescending { it.unusedDays }
    val unusedBytes get() = unused.sumOf { it.sizeBytes }

    companion object {
        val EMPTY = AppPlan(false, emptyList())
    }
}

class AppRepo(private val context: Context) {

    private val pm = context.packageManager

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun load(): AppPlan {
        val settings = Settings(context)
        val usage = hasUsageAccess()
        val now = System.currentTimeMillis()
        val lastUsed = if (usage) lastUsedMap(now) else emptyMap()
        val roles = defaultRoles()
        val admins = deviceAdmins()

        val apps = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .filter { it.packageName != context.packageName }
            // Only apps you can see or that matter: launchable ones, plus anything user-installed.
            .filter { info ->
                val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                !system || pm.getLaunchIntentForPackage(info.packageName) != null || !info.enabled
            }
            .mapNotNull { info ->
                try {
                    entryFor(info, now, usage, lastUsed, roles, admins, settings)
                } catch (e: Exception) {
                    Log.w(TAG, "skip ${info.packageName}", e); null
                }
            }
            .sortedWith(compareBy<AppEntry> { it.isSystem }.thenBy { it.label.lowercase() })
        return AppPlan(usage, apps)
    }

    private fun entryFor(
        info: ApplicationInfo,
        now: Long,
        usage: Boolean,
        lastUsed: Map<String, Long>,
        roles: Map<String, String>,
        admins: Set<String>,
        settings: Settings,
    ): AppEntry {
        val pkgInfo = pm.getPackageInfo(info.packageName, 0)
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val facts = AppFacts(
            pkg = info.packageName,
            label = pm.getApplicationLabel(info).toString(),
            isSystem = system,
            category = info.category,
            ownDescription = try { info.loadDescription(pm)?.toString() } catch (_: Exception) { null },
            defaultRole = roles[info.packageName],
            isDeviceAdmin = info.packageName in admins,
            installedMs = pkgInfo.firstInstallTime,
            lastUsedMs = lastUsed[info.packageName],
            usageKnown = usage,
        )
        val verdict = AppRules.verdict(facts)
        val unused = if (info.packageName in settings.keptApps) null
        else AppRules.unusedForDays(facts, settings.sensitivity, now)
            ?.takeIf { verdict.importance != Importance.ESSENTIAL }
        return AppEntry(
            facts = facts,
            verdict = verdict,
            sizeBytes = sizeOf(info, usage),
            isEnabled = info.enabled,
            canUninstall = !system || (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0),
            unusedDays = unused,
        )
    }

    /** App + data + cache size (needs usage access); otherwise just the install file size. */
    private fun sizeOf(info: ApplicationInfo, usage: Boolean): Long {
        if (usage) {
            try {
                val ssm = context.getSystemService(StorageStatsManager::class.java)
                val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle())
                return s.appBytes + s.dataBytes + s.cacheBytes
            } catch (_: Exception) {
            }
        }
        return try { java.io.File(info.sourceDir).length() } catch (_: Exception) { 0L }
    }

    private fun lastUsedMap(now: Long): Map<String, Long> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val stats = usm.queryAndAggregateUsageStats(now - 2L * 365 * 86_400_000L, now)
        return stats.mapValues { it.value.lastTimeUsed }.filterValues { it > 0 }
    }

    /** Which apps are currently the default home screen, phone, SMS, browser, keyboard. */
    private fun defaultRoles(): Map<String, String> {
        val out = HashMap<String, String>()
        fun resolve(intent: Intent, role: String) {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?.takeIf { it != "android" }?.let { out.putIfAbsent(it, role) }
        }
        resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "home screen")
        resolve(Intent(Intent.ACTION_DIAL), "phone app")
        resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), "web browser")
        try { Telephony.Sms.getDefaultSmsPackage(context)?.let { out.putIfAbsent(it, "SMS app") } } catch (_: Exception) {}
        try {
            AndroidSettings.Secure.getString(context.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { out.putIfAbsent(it, "keyboard") }
        } catch (_: Exception) {}
        return out
    }

    private fun deviceAdmins(): Set<String> = try {
        context.getSystemService(DevicePolicyManager::class.java).activeAdmins
            ?.map { it.packageName }?.toSet() ?: emptySet()
    } catch (_: Exception) { emptySet() }

    companion object {
        private const val TAG = "AppRepo"
    }
}
