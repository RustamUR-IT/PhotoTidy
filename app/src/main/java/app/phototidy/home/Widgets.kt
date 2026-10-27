package app.phototidy.home

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Process

/** A widget placed on the home screen. Order and height only change when YOU change them. */
data class PlacedWidget(val id: Int, val heightDp: Int)

class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("home_widgets", Context.MODE_PRIVATE)
    private val manager = AppWidgetManager.getInstance(context)

    fun list(): List<PlacedWidget> =
        prefs.getString("widgets", "")!!.split(',').mapNotNull {
            val p = it.split(':')
            val id = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            PlacedWidget(id, p.getOrNull(1)?.toIntOrNull() ?: 160)
        }.filter { manager.getAppWidgetInfo(it.id) != null }

    fun save(list: List<PlacedWidget>) =
        prefs.edit().putString("widgets", list.joinToString(",") { "${it.id}:${it.heightDp}" }).apply()

    fun add(id: Int, info: AppWidgetProviderInfo, density: Float) {
        val minDp = (info.minHeight / density).toInt()
        val target = if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight * 90 else minDp
        save(list() + PlacedWidget(id, target.coerceIn(80, 420)))
    }

    fun remove(id: Int) = save(list().filter { it.id != id })

    fun move(id: Int, delta: Int) {
        val l = list().toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        val w = l.removeAt(i); l.add(j, w); save(l)
    }

    fun resize(id: Int, deltaDp: Int) =
        save(list().map { if (it.id == id) it.copy(heightDp = (it.heightDp + deltaDp).coerceIn(60, 600)) else it })

    fun providers(): List<AppWidgetProviderInfo> =
        manager.getInstalledProvidersForProfile(Process.myUserHandle())
}
