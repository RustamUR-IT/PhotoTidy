package app.phototidy.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.Settings as AndroidSettings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.phototidy.MainActivity
import app.phototidy.core.HomeFolder
import app.phototidy.ui.Accent
import app.phototidy.ui.PhotoTidyTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The home screen. Apps are sorted into folders automatically and re-sorted
 * whenever an app is installed or removed. Widgets live in their own area at
 * the top and are only ever moved by you.
 */
class HomeActivity : ComponentActivity() {

    private lateinit var host: AppWidgetHost
    private lateinit var widgets: WidgetStore
    private val scope: CoroutineScope = MainScope()

    private val layout = mutableStateOf(HomeLayout.EMPTY)
    private val placed = mutableStateOf<List<PlacedWidget>>(emptyList())
    private var pendingWidget: Pair<Int, AppWidgetProviderInfo>? = null

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        host = AppWidgetHost(this, HOST_ID)
        widgets = WidgetStore(this)
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback)

        setContent {
            PhotoTidyTheme {
                HomeUi(
                    layout = layout.value,
                    widgets = placed.value,
                    widgetView = { w ->
                        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(w.id)
                        host.createView(applicationContext, w.id, info).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    providers = { widgets.providers() },
                    onLaunch = { HomeRepo(this).launch(it) },
                    onInfo = { HomeRepo(this).openInfo(it) },
                    onUninstall = { HomeRepo(this).uninstall(it) },
                    onMoveTo = { app, folder ->
                        val p = HomePrefs(this)
                        p.overrides = p.overrides + (app.pkg to folder)
                        reload()
                    },
                    onToggleDock = { app ->
                        val p = HomePrefs(this)
                        val current = p.pinnedDock.ifEmpty { layout.value.dock.map { it.key } }
                        p.pinnedDock = if (app.key in current) current - app.key else (current + app.key).takeLast(5)
                        reload()
                    },
                    onHide = { app ->
                        val p = HomePrefs(this)
                        p.hiddenPkgs = p.hiddenPkgs + app.pkg
                        reload()
                    },
                    onResetSorting = {
                        val p = HomePrefs(this)
                        p.overrides = emptyMap(); p.pinnedDock = emptyList(); p.hiddenPkgs = emptySet()
                        reload()
                    },
                    onAddWidget = ::addWidget,
                    onRemoveWidget = { id -> host.deleteAppWidgetId(id); widgets.remove(id); placed.value = widgets.list() },
                    onMoveWidget = { id, d -> widgets.move(id, d); placed.value = widgets.list() },
                    onResizeWidget = { id, d -> widgets.resize(id, d); placed.value = widgets.list() },
                    onWallpaper = {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                    },
                    onOptimizer = { startActivity(Intent(this, MainActivity::class.java)) },
                    onHomeSettings = { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        try { host.stopListening() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback)
        scope.cancel()
        super.onDestroy()
    }

    private fun reload() {
        scope.launch {
            layout.value = withContext(Dispatchers.IO) { HomeRepo(this@HomeActivity).load() }
            placed.value = widgets.list()
        }
    }

    // ---------------- adding widgets ----------------

    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingWidget = id to info
        val ok = AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id, Process.myUserHandle(), info.provider, null)
        if (ok) configureOrPlace() else {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
                REQ_BIND,
            )
        }
    }

    private fun configureOrPlace() {
        val (id, info) = pendingWidget ?: return
        if (info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Exception) {
                // Some widgets don't allow this; place without configuring.
            }
        }
        place()
    }

    private fun place() {
        val (id, info) = pendingWidget ?: return
        pendingWidget = null
        widgets.add(id, info, resources.displayMetrics.density)
        placed.value = widgets.list()
    }

    @Deprecated("Needed for widget binding/configuration results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQ_BIND) configureOrPlace() else place()
        } else {
            pendingWidget?.let { host.deleteAppWidgetId(it.first) }
            pendingWidget = null
        }
    }

    companion object {
        private const val HOST_ID = 4711
        private const val REQ_BIND = 11
        private const val REQ_CONFIGURE = 12
    }
}

