package app.phototidy.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.phototidy.core.JunkReason
import app.phototidy.data.JunkItem
import app.phototidy.data.MediaItem
import app.phototidy.data.Plan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunkScreen(
    plan: Plan,
    busy: Boolean,
    onBack: () -> Unit,
    onTrash: (List<MediaItem>) -> Unit,
    onKeep: (List<Long>) -> Unit,
) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<Long>() }
    var filter by remember { mutableStateOf<JunkReason?>(null) }
    var preview by remember { mutableStateOf<JunkItem?>(null) }

    val visible = plan.junk.filter { filter == null || filter in it.reasons }
    LaunchedEffect(plan) {
        val stillThere = plan.junk.map { it.item.id }.toHashSet()
        selected.retainAll { it in stillThere }
    }
    val selectedItems = plan.junk.filter { it.item.id in selected }
    val reasonCounts = JunkReason.entries.associateWith { r -> plan.junk.count { r in it.reasons } }
        .filterValues { it > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Possible junk") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.item.id in selected }
                    TextButton(onClick = {
                        if (allVisibleSelected) selected.removeAll(visible.map { it.item.id }.toSet())
                        else visible.forEach { if (it.item.id !in selected) selected.add(it.item.id) }
                    }) { Text(if (allVisibleSelected) "Clear" else "Select all") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                if (selectedItems.isEmpty()) {
                    Text(
                        "Tap the photos you want gone. Long-press to look closer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onKeep(selectedItems.map { it.item.id })
                                selected.clear()
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) { Text("Not junk") }
                        Button(
                            onClick = { onTrash(selectedItems.map { it.item }) },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(52.dp),
                        ) {
                            val size = Formatter.formatShortFileSize(context, selectedItems.sumOf { it.item.sizeBytes })
                            Text(if (busy) "Working…" else "Trash ${selectedItems.size} · $size")
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (plan.junk.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp)) {
                Text("No junk found. Nice and tidy.", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filter = null },
                            label = { Text("All ${plan.junk.size}") },
                            colors = chipColors(),
                        )
                    }
                    items(reasonCounts.entries.toList(), key = { it.key.name }) { (reason, n) ->
                        FilterChip(
                            selected = filter == reason,
                            onClick = { filter = if (filter == reason) null else reason },
                            label = { Text("${reason.title} $n") },
                            colors = chipColors(),
                        )
                    }
                }
            }
            items(visible, key = { it.item.id }) { junk ->
                val id = junk.item.id
                Thumb(
                    item = junk.item,
                    mark = if (id in selected) ThumbMark.SELECTED else ThumbMark.NONE,
                    caption = junk.reasons.first().title,
                    onClick = { if (id in selected) selected.remove(id) else selected.add(id) },
                    onLongClick = { preview = junk },
                )
            }
        }
    }

    preview?.let { junk ->
        PreviewDialog(
            junk.item,
            junk.reasons.map { "${it.title}: ${it.explanation}" },
        ) { preview = null }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)
