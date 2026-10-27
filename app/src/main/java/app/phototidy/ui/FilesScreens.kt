package app.phototidy.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.phototidy.core.FileJunkReason
import app.phototidy.data.FilePlan
import app.phototidy.data.FileTrash
import java.io.File

/** Opens a file in whatever app handles it (PDF viewer, Office, music player…). */
fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(context, "No app on this phone can open this file", Toast.LENGTH_SHORT).show()
    }
}

/** Little square showing the file type, e.g. "PDF". */
@Composable
private fun ExtBadge(ext: String, dim: Boolean) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (dim) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ext.take(4).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.labelSmall,
            color = if (ext == "pdf") Accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun FileRow(
    file: File,
    sizeBytes: Long,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    dim: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Accent),
        )
        ExtBadge(file.extension.lowercase(), dim)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${Formatter.formatShortFileSize(context, sizeBytes)} · $subtitle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { openFile(context, file) }) { Text("OPEN", style = MaterialTheme.typography.labelSmall) }
    }
}

private fun ago(ms: Long) = DateUtils.getRelativeTimeSpanString(ms).toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

// ------------------------------------------------------------------ sort

@Composable
fun FilesSortScreen(
    plan: FilePlan,
    busy: Boolean,
    onBack: () -> Unit,
    onMove: (Map<File, List<File>>) -> Unit,
    onNeverSort: (List<String>) -> Unit,
) {
    val excluded = remember { mutableStateListOf<String>() }
    val disabled = remember { mutableStateListOf<String>() }
    val expanded = remember { mutableStateListOf<String>() }

    val selection = plan.groups.filter { it.key !in disabled }
        .associate { g -> g.targetDir to g.items.filter { it.path !in excluded }.map { it.file } }
        .filterValues { it.isNotEmpty() }
    val count = selection.values.sumOf { it.size }

    Scaffold(
        topBar = { SimpleTopBar("Files to sort", onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { onMove(selection) },
                    enabled = count > 0 && !busy,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) { Text(if (busy) "Working…" else "Move $count files into ${selection.size} folders") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (plan.groups.isEmpty()) {
            Text("Nothing to sort right now.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Loose files from Download, Documents and your phone's main folder go into Documents/Sorted. " +
                        "Folders you made yourself are never touched. Untick anything you want left where it is.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(plan.groups, key = { it.key }) { group ->
                val on = group.key !in disabled
                val open = group.key in expanded
                val shown = if (open) group.items else group.items.take(4)
                val left = group.items.filter { it.path in excluded }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("${group.items.size} files")
                            Spacer(Modifier.height(4.dp))
                            Text(group.category.folder, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "→ Documents/Sorted/${group.category.folder}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = on,
                            onCheckedChange = { if (it) disabled.remove(group.key) else disabled.add(group.key) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    shown.forEach { item ->
                        val isIn = on && item.path !in excluded
                        FileRow(
                            file = item.file,
                            sizeBytes = item.size,
                            subtitle = "${item.folderLabel} · ${ago(item.modified)}",
                            checked = isIn,
                            dim = !isIn,
                            onToggle = { if (item.path in excluded) excluded.remove(item.path) else excluded.add(item.path) },
                        )
                    }
                    if (group.items.size > 4) {
                        TextButton(onClick = { if (open) expanded.remove(group.key) else expanded.add(group.key) }) {
                            Text(if (open) "Show less" else "Show all ${group.items.size}")
                        }
                    }
                    if (left.isNotEmpty()) {
                        TextButton(onClick = {
                            onNeverSort(left.map { it.path })
                            excluded.removeAll(left.map { it.path })
                        }) { Text("Never suggest the ${left.size} I unticked") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ junk

@Composable
fun FilesJunkScreen(
    plan: FilePlan,
    busy: Boolean,
    onBack: () -> Unit,
    onTrash: (List<File>) -> Unit,
    onKeep: (List<String>) -> Unit,
    onOpenTrash: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<String>() }
    var filter by remember { mutableStateOf<FileJunkReason?>(null) }
    LaunchedEffect(plan) {
        val there = plan.junk.map { it.item.path }.toHashSet()
        selected.retainAll { it in there }
    }
    val visible = plan.junk.filter { filter == null || filter in it.reasons }
    val chosen = plan.junk.filter { it.item.path in selected }
    val counts = FileJunkReason.entries.associateWith { r -> plan.junk.count { r in it.reasons } }.filterValues { it > 0 }

    Scaffold(
        topBar = {
            SimpleTopBar("Junk files", onBack) {
                val all = visible.isNotEmpty() && visible.all { it.item.path in selected }
                TextButton(onClick = {
                    if (all) selected.removeAll(visible.map { it.item.path }.toSet())
                    else visible.forEach { if (it.item.path !in selected) selected.add(it.item.path) }
                }) { Text(if (all) "Clear" else "Select all") }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                if (chosen.isEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tick what you want gone. OPEN lets you check a file first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (plan.trashCount > 0) TextButton(onClick = onOpenTrash) { Text("Trash (${plan.trashCount})") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onKeep(chosen.map { it.item.path }); selected.clear() },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Not junk") }
                        Button(
                            onClick = { onTrash(chosen.map { it.item.file }) },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                            modifier = Modifier.weight(1.4f).height(52.dp),
                        ) {
                            val size = Formatter.formatShortFileSize(context, chosen.sumOf { it.item.size })
                            Text(if (busy) "Working…" else "Trash ${chosen.size} · $size")
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (plan.junk.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp)) {
                Text("No junk files found.", style = MaterialTheme.typography.titleMedium)
                if (plan.trashCount > 0) TextButton(onClick = onOpenTrash) { Text("Open trash (${plan.trashCount})") }
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    item {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All ${plan.junk.size}") }, colors = chips())
                    }
                    items(counts.entries.toList(), key = { it.key.name }) { (r, n) ->
                        FilterChip(
                            selected = filter == r,
                            onClick = { filter = if (filter == r) null else r },
                            label = { Text("${r.title} $n") },
                            colors = chips(),
                        )
                    }
                }
            }
            items(visible, key = { it.item.path }) { j ->
                FileRow(
                    file = j.item.file,
                    sizeBytes = j.item.size,
                    subtitle = j.reasons.joinToString(", ") { it.title } + " · " + j.item.folderLabel,
                    checked = j.item.path in selected,
                    onToggle = { if (j.item.path in selected) selected.remove(j.item.path) else selected.add(j.item.path) },
                )
            }
        }
    }
}

@Composable
private fun chips() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

// ------------------------------------------------------------------ trash

@Composable
fun FilesTrashScreen(
    entries: List<FileTrash.Entry>,
    busy: Boolean,
    onBack: () -> Unit,
    onRestore: (List<FileTrash.Entry>) -> Unit,
    onDeleteForever: (List<FileTrash.Entry>) -> Unit,
) {
    val selected = remember { mutableStateListOf<String>() }
    LaunchedEffect(entries) {
        val there = entries.map { it.stored.path }.toHashSet()
        selected.retainAll { it in there }
    }
    val chosen = entries.filter { it.stored.path in selected }
    val target = chosen.ifEmpty { entries }

    Scaffold(
        topBar = { SimpleTopBar("Trash", onBack) },
        bottomBar = {
            if (entries.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onDeleteForever(target) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (chosen.isEmpty()) "Empty trash" else "Delete ${chosen.size}") }
                    Button(
                        onClick = { onRestore(target) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (chosen.isEmpty()) "Restore all" else "Restore ${chosen.size}") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (entries.isEmpty()) {
            Text("Trash is empty.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Files stay here for 30 days, then they're deleted automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(entries, key = { it.stored.path }) { e ->
                FileRow(
                    file = e.stored,
                    sizeBytes = e.stored.length(),
                    subtitle = "from " + File(e.originalPath).parent.orEmpty()
                        .removePrefix(app.phototidy.data.FilePlaces.storage.absolutePath).trimStart('/').ifEmpty { "Phone storage" } +
                        " · " + ago(e.trashedAt),
                    checked = e.stored.path in selected,
                    onToggle = { if (e.stored.path in selected) selected.remove(e.stored.path) else selected.add(e.stored.path) },
                )
            }
        }
    }
}
