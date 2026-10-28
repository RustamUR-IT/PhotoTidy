package app.phototidy.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phototidy.core.WaCategory
import app.phototidy.core.WaFile
import app.phototidy.core.WhatsAppRules
import app.phototidy.data.HealthPlan
import app.phototidy.data.WhatsAppPlan
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun PkgIcon(pkg: String) {
    val context = LocalContext.current
    val icon = remember(pkg) { try { context.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null } }
    AsyncImage(
        model = icon,
        contentDescription = null,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun UsageAccessCard(onAllowUsage: () -> Unit, text: String) {
    Panel {
        Text("Needs \"Usage access\"", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAllowUsage) { Text("Allow usage access") }
    }
}

// =============================================================== C1 WhatsApp

private enum class Age(val title: String, val days: Int) {
    ALL("All", 0), MONTH("Older than 1 month", 30), QUARTER("Older than 3 months", 90), YEAR("Older than 1 year", 365)
}

@Composable
fun WhatsAppScreen(
    plan: WhatsAppPlan,
    hasFileAccess: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onAllowFiles: () -> Unit,
    onClean: (files: List<WaFile>, now: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var age by remember { mutableStateOf(Age.MONTH) }
    val on = remember { mutableStateListOf<WaCategory>().apply { addAll(WaCategory.entries.filter { it.defaultOn }) } }
    var confirmNow by remember { mutableStateOf(false) }

    val groups = WhatsAppRules.group(plan.files, age.days, System.currentTimeMillis())
    val chosen = groups.filter { it.category in on }.flatMap { it.files }
    val chosenBytes = chosen.sumOf { it.sizeBytes }
    fun size(b: Long) = Formatter.formatShortFileSize(context, b)

    Scaffold(
        topBar = { Bar("WhatsApp clean-up", onBack) },
        bottomBar = {
            if (chosen.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = { onClean(chosen, false) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (busy) "Working…" else "Move ${chosen.size} files · ${size(chosenBytes)} to trash") }
                    TextButton(onClick = { confirmNow = true }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Delete now instead (frees space right away)")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!hasFileAccess) {
                item {
                    Panel {
                        Text("Needs file access", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("To look into WhatsApp's media folder, allow \"All files access\".", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onAllowFiles) { Text("Allow file access") }
                    }
                }
                return@LazyColumn
            }
            if (!plan.found) {
                item { Text("WhatsApp media folder not found on this phone.", style = MaterialTheme.typography.titleMedium) }
                return@LazyColumn
            }
            item {
                Text(
                    "WhatsApp uses ${size(plan.totalBytes)} on your phone. The safe kinds are already ticked. " +
                        "Your chats themselves aren't touched – only the media files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Age.entries) { a ->
                        FilterChip(selected = age == a, onClick = { age = a }, label = { Text(a.title) }, colors = chipColors())
                    }
                }
            }
            if (groups.isEmpty()) item { Text("Nothing here for this age.", modifier = Modifier.padding(8.dp)) }
            items(groups, key = { it.category.name }) { g ->
                val checked = g.category in on
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { if (checked) on.remove(g.category) else on.add(g.category) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { if (checked) on.remove(g.category) else on.add(g.category) },
                        colors = CheckboxDefaults.colors(checkedColor = Accent),
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.category.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(size(g.bytes), style = MaterialTheme.typography.labelMedium, color = if (checked) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            "${g.files.size} files · ${g.category.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmNow) {
        AlertDialog(
            onDismissRequest = { confirmNow = false },
            title = { Text("Delete for good?") },
            text = { Text("${chosen.size} files (${size(chosenBytes)}) will be deleted right away and can't be restored.") },
            confirmButton = { TextButton(onClick = { confirmNow = false; onClean(chosen, true) }) { Text("Delete", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmNow = false }) { Text("Cancel") } },
        )
    }
}

// =============================================================== E1 Privacy

@Composable
fun PrivacyScreen(
    plan: HealthPlan,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    onAllowUsage: () -> Unit,
) {
    var byPermission by remember { mutableStateOf(plan.privacy.isEmpty()) }
    val groups = remember(plan) { app.phototidy.core.PrivacyRules.byPermission(plan.privacyApps) }

    Scaffold(topBar = { Bar("Privacy check", onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Which apps can use your camera, microphone, location, contacts and more. Tap an app to change what it may do " +
                        "(App info → Permissions).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!plan.usageAccess) item {
                UsageAccessCard(onAllowUsage, "With it, the check also finds apps you haven't opened in a long time that still have access.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !byPermission, onClick = { byPermission = false }, label = { Text("Worth checking ${plan.privacy.size}") }, colors = chipColors())
                    FilterChip(selected = byPermission, onClick = { byPermission = true }, label = { Text("By permission") }, colors = chipColors())
                }
            }
            if (!byPermission) {
                if (plan.privacy.isEmpty()) item { Text("Nothing suspicious found. 👍", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp)) }
                items(plan.privacy, key = { it.app.pkg }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpenApp(f.app.pkg) }.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        PkgIcon(f.app.pkg)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.app.label, style = MaterialTheme.typography.titleMedium)
                            f.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Spacer(Modifier.height(4.dp))
                            Text("CHECK PERMISSIONS →", style = MaterialTheme.typography.labelSmall, color = Accent)
                        }
                    }
                }
            } else {
                groups.forEach { (perm, apps) ->
                    item(key = "h-" + perm.name) {
                        SectionLabel("${perm.title} · ${apps.size}", Modifier.padding(top = 10.dp, start = 4.dp))
                    }
                    items(apps, key = { perm.name + it.pkg }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenApp(a.pkg) }.padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PkgIcon(a.pkg)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            a.daysUnused?.takeIf { it >= 30 }?.let {
                                Text("unused ${it}d", style = MaterialTheme.typography.labelSmall, color = Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================== D1 Battery

@Composable
fun BatteryScreen(
    plan: HealthPlan,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    onAllowUsage: () -> Unit,
    onBatterySettings: () -> Unit,
) {
    Scaffold(topBar = { Bar("Battery drainers", onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "Apps that kept working in the background during the last 7 days – much more than you actually used them. " +
                        "Tap one, then App info → Battery → Restricted. Music, navigation, fitness and similar apps are left out on purpose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!plan.usageAccess) {
                item { UsageAccessCard(onAllowUsage, "Android only shows how long apps run in the background with usage access.") }
                return@LazyColumn
            }
            if (plan.battery.isEmpty()) item {
                Text("No battery drainers found this week. 🔋", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            }
            items(plan.battery, key = { it.app.pkg }) { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenApp(f.app.pkg) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PkgIcon(f.app.pkg)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(f.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("RESTRICT →", style = MaterialTheme.typography.labelSmall, color = Accent)
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBatterySettings,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("All apps without battery saving") }
            }
        }
    }
}
