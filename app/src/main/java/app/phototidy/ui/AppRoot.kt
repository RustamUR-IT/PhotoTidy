package app.phototidy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.phototidy.data.AppEntry
import app.phototidy.work.ScanWorker

enum class Screen { HOME, SORT, JUNK, SETTINGS, FILE_SORT, FILE_JUNK, FILE_TRASH, APPS }

@Composable
fun AppRoot(
    vm: MainViewModel,
    perms: PermState,
    consent: ConsentRequester,
    onRequestAccess: () -> Unit,
    onEnableManageMedia: () -> Unit,
    onNightlyEnabled: () -> Unit,
    onAllowFiles: () -> Unit,
    onUninstall: (List<AppEntry>) -> Unit,
    onAppSettings: (AppEntry) -> Unit,
    onAllowUsage: () -> Unit,
    onSetHome: () -> Unit = {},
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val plan by vm.plan.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val sensitivity by vm.sensitivity.collectAsState()
    val events by vm.eventsEnabled.collectAsState()
    val nightly by vm.nightlyEnabled.collectAsState()
    val lastScan by vm.lastScanMs.collectAsState()
    val workInfos by vm.scanWork.observeAsState(emptyList())
    val filePlan by vm.filePlan.collectAsState()
    val filesEnabled by vm.filesEnabled.collectAsState()
    val trash by vm.trash.collectAsState()
    val appPlan by vm.appPlan.collectAsState()
    val appsLoading by vm.appsLoading.collectAsState()
    val appsEnabled by vm.appsEnabled.collectAsState()

    val info = workInfos.firstOrNull { !it.state.isFinished }
    val scan = ScanStatus(
        running = info != null,
        done = info?.progress?.getInt(ScanWorker.KEY_DONE, 0) ?: 0,
        total = info?.progress?.getInt(ScanWorker.KEY_TOTAL, 0) ?: 0,
        phase = info?.progress?.getString(ScanWorker.KEY_PHASE) ?: ScanWorker.PHASE_PHOTOS,
    )

    // Refresh suggestions on start, when access changes, and whenever a scan finishes.
    LaunchedEffect(perms.hasAccess, perms.hasFileAccess, scan.running) {
        if (!scan.running) vm.refresh()
    }
    // While scanning, refresh now and then so numbers grow live.
    LaunchedEffect(scan.done / 300) {
        if (scan.running && scan.done > 0) vm.refresh()
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.FILE_TRASH) Screen.FILE_JUNK else Screen.HOME
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    plan = plan,
                    scan = scan,
                    perms = perms,
                    lastScanMs = lastScan,
                    onRequestAccess = onRequestAccess,
                    onEnableManageMedia = onEnableManageMedia,
                    onScan = vm::scan,
                    onCancelScan = vm::cancelScan,
                    onOpenSort = { screen = Screen.SORT },
                    onOpenJunk = { screen = Screen.JUNK },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    filePlan = filePlan,
                    filesEnabled = filesEnabled,
                    onAllowFiles = onAllowFiles,
                    onOpenFileSort = { screen = Screen.FILE_SORT },
                    onOpenFileJunk = { screen = Screen.FILE_JUNK },
                    appPlan = appPlan,
                    appsEnabled = appsEnabled,
                    onOpenApps = { screen = Screen.APPS },
                    onSetHome = onSetHome,
                )
                Screen.APPS -> AppsScreen(
                    plan = appPlan,
                    loading = appsLoading,
                    onBack = { screen = Screen.HOME },
                    onUninstall = onUninstall,
                    onOpenSettings = onAppSettings,
                    onKeep = vm::keepApps,
                    onAllowUsage = onAllowUsage,
                )
                Screen.FILE_SORT -> FilesSortScreen(
                    plan = filePlan,
                    busy = busy,
                    onBack = { screen = Screen.HOME },
                    onMove = vm::moveFiles,
                    onNeverSort = vm::dontSortFiles,
                )
                Screen.FILE_JUNK -> FilesJunkScreen(
                    plan = filePlan,
                    busy = busy,
                    onBack = { screen = Screen.HOME },
                    onTrash = vm::trashFiles,
                    onKeep = vm::keepFiles,
                    onOpenTrash = { screen = Screen.FILE_TRASH },
                )
                Screen.FILE_TRASH -> FilesTrashScreen(
                    entries = trash,
                    busy = busy,
                    onBack = { screen = Screen.FILE_JUNK },
                    onRestore = vm::restore,
                    onDeleteForever = vm::deleteForever,
                )
                Screen.SORT -> SortScreen(
                    plan = plan,
                    busy = busy,
                    onBack = { screen = Screen.HOME },
                    onMove = { vm.move(it, consent) },
                    onRenameEvent = vm::renameEvent,
                    onNeverSort = vm::dontSort,
                )
                Screen.JUNK -> JunkScreen(
                    plan = plan,
                    busy = busy,
                    onBack = { screen = Screen.HOME },
                    onTrash = { vm.trash(it, consent) },
                    onKeep = vm::keep,
                )
                Screen.SETTINGS -> SettingsScreen(
                    sensitivity = sensitivity,
                    eventsEnabled = events,
                    nightlyEnabled = nightly,
                    canManageMedia = perms.canManageMedia,
                    onBack = { screen = Screen.HOME },
                    onSensitivity = vm::setSensitivity,
                    onEvents = vm::setEvents,
                    onNightly = {
                        vm.setNightly(it)
                        if (it) onNightlyEnabled()
                    },
                    onManageMedia = onEnableManageMedia,
                    filesEnabled = filesEnabled,
                    hasFileAccess = perms.hasFileAccess,
                    onFiles = vm::setFilesEnabled,
                    onAllowFiles = onAllowFiles,
                    appsEnabled = appsEnabled,
                    onApps = vm::setAppsEnabled,
                    onResetDecisions = vm::resetDecisions,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        }
    }
}