// ======================================================================= UI

private val wallpaperText = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f),
)

@Composable
private fun Icon(app: LaunchApp, size: Int) {
    val context = LocalContext.current
    val drawable = remember(app.key) {
        try { context.packageManager.getActivityIcon(app.component) } catch (_: Exception) { null }
    }
    AsyncImage(model = drawable, contentDescription = app.label, modifier = Modifier.size(size.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: LaunchApp, onClick: () -> Unit, onLongClick: () -> Unit, showLabel: Boolean = true, dark: Boolean = false) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(app, 52)
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                style = if (dark) MaterialTheme.typography.labelMedium.copy(fontFamily = null) else wallpaperText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(folder: HomeFolder, apps: List<LaunchApp>, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.22f))
                .padding(5.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { row.forEach { Icon(it, 20) } }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(folder.title, style = wallpaperText, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private sealed interface Sheet {
    data object Menu : Sheet
    data object Drawer : Sheet
    data object WidgetPicker : Sheet
    data class Folder(val folder: HomeFolder) : Sheet
    data class AppMenu(val app: LaunchApp) : Sheet
    data class MoveTo(val app: LaunchApp) : Sheet
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HomeUi(
    layout: HomeLayout,
    widgets: List<PlacedWidget>,
    widgetView: (PlacedWidget) -> android.view.View,
    providers: () -> List<AppWidgetProviderInfo>,
    onLaunch: (LaunchApp) -> Unit,
    onInfo: (LaunchApp) -> Unit,
    onUninstall: (LaunchApp) -> Unit,
    onMoveTo: (LaunchApp, HomeFolder) -> Unit,
    onToggleDock: (LaunchApp) -> Unit,
    onHide: (LaunchApp) -> Unit,
    onResetSorting: () -> Unit,
    onAddWidget: (AppWidgetProviderInfo) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onWallpaper: () -> Unit,
    onOptimizer: () -> Unit,
    onHomeSettings: () -> Unit,
) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var editWidgets by remember { mutableStateOf(false) }
    BackHandler(enabled = sheet != null || editWidgets) { sheet = null; editWidgets = false }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag < -40) sheet = Sheet.Drawer }
            }
            .combinedClickable(onClick = { editWidgets = false }, onLongClick = { sheet = Sheet.Menu })
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp),
        ) {
            // ---- widgets: fixed area, never touched by auto-sorting ----
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(12.dp))
                widgets.forEach { w ->
                    Box(Modifier.fillMaxWidth().height(w.heightDp.dp).padding(vertical = 4.dp)) {
                        AndroidView(factory = { widgetView(w) }, modifier = Modifier.fillMaxSize())
                    }
                    if (editWidgets) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            EditChip("↑") { onMoveWidget(w.id, -1) }
                            EditChip("↓") { onMoveWidget(w.id, 1) }
                            EditChip("Taller") { onResizeWidget(w.id, 40) }
                            EditChip("Shorter") { onResizeWidget(w.id, -40) }
                            EditChip("Remove", danger = true) { onRemoveWidget(w.id) }
                        }
                    }
                }
                if (editWidgets) {
                    TextButton(onClick = { sheet = Sheet.WidgetPicker }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("+ Add widget", style = wallpaperText.copy(fontSize = 15.sp))
                    }
                    TextButton(onClick = { editWidgets = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Done", style = wallpaperText.copy(fontSize = 15.sp))
                    }
                }
            }

            Spacer(Modifier.weight(0.01f))

            // ---- auto folders ----
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                userScrollEnabled = false,
            ) {
                items(layout.folders, key = { it.first.name }) { (folder, apps) ->
                    if (apps.size == 1) {
                        AppTile(apps[0], onClick = { onLaunch(apps[0]) }, onLongClick = { sheet = Sheet.AppMenu(apps[0]) })
                    } else {
                        FolderTile(folder, apps) { sheet = Sheet.Folder(folder) }
                    }
                }
            }

            // ---- swipe hint + dock ----
            Text(
                "︿  all apps",
                style = wallpaperText.copy(fontSize = 11.sp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = { sheet = Sheet.Drawer })
                    .padding(6.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                layout.dock.forEach { app ->
                    AppTile(app, onClick = { onLaunch(app) }, onLongClick = { sheet = Sheet.AppMenu(app) }, showLabel = false)
                }
            }
        }
    }

    // ------------------------------------------------------------- sheets
    val current = sheet ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = current is Sheet.Drawer || current is Sheet.WidgetPicker)
    ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        when (current) {
            Sheet.Menu -> Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                SheetTitle("Home screen")
                MenuItem("Add widget") { sheet = Sheet.WidgetPicker }
                if (widgets.isNotEmpty()) MenuItem("Edit widgets (move, resize, remove)") { editWidgets = true; sheet = null }
                MenuItem("Wallpaper") { sheet = null; onWallpaper() }
                MenuItem("Open System Optimizer") { sheet = null; onOptimizer() }
                MenuItem("Undo my manual folder changes") { sheet = null; onResetSorting() }
                MenuItem("Switch back to another home screen") { sheet = null; onHomeSettings() }
                Text(
                    "Apps are sorted into folders automatically – new apps too. Long-press any app to move it; " +
                        "your choice sticks. Widgets only move when you move them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Sheet.Drawer -> {
                var q by remember { mutableStateOf("") }
                val shown = layout.all.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
                Column(Modifier.padding(horizontal = 16.dp).fillMaxSize()) {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        placeholder = { Text("Search apps") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it.key }) { app ->
                            AppTile(app, onClick = { sheet = null; onLaunch(app) }, onLongClick = { sheet = Sheet.AppMenu(app) }, dark = true)
                        }
                    }
                }
            }

            is Sheet.Folder -> {
                val apps = layout.folders.firstOrNull { it.first == current.folder }?.second.orEmpty()
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                    SheetTitle(current.folder.title)
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().height(((apps.size + 3) / 4 * 96).coerceAtMost(480).dp)) {
                        items(apps, key = { it.key }) { app ->
                            AppTile(app, onClick = { sheet = null; onLaunch(app) }, onLongClick = { sheet = Sheet.AppMenu(app) }, dark = true)
                        }
                    }
                }
            }

            is Sheet.AppMenu -> Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                val app = current.app
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(app, 40); Spacer(Modifier.width(12.dp)); SheetTitle(app.label)
                }
                val inDock = layout.dock.any { it.key == app.key }
                MenuItem("Move to folder…") { sheet = Sheet.MoveTo(app) }
                MenuItem(if (inDock) "Remove from dock" else "Put in dock") { sheet = null; onToggleDock(app) }
                MenuItem("App info (disable, force stop)") { sheet = null; onInfo(app) }
                MenuItem("Uninstall") { sheet = null; onUninstall(app) }
                MenuItem("Hide from home screen") { sheet = null; onHide(app) }
            }

            is Sheet.MoveTo -> Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())
            ) {
                SheetTitle("Move ${current.app.label} to")
                HomeFolder.entries.forEach { f -> MenuItem(f.title) { sheet = null; onMoveTo(current.app, f) } }
            }

            Sheet.WidgetPicker -> {
                val context = LocalContext.current
                val list = remember { providers().sortedBy { it.loadLabel(context.packageManager).lowercase() } }
                Column(Modifier.padding(horizontal = 20.dp).fillMaxSize()) {
                    SheetTitle("Add a widget")
                    LazyColumn {
                        items(list, key = { it.provider.flattenToShortString() }) { p ->
                            val app = remember(p) {
                                try {
                                    context.packageManager.getApplicationLabel(
                                        context.packageManager.getApplicationInfo(p.provider.packageName, 0)
                                    ).toString()
                                } catch (_: Exception) { "" }
                            }
                            MenuItem("${p.loadLabel(context.packageManager)}" + if (app.isNotEmpty()) "  ·  $app" else "") {
                                sheet = null; onAddWidget(p)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 10.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditChip(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (danger) Accent else Color.Black.copy(alpha = 0.55f),
        modifier = Modifier.padding(3.dp).combinedClickable(onClick = onClick),
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
