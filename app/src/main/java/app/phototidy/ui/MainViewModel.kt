package app.phototidy.ui

import android.app.Application
import android.app.PendingIntent
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.phototidy.core.Sensitivity
import app.phototidy.data.AppPlan
import app.phototidy.data.AppRepo
import app.phototidy.core.WaFile
import app.phototidy.data.FilePlaces
import app.phototidy.data.HealthPlan
import app.phototidy.data.HealthRepo
import app.phototidy.data.WhatsAppPlan
import app.phototidy.data.WhatsAppScanner
import app.phototidy.data.FilePlan
import app.phototidy.data.FilePlanner
import app.phototidy.data.FileRepo
import app.phototidy.data.FileTrash
import app.phototidy.data.MediaItem
import app.phototidy.data.MediaRepo
import app.phototidy.data.Plan
import app.phototidy.data.Planner
import app.phototidy.data.Settings
import app.phototidy.work.ScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Asks Android for the user's OK (move/trash dialog). Returns true if allowed. */
typealias ConsentRequester = suspend (PendingIntent) -> Boolean

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val settings = Settings(app)

    private val _plan = MutableStateFlow(Plan.EMPTY)
    val plan = _plan.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    /** Short-lived status line ("Moved 120 photos"), shown as a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val sensitivity = MutableStateFlow(settings.sensitivity)
    val eventsEnabled = MutableStateFlow(settings.eventsEnabled)
    val nightlyEnabled = MutableStateFlow(settings.nightlyEnabled)
    val lastScanMs = MutableStateFlow(settings.lastScanMs)
    val filesEnabled = MutableStateFlow(settings.filesEnabled)
    val appsEnabled = MutableStateFlow(settings.appsEnabled)

    private val _appPlan = MutableStateFlow(AppPlan.EMPTY)
    val appPlan = _appPlan.asStateFlow()
    private val _appsLoading = MutableStateFlow(false)
    val appsLoading = _appsLoading.asStateFlow()

    private val _filePlan = MutableStateFlow(FilePlan.EMPTY)
    val filePlan = _filePlan.asStateFlow()

    private val _trash = MutableStateFlow<List<FileTrash.Entry>>(emptyList())
    val trash = _trash.asStateFlow()

    val scanWork: LiveData<List<WorkInfo>> =
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkLiveData(ScanWorker.MANUAL_WORK)

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _plan.value = withContext(Dispatchers.IO) {
                try { Planner(ctx).build() } catch (e: SecurityException) { Plan.EMPTY }
            }
            refreshFilesNow()
            refreshAppsNow()
            refreshHealthNow()
            refreshWhatsAppNow()
            lastScanMs.value = settings.lastScanMs
            _loading.value = false
        }
    }

    private val _whatsApp = MutableStateFlow(WhatsAppPlan.EMPTY)
    val whatsApp = _whatsApp.asStateFlow()
    private val _health = MutableStateFlow(HealthPlan.EMPTY)
    val health = _health.asStateFlow()

    private suspend fun refreshHealthNow() {
        _health.value = withContext(Dispatchers.IO) {
            try { HealthRepo(ctx).load() } catch (e: Exception) { HealthPlan.EMPTY }
        }
    }

    private suspend fun refreshWhatsAppNow() {
        _whatsApp.value = withContext(Dispatchers.IO) {
            if (!settings.filesEnabled) WhatsAppPlan.EMPTY
            else try { WhatsAppScanner.scan() } catch (e: Exception) { WhatsAppPlan.EMPTY }
        }
    }

    fun refreshHealth() { viewModelScope.launch { refreshHealthNow() } }

    /** WhatsApp clean-up: to the app's trash (restorable) or deleted right away. */
    fun cleanWhatsApp(files: List<WaFile>, now: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val msg = withContext(Dispatchers.IO) {
                try {
                    if (now) {
                        val freed = WhatsAppScanner.deleteNow(ctx, files)
                        "Freed " + android.text.format.Formatter.formatShortFileSize(ctx, freed)
                    } else {
                        val n = FileTrash(ctx).trash(files.map { java.io.File(it.path) })
                        "$n files moved to trash (restorable for 30 days)"
                    }
                } catch (e: Exception) { "Something went wrong: ${e.message}" }
            }
            _busy.value = false
            _message.value = msg
            refreshWhatsAppNow()
            refreshFilesNow()
        }
    }

    private suspend fun refreshFilesNow() {
        val (plan, trash) = withContext(Dispatchers.IO) {
            if (!settings.filesEnabled || !FilePlaces.hasAccess()) FilePlan.EMPTY to emptyList()
            else try {
                FilePlanner(ctx).build() to FileTrash(ctx).list()
            } catch (e: Exception) {
                FilePlan.EMPTY to emptyList()
            }
        }
        _filePlan.value = plan
        _trash.value = trash
    }

    private suspend fun refreshAppsNow() {
        if (!settings.appsEnabled) { _appPlan.value = AppPlan.EMPTY; return }
        _appsLoading.value = true
        _appPlan.value = withContext(Dispatchers.IO) {
            try { AppRepo(ctx).load() } catch (e: Exception) { AppPlan.EMPTY }
        }
        _appsLoading.value = false
    }

    fun refreshApps() {
        viewModelScope.launch { refreshAppsNow() }
    }

    fun keepApps(pkgs: Collection<String>) {
        settings.keptApps = settings.keptApps + pkgs
        _message.value = "Got it – won't suggest ${if (pkgs.size == 1) "it" else "them"} again"
        refreshApps()
    }

    fun setAppsEnabled(on: Boolean) {
        settings.appsEnabled = on
        appsEnabled.value = on
        refreshApps()
    }

    fun refreshFiles() {
        viewModelScope.launch { refreshFilesNow() }
    }

    fun scan() = ScanWorker.scanNow(ctx)
    fun cancelScan() = ScanWorker.cancelScan(ctx)

    fun consumeMessage() { _message.value = null }

    // ---------- decisions ----------

    fun keep(ids: Collection<Long>) {
        settings.keptIds = settings.keptIds + ids
        _message.value = "Got it – ${ids.size} won't be suggested again"
        refresh()
    }

    fun dontSort(ids: Collection<Long>) {
        settings.noSortIds = settings.noSortIds + ids
        refresh()
    }

    fun renameEvent(key: String, name: String) {
        settings.setEventName(key, name)
        refresh()
    }

    fun setSensitivity(s: Sensitivity) {
        settings.sensitivity = s
        sensitivity.value = s
        refresh()
    }

    fun setEvents(on: Boolean) {
        settings.eventsEnabled = on
        eventsEnabled.value = on
        refresh()
    }

    fun setNightly(on: Boolean) {
        settings.nightlyEnabled = on
        nightlyEnabled.value = on
        ScanWorker.setNightly(ctx, on)
    }

    fun setFilesEnabled(on: Boolean) {
        settings.filesEnabled = on
        filesEnabled.value = on
        refreshFiles()
    }

    // ---------- files ----------

    fun keepFiles(paths: Collection<String>) {
        settings.keptFiles = settings.keptFiles + paths
        _message.value = "Got it – ${paths.size} won't be suggested again"
        refreshFiles()
    }

    fun dontSortFiles(paths: Collection<String>) {
        settings.noSortFiles = settings.noSortFiles + paths
        refreshFiles()
    }

    fun moveFiles(moves: Map<File, List<File>>) = fileJob {
        val n = FileRepo(ctx).move(moves)
        if (n > 0) "Moved $n files into Documents/Sorted" else "Couldn't move these files"
    }

    fun trashFiles(files: List<File>) = fileJob {
        val n = FileTrash(ctx).trash(files)
        if (n > 0) "$n files moved to trash (restorable for 30 days)" else "Nothing deleted"
    }

    fun restore(entries: List<FileTrash.Entry>) = fileJob {
        "Restored ${FileTrash(ctx).restore(entries)} files"
    }

    fun deleteForever(entries: List<FileTrash.Entry>) = fileJob {
        "Deleted ${FileTrash(ctx).deleteForever(entries)} files for good"
    }

    private fun fileJob(work: () -> String) {
        viewModelScope.launch {
            _busy.value = true
            val msg = withContext(Dispatchers.IO) {
                try { work() } catch (e: Exception) { "Something went wrong: ${e.message}" }
            }
            _busy.value = false
            _message.value = msg
            refreshFilesNow()
        }
    }

    fun resetDecisions() {
        settings.resetDecisions()
        _message.value = "Decisions cleared"
        refresh()
    }

    // ---------- actions that change files ----------

    /** Moves photos into their target folders. [moves] maps target folder -> photos. */
    fun move(moves: Map<String, List<MediaItem>>, consent: ConsentRequester) {
        val targetOf = HashMap<Uri, String>()
        moves.forEach { (target, items) -> items.forEach { targetOf[it.uri] = target } }
        if (targetOf.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            var moved = 0
            var cancelled = false
            val repo = MediaRepo(ctx)
            for (chunk in targetOf.keys.toList().chunked(CHUNK)) {
                val pi = MediaStore.createWriteRequest(ctx.contentResolver, chunk)
                if (!consent(pi)) { cancelled = true; break }
                moved += withContext(Dispatchers.IO) {
                    chunk.groupBy { targetOf.getValue(it) }.entries.sumOf { (target, uris) -> repo.move(uris, target) }
                }
            }
            _busy.value = false
            _message.value = when {
                moved > 0 -> "Moved $moved photos"
                cancelled -> "Nothing moved"
                else -> "Couldn't move these photos"
            }
            refresh()
        }
    }

    /** Sends photos to the system trash (recoverable for 30 days). */
    fun trash(items: List<MediaItem>, consent: ConsentRequester) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            var trashed = 0
            for (chunk in items.map { it.uri }.chunked(CHUNK)) {
                val pi = MediaStore.createTrashRequest(ctx.contentResolver, chunk, true)
                if (!consent(pi)) break
                trashed += chunk.size
            }
            _busy.value = false
            _message.value = if (trashed > 0) "$trashed photos moved to trash (restorable for 30 days)"
            else "Nothing deleted"
            refresh()
        }
    }

    companion object {
        private const val CHUNK = 250
    }
}
