package app.phototidy.home

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import android.provider.Telephony
import app.phototidy.core.HomeApp
import app.phototidy.core.HomeFolder
import app.phototidy.core.HomeRules
import app.phototidy.data.AppRepo

/** One app as the home screen shows it. */
data class LaunchApp(val app: HomeApp, val component: ComponentName) {
    val key get() = app.key
    val label get() = app.label
    val pkg get() = app.pkg
}

data class HomeLayout(
    val dock: List<LaunchApp>,
    val folders: List<Pair<HomeFolder, List<LaunchApp>>>,
    val all: List<LaunchApp>,
) {
    companion object {
        val EMPTY = HomeLayout(emptyList(), emptyList(), emptyList())
    }
}

/** User choices for the home screen (manual folder moves). Widgets are stored separately. */
class HomePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    /** pkg -> folder the user picked by hand. Auto-sorting never overrides these. */
    var overrides: Map<String, HomeFolder>
        get() = prefs.getStringSet("overrides", emptySet())!!.mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else HomeFolder.fromName(it.substring(i + 1))?.let { f -> it.substring(0, i) to f }
        }.toMap()
        set(v) = prefs.edit().putStringSet("overrides", v.map { "${it.key}=${it.value.name}" }.toSet()).apply()

    /** Dock picked by hand (keys); empty = automatic. */
    var pinnedDock: List<String>
        get() = prefs.getString("dock", "")!!.split('|').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("dock", v.joinToString("|")).apply()

    var hiddenPkgs: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("hidden", v).apply()
}

class HomeRepo(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun activities(): List<LauncherActivityInfo> =
        launcherApps.getActivityList(null, Process.myUserHandle())

    fun load(): HomeLayout {
        val prefs = HomePrefs(context)
        val hidden = prefs.hiddenPkgs
        val list = activities()
            .filter { it.applicationInfo.packageName !in hidden }
            .map { info ->
                val ai = info.applicationInfo
                LaunchApp(
                    HomeApp(
                        key = info.componentName.flattenToShortString(),
                        pkg = ai.packageName,
                        label = info.label.toString(),
                        category = ai.category,
                        isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    ),
                    info.componentName,
                )
            }
            .sortedBy { it.label.lowercase() }

        val apps = list.map { it.app }
        val byKey = list.associateBy { it.key }
        val pinned = prefs.pinnedDock.filter { it in byKey }
        val dockKeys = pinned.ifEmpty { HomeRules.pickDock(apps, usageMinutes(), defaultApps()) }
        val folders = HomeRules.arrange(apps, dockKeys, prefs.overrides)
            .map { (folder, members) -> folder to members.mapNotNull { byKey[it.key] } }
            .filter { it.second.isNotEmpty() }

        return HomeLayout(dockKeys.mapNotNull { byKey[it] }, folders, list)
    }

    fun launch(app: LaunchApp) {
        launcherApps.startMainActivity(app.component, Process.myUserHandle(), null, null)
    }

    fun openInfo(app: LaunchApp) {
        launcherApps.startAppDetailsActivity(app.component, Process.myUserHandle(), null, null)
    }

    fun uninstall(app: LaunchApp) {
        @Suppress("DEPRECATION")
        context.startActivity(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Minutes in the foreground over the last 2 weeks (needs usage access). */
    fun usageMinutes(): Map<String, Long> {
        if (!AppRepo(context).hasUsageAccess()) return emptyMap()
        return try {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            usm.queryAndAggregateUsageStats(now - 14L * 86_400_000L, now)
                .mapValues { it.value.totalTimeInForeground / 60_000L }
                .filterValues { it > 0 }
                .filterKeys { it != context.packageName }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Phone, messages, browser, camera – the classic dock. */
    private fun defaultApps(): List<String> {
        val pm = context.packageManager
        fun pkgOf(i: Intent) = pm.resolveActivity(i, 0)?.activityInfo?.packageName?.takeIf { it != "android" }
        return listOfNotNull(
            pkgOf(Intent(Intent.ACTION_DIAL)),
            try { Telephony.Sms.getDefaultSmsPackage(context) } catch (_: Exception) { null },
            pkgOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        )
    }
}
