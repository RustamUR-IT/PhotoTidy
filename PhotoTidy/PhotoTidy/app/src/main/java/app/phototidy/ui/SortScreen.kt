package app.phototidy.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.phototidy.data.MediaItem
import app.phototidy.data.Plan
import app.phototidy.data.SortGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortScreen(
    plan: Plan,
    busy: Boolean,
    onBack: () -> Unit,
    onMove: (Map<String, List<MediaItem>>) -> Unit,
    onRenameEvent: (key: String, name: String) -> Unit,
    onNeverSort: (List<Long>) -> Unit,
) {
    val excluded = remember { mutableStateListOf<Long>() }
    val disabledGroups = remember { mutableStateListOf<String>() }
    var preview by remember { mutableStateOf<MediaItem?>(null) }
    var renaming by remember { mutableStateOf<SortGroup?>(null) }

    val selection: Map<String, List<MediaItem>> = plan.sortGroups
        .filter { it.key !in disabledGroups }
        .flatMap { g -> g.items.filter { it.id !in excluded }.map { g.targetPath to it } }
        .groupBy({ it.first }, { it.second })
    val count = selection.values.sumOf { it.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ready to sort") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { onMove(selection) },
                    enabled = count > 0 && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                ) {
                    Text(if (busy) "Working…" else "Move $count photos into ${selection.size} folders")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (plan.sortGroups.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp)) {
                Text("Nothing to sort right now.", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Tap a photo to leave it where it is. Long-press to look closer. " +
                        "Photos go into Pictures/Sorted, so your gallery app still shows them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(plan.sortGroups, key = { it.key }) { group ->
                val enabled = group.key !in disabledGroups
                val groupExcluded = group.items.filter { it.id in excluded }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel(if (group.isEvent) "Event · ${group.items.size}" else "${group.items.size} photos")
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(group.title, style = MaterialTheme.typography.titleMedium)
                                if (group.isEvent) {
                                    IconButton(onClick = { renaming = group }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Edit, "Rename", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Text(
                                "→ " + group.targetPath.removePrefix("Pictures/").trimEnd('/'),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { on ->
                                if (on) disabledGroups.remove(group.key) else disabledGroups.add(group.key)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(group.items, key = { it.id }) { item ->
                            val isOut = !enabled || item.id in excluded
                            Thumb(
                                item = item,
                                mark = if (isOut) ThumbMark.EXCLUDED else ThumbMark.NONE,
                                onClick = {
                                    if (item.id in excluded) excluded.remove(item.id) else excluded.add(item.id)
                                },
                                onLongClick = { preview = item },
                                modifier = Modifier.width(92.dp),
                            )
                        }
                    }
                    if (groupExcluded.isNotEmpty()) {
                        TextButton(onClick = {
                            onNeverSort(groupExcluded.map { it.id })
                            excluded.removeAll(groupExcluded.map { it.id })
                        }) {
                            Text("Never suggest the ${groupExcluded.size} I left out")
                        }
                    }
                }
            }
        }
    }

    preview?.let { item ->
        PreviewDialog(item, listOf("Taken in: ${item.relativePath.trimEnd('/')}")) { preview = null }
    }

    renaming?.let { group ->
        var name by remember(group.key) { mutableStateOf(group.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Name this event") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Salzburg weekend") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onRenameEvent(group.key, name.trim())
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}
