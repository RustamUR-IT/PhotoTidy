package app.phototidy.home

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.phototidy.MainActivity
import app.phototidy.R
import app.phototidy.core.HomeApp
import app.phototidy.core.HomeFolder
import app.phototidy.core.HomeRules
import app.phototidy.ui.PhotoTidyTheme
import app.phototidy.ui.SectionLabel

/**
 * Smart home screen widgets. They live on your normal (Nothing) home screen next to
 * your other widgets and never move anything – they only fill themselves:
 *  - Smart folder: one category, most-used apps first
 *  - App dashboard: most-used row on top, then every category
 * They refresh when you open System Optimizer, after the nightly scan and every 30 min.
 */
object SmartWidgets {

    const val EXTRA_COMPONENT = "component"
    private const val EXTRA_KIND = "kind"
    private const val KIND_FOLDER = "folder"
    private const val KIND_DASHBOARD = "dashboard"

    // ---------- which folder each widget shows ----------

    private fun prefs(c: Context) = c.getSharedPreferences("smart_widgets", Context.MODE_PRIVATE)
    fun folderOf(c: Context, id: Int): HomeFolder? = HomeFolder.fromName(prefs(c).getString("w$id", null))
    fun setFolder(c: Context, id: Int, f: HomeFolder) = prefs(c).edit().putString("w$id", f.name).apply()
    fun forget(c: Context, ids: IntArray) = prefs(c).edit().apply { ids.forEach { remove("w$it") } }.apply()

    // ---------- refresh ----------

    fun refreshAll(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val folderIds = m.getAppWidgetIds(ComponentName(c, FolderWidget::class.java))
        val dashIds = m.getAppWidgetIds(ComponentName(c, DashboardWidget::class.java))
        folderIds.forEach { updateFolder(c, m, it) }
        dashIds.forEach { updateDashboard(c, m, it) }
        if (folderIds.isNotEmpty()) m.notifyAppWidgetViewDataChanged(folderIds, R.id.grid)
        if (dashIds.isNotEmpty()) m.notifyAppWidgetViewDataChanged(dashIds, R.id.list)
    }

    /** Asks the launcher to add one of our widgets (shows its own "Add widget?" dialog). */
    fun requestPin(c: Context, dashboard: Boolean): Boolean {
        val m = AppWidgetManager.getInstance(c)
        if (!m.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(c, if (dashboard) DashboardWidget::class.java else FolderWidget::class.java)
        return m.requestPinAppWidget(provider, null, null)
    }

    private fun launchTemplate(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 7,
        Intent(c, WidgetLaunchActivity::class.java),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pickerIntent(c: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        c, 1000 + id,
        Intent(c, FolderPickerActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(FolderPickerActivity.EXTRA_FROM_WIDGET, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 3, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceIntent(c: Context, id: Int, kind: String) =
        Intent(c, SmartWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(EXTRA_KIND, kind)
            .also { it.data = Uri.parse("phototidy://widget/$kind/$id") } // unique per widget

    @Suppress("DEPRECATION")
    fun updateFolder(c: Context, m: AppWidgetManager, id: Int) {
        val rv = RemoteViews(c.packageName, R.layout.widget_folder)
        val folder = folderOf(c, id)
        if (folder == null) {
            rv.setTextViewText(R.id.title, "Smart folder")
            rv.setViewVisibility(R.id.grid, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.root, pickerIntent(c, id))
        } else {
            rv.setTextViewText(R.id.title, folder.title)
            rv.setViewVisibility(R.id.grid, View.VISIBLE)
            rv.setViewVisibility(R.id.empty, View.GONE)
            rv.setRemoteAdapter(R.id.grid, serviceIntent(c, id, KIND_FOLDER))
            rv.setPendingIntentTemplate(R.id.grid, launchTemplate(c))
            rv.setOnClickPendingIntent(R.id.title, pickerIntent(c, id)) // tap title to change folder
        }
        m.updateAppWidget(id, rv)
    }

    @Suppress("DEPRECATION")
    fun updateDashboard(c: Context, m: AppWidgetManager, id: Int) {
        val rv = RemoteViews(c.packageName, R.layout.widget_dashboard)
        rv.setRemoteAdapter(R.id.list, serviceIntent(c, id, KIND_DASHBOARD))
        rv.setEmptyView(R.id.list, R.id.empty)
        rv.setPendingIntentTemplate(R.id.list, launchTemplate(c))
        rv.setOnClickPendingIntent(R.id.title, openApp(c))
        m.updateAppWidget(id, rv)
    }

    // ---------- data ----------

    /** All launchable apps + usage, shared by both widgets. */
    class Snapshot(val apps: List<HomeApp>, val components: Map<String, ComponentName>, val usage: Map<String, Long>)

    fun snapshot(c: Context): Snapshot {
        val repo = HomeRepo(c)
        val hidden = HomePrefs(c).hiddenPkgs
        val acts = repo.activities().filter { it.applicationInfo.packageName !in hidden && it.applicationInfo.packageName != c.packageName }
        val apps = acts.map {
            HomeApp(
                key = it.componentName.flattenToShortString(),
                pkg = it.applicationInfo.packageName,
                label = it.label.toString(),
                category = it.applicationInfo.category,
                isSystem = it.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
            )
        }
        return Snapshot(apps, acts.associate { it.componentName.flattenToShortString() to it.componentName }, repo.usageMinutes())
    }

    fun iconOf(c: Context, component: ComponentName?, sizePx: Int): Bitmap? = try {
        val la = c.getSystemService(LauncherApps::class.java)
        val info = la.getActivityList(component?.packageName, Process.myUserHandle())
            .firstOrNull { it.componentName == component }
        val d = info?.getBadgedIcon(0) ?: c.packageManager.getApplicationIcon(component!!.packageName)
        d.toBitmap(sizePx, sizePx)
    } catch (_: Exception) {
        null
    }

    internal fun kindOf(i: Intent) = i.getStringExtra(EXTRA_KIND)
    internal const val FOLDER = KIND_FOLDER
}

class FolderWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { SmartWidgets.updateFolder(context, manager, it) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.grid)
    }

    override fun onDeleted(context: Context, ids: IntArray) = SmartWidgets.forget(context, ids)
}

class DashboardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { SmartWidgets.updateDashboard(context, manager, it) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.list)
    }
}

class SmartWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        return if (SmartWidgets.kindOf(intent) == SmartWidgets.FOLDER) FolderFactory(applicationContext, id)
        else DashboardFactory(applicationContext)
    }
}

