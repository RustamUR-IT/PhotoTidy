package app.phototidy.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phototidy.core.Importance
import app.phototidy.data.AppEntry
import app.phototidy.data.AppPlan
import coil.compose.AsyncImage

private enum class AppFilter(val title: String) { UNUSED("Unused"), ALL("Installed"), IMPORTANT("Important"), SYSTEM("Pre-installed") }

@Composable
private fun AppIcon(pkg: String, size: Int = 44) {
    val context = LocalContext.current
    val icon = remember(pkg) {
        try { context.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
    }
    AsyncImage(
        model = icon,
        contentDescription = null,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun ImportanceBadge(imp: Importance) {
    if (imp == Importance.NORMAL) return
    val essential = imp == Importance.ESSENTIAL
    Text(
        imp.title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (essential) Color.White else Accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (essential) Accent else Accent.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun usageLine(a: AppEntry, usageAccess: Boolean): String = when {
    a.unusedDays != null -> "Not opened in ${a.unusedDays} days"
    !usageAccess -> "Installed " + DateUtils.getRelativeTimeSpanString(a.facts.installedMs).toString().lowercase()
    a.facts.lastUsedMs != null -> "Used " + DateUtils.getRelativeTimeSpanString(a.facts.lastUsedMs).toString().lowercase()
    else -> "Not opened recently"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    plan: AppPlan,
    loading: Boolean,
    onBack: () -> Unit,
    onUninstall: (List<AppEntry>) -> Unit,
    onOpenSettings: (AppEntry) -> Unit,
    onKeep: (List<String>) -> Unit,
    onAllowUsage: () -> Unit,
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(if (plan.unused.isNotEmpty()) AppFilter.UNUSED else AppFilter.ALL) }
    val selected = remember { mutableStateListOf<String>() }
    var details by remember { mutableStateOf<AppEntry?>(null) }
    var confirm by remember { mutableStateOf<List<AppEntry>?>(null) }

    LaunchedEffect(plan) {
        val there = plan.apps.filter { it.canUninstall }.map { it.pkg }.toHashSet()
        selected.retainAll { it in there }
    }

    val visible = when (filter) {
        AppFilter.UNUSED -> plan.unused
        AppFilter.ALL -> plan.userApps
        AppFilter.IMPORTANT -> plan.apps.filter { it.importance != Importance.NORMAL }
        AppFilter.SYSTEM -> plan.apps.filter { it.isSystem }
    }
    val chosen = plan.apps.filter { it.pkg in selected }

    /** Removing: if anything important is among them, remind first. */
    fun requestUninstall(list: List<AppEntry>) {
        if (list.any { it.importance != Importance.NORMAL }) confirm = list else onUninstall(list)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (filter == AppFilter.UNUSED) {
                        OutlinedButton(
                            onClick = { onKeep(chosen.map { it.pkg }); selected.clear() },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Keep") }
                    }
                    Button(
                        onClick = { requestUninstall(chosen) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                        modifier = Modifier.weight(1.4f).height(52.dp),
                    ) {
                        Text("Uninstall ${chosen.size} · " + Formatter.formatShortFileSize(context, chosen.sumOf { it.sizeBytes }))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!plan.usageAccess) item {
                Panel {
                    Text("See which apps you never use", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "With \"Usage access\" System Optimizer can tell when each app was last opened and how much space it really takes. " +
                            "This stays on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onAllowUsage) { Text("Allow usage access") }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    items(AppFilter.entries.filter { it != AppFilter.UNUSED || plan.usageAccess }) { f ->
                        val n = when (f) {
                            AppFilter.UNUSED -> plan.unused.size
                            AppFilter.ALL -> plan.userApps.size
                            AppFilter.IMPORTANT -> plan.apps.count { it.importance != Importance.NORMAL }
                            AppFilter.SYSTEM -> plan.apps.count { it.isSystem }
                        }
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text("${f.title} $n") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
            if (loading && plan.apps.isEmpty()) item { Text("Loading apps…", modifier = Modifier.padding(8.dp)) }
            if (!loading && visible.isEmpty()) item {
                Text(
                    if (filter == AppFilter.UNUSED) "No unused apps. Nice." else "Nothing here.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp),
                )
            }
            if (filter == AppFilter.SYSTEM && visible.isNotEmpty()) item {
                Text(
                    "Pre-installed apps can't be uninstalled, but most can be disabled: tap one, then \"Disable in Settings\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(visible, key = { it.pkg }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { details = entry }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.canUninstall) {
                        Checkbox(
                            checked = entry.pkg in selected,
                            onCheckedChange = { if (entry.pkg in selected) selected.remove(entry.pkg) else selected.add(entry.pkg) },
                            colors = CheckboxDefaults.colors(checkedColor = Accent),
                        )
                    } else Spacer(Modifier.width(48.dp))
                    AppIcon(entry.pkg)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            ImportanceBadge(entry.importance)
                            if (!entry.isEnabled) {
                                Spacer(Modifier.width(6.dp))
                                Text("DISABLED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            entry.verdict.whatItDoes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Formatter.formatShortFileSize(context, entry.sizeBytes) + " · " + usageLine(entry, plan.usageAccess),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    details?.let { entry ->
        ModalBottomSheet(onDismissRequest = { details = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(entry.pkg, 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.label, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                        Text(entry.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel("What it does")
                Spacer(Modifier.height(4.dp))
                Text(entry.verdict.whatItDoes, style = MaterialTheme.typography.bodyMedium)
                entry.verdict.reminder?.let {
                    Spacer(Modifier.height(14.dp))
                    Reminder(entry.importance, it)
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Details")
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(
                        "Size: " + Formatter.formatShortFileSize(context, entry.sizeBytes),
                        usageLine(entry, plan.usageAccess),
                        "Installed " + DateUtils.getRelativeTimeSpanString(entry.facts.installedMs).toString().lowercase(),
                        if (entry.isSystem) "Pre-installed" else "Installed by you",
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                if (entry.canUninstall) {
                    Button(
                        onClick = { details = null; requestUninstall(listOf(entry)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (entry.isSystem) "Uninstall updates" else "Uninstall") }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = { details = null; onOpenSettings(entry) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (entry.isSystem) "Disable in Settings" else "Force stop / disable in Settings") }
                if (entry.unusedDays != null) {
                    TextButton(onClick = { details = null; onKeep(listOf(entry.pkg)) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("I use this – stop suggesting it")
                    }
                }
            }
        }
    }

    confirm?.let { list ->
        val flagged = list.filter { it.importance != Importance.NORMAL }
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Default.Warning, null, tint = Accent) },
            title = { Text(if (flagged.size == 1) "Heads up: ${flagged[0].label}" else "Heads up: ${flagged.size} important apps") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    flagged.forEach { a ->
                        if (flagged.size > 1) Text(a.label, fontWeight = FontWeight.Medium)
                        Text(a.verdict.reminder ?: a.verdict.whatItDoes, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        "Android will still ask you to confirm each app.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirm = null; selected.clear(); onUninstall(list) }) {
                    Text("Uninstall anyway", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirm = null
                    // Go ahead with just the normal ones, if there were any
                    val rest = list.filter { it.importance == Importance.NORMAL }
                    if (rest.isNotEmpty()) { selected.removeAll(flagged.map { it.pkg }); onUninstall(rest) }
                }) { Text(if (list.size > flagged.size) "Skip important ones" else "Keep it") }
            },
        )
    }
}

@Composable
private fun Reminder(imp: Importance, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = if (imp == Importance.NORMAL) 0.06f else 0.12f))
            .padding(12.dp),
    ) {
        Icon(Icons.Default.Warning, null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
