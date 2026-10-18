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

    val scanWork: LiveData<List<WorkInfo>> =
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkLiveData(ScanWorker.MANUAL_WORK)

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _plan.value = withContext(Dispatchers.IO) {
                try { Planner(ctx).build() } catch (e: SecurityException) { Plan.EMPTY }
            }
            lastScanMs.value = settings.lastScanMs
            _loading.value = false
        }
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