private class FolderFactory(private val c: Context, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private var apps: List<HomeApp> = emptyList()
    private var components: Map<String, ComponentName> = emptyMap()
    private val iconPx = (44 * c.resources.displayMetrics.density).toInt()

    override fun onCreate() {}
    override fun onDataSetChanged() {
        val folder = SmartWidgets.folderOf(c, id) ?: run { apps = emptyList(); return }
        val s = SmartWidgets.snapshot(c)
        apps = HomeRules.folderApps(s.apps, folder, s.usage, HomePrefs(c).overrides)
        components = s.components
    }

    override fun getViewAt(position: Int): RemoteViews {
        val a = apps.getOrNull(position) ?: return RemoteViews(c.packageName, R.layout.widget_item_app)
        val rv = RemoteViews(c.packageName, R.layout.widget_item_app)
        rv.setTextViewText(R.id.label, a.label)
        SmartWidgets.iconOf(c, components[a.key], iconPx)?.let { rv.setImageViewBitmap(R.id.icon, it) }
        rv.setOnClickFillInIntent(R.id.item, Intent().putExtra(SmartWidgets.EXTRA_COMPONENT, a.key))
        return rv
    }

    override fun getCount() = apps.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = apps.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds() = true
    override fun onDestroy() {}
}

private class DashboardFactory(private val c: Context) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<HomeRules.Row> = emptyList()
    private var components: Map<String, ComponentName> = emptyMap()
    private val iconPx = (40 * c.resources.displayMetrics.density).toInt()
    private val slots = intArrayOf(R.id.i0, R.id.i1, R.id.i2, R.id.i3, R.id.i4)

    override fun onCreate() {}
    override fun onDataSetChanged() {
        val s = SmartWidgets.snapshot(c)
        components = s.components
        // Long folders are split into several rows of 5 so every app stays reachable.
        rows = HomeRules.dashboardRows(s.apps, s.usage, HomePrefs(c).overrides, perRow = 5).flatMap { row ->
            row.apps.chunked(5).mapIndexed { i, chunk -> HomeRules.Row(if (i == 0) row.title else "", row.folder, chunk) }
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_item_row)
        val row = rows.getOrNull(position) ?: return rv
        rv.setTextViewText(R.id.rowTitle, row.title)
        rv.setViewVisibility(R.id.rowTitle, if (row.title.isEmpty()) View.GONE else View.VISIBLE)
        slots.forEachIndexed { i, slot ->
            val a = row.apps.getOrNull(i)
            if (a == null) {
                rv.setViewVisibility(slot, View.INVISIBLE)
            } else {
                rv.setViewVisibility(slot, View.VISIBLE)
                SmartWidgets.iconOf(c, components[a.key], iconPx)?.let { rv.setImageViewBitmap(slot, it) }
                rv.setContentDescription(slot, a.label)
                rv.setOnClickFillInIntent(slot, Intent().putExtra(SmartWidgets.EXTRA_COMPONENT, a.key))
            }
        }
        return rv
    }

    override fun getCount() = rows.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
    override fun onDestroy() {}
}

/** Invisible step between a widget tap and the app you tapped. */
class WidgetLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(SmartWidgets.EXTRA_COMPONENT)
            ?.let { ComponentName.unflattenFromString(it) }
            ?.let { comp ->
                try {
                    getSystemService(LauncherApps::class.java)
                        .startMainActivity(comp, Process.myUserHandle(), intent.sourceBounds, null)
                } catch (_: Exception) {
                    packageManager.getLaunchIntentForPackage(comp.packageName)?.let { startActivity(it) }
                }
            }
        finish()
    }
}

/** Shown when you add a Smart folder widget (or tap its title): choose which folder it shows. */
class FolderPickerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FROM_WIDGET = "from_widget"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            PhotoTidyTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        SectionLabel("Smart folder")
                        Spacer(Modifier.height(6.dp))
                        Text("Which folder should this widget show?", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "It fills itself with the right apps, most-used first, and keeps up when you install new ones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn {
                            items(HomeFolder.entries) { f ->
                                Text(
                                    f.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { choose(id, f) }
                                        .padding(vertical = 14.dp, horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(id: Int, f: HomeFolder) {
        SmartWidgets.setFolder(this, id, f)
        val m = AppWidgetManager.getInstance(this)
        SmartWidgets.updateFolder(this, m, id)
        m.notifyAppWidgetViewDataChanged(intArrayOf(id), R.id.grid)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
