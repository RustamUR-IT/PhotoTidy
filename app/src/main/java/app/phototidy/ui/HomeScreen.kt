package app.phototidy.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.phototidy.data.AppPlan
import app.phototidy.data.FilePlan
import app.phototidy.data.Plan

data class ScanStatus(val running: Boolean, val done: Int, val total: Int, val phase: String = "photos")

data class PermState(
    val hasAccess: Boolean,
    val partialOnly: Boolean,
    val canManageMedia: Boolean,
    val hasFileAccess: Boolean = false,
    /** Changes on every return to the app so screens re-check things like the default home app. */
    val checkedAt: Long = 0L,
)

@Composable
fun HomeScreen(
    plan: Plan,
    scan: ScanStatus,
    perms: PermState,
    lastScanMs: Long,
    onRequestAccess: () -> Unit,
    onEnableManageMedia: () -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenJunk: () -> Unit,
    onOpenSettings: () -> Unit,
    filePlan: FilePlan = FilePlan.EMPTY,
    filesEnabled: Boolean = false,
    onAllowFiles: () -> Unit = {},
    onOpenFileSort: () -> Unit = {},
    onOpenFileJunk: () -> Unit = {},
    appPlan: AppPlan = AppPlan.EMPTY,
    appsEnabled: Boolean = false,
    onOpenApps: () -> Unit = {},
    onSetHome: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDefaultHome = context.packageManager.resolveActivity(
        android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName == context.packageName
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SYSTEM OPTIMIZER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        if (!perms.hasAccess) {
            InfoCard(
                title = "Let's look at your photos",
                body = "System Optimizer needs access to your photos to sort them and find junk. " +
                    "Everything happens on this phone – nothing is uploaded.",
                action = "Allow photo access",
                onAction = onRequestAccess,
                primary = true,
            )
        }
        if (perms.partialOnly) {
            InfoCard(
                title = "Only some photos are visible",
                body = "You allowed access to selected photos only, so the rest won't be sorted.",
                action = "Allow all photos",
                onAction = onRequestAccess,
            )
        }

        // ---- scan status ----
        Panel {
            SectionLabel(if (scan.running) "Scanning ${scan.phase}" else "Library")
            Spacer(Modifier.height(8.dp))
            if (scan.running) {
                Text(
                    if (scan.total > 0) "${scan.done} / ${scan.total}" else "Getting ready…",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(10.dp))
                if (scan.total > 0) {
                    LinearProgressIndicator(
                        progress = { scan.done.toFloat() / scan.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "You can leave the app – it keeps going in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onCancelScan) { Text("Stop") }
            } else {
                Text(
                    "${plan.analyzed} of ${plan.totalPhotos} photos analysed",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (filesEnabled && perms.hasFileAccess) {
                    Text(
                        "${filePlan.totalFiles} loose files checked",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (lastScanMs > 0) {
                    Text(
                        "Last scan " + DateUtils.getRelativeTimeSpanString(lastScanMs).toString().lowercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            plan.analyzed == 0 && filePlan.totalFiles == 0 -> "Start first scan"
                            plan.analyzed < plan.totalPhotos -> "Scan ${plan.totalPhotos - plan.analyzed} new photos"
                            else -> "Check for new photos"
                        }
                    )
                }
            }
        }

        // ---- photo results ----
        if (perms.hasAccess) {
        SectionLabel("Photos", Modifier.padding(start = 4.dp, top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(
                label = "Ready to sort",
                number = plan.sortCount,
                sub = if (plan.sortGroups.isEmpty()) "nothing yet" else "into ${plan.sortGroups.size} folders",
                onClick = onOpenSort,
                modifier = Modifier.weight(1f),
            )
            BigTile(
                label = "Possible junk",
                number = plan.junk.size,
                sub = if (plan.junk.isEmpty()) "all clean" else Formatter.formatShortFileSize(context, plan.junkBytes),
                onClick = onOpenJunk,
                accent = plan.junk.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
        }

        // ---- file results ----
        if (filesEnabled) {
            SectionLabel("Files", Modifier.padding(start = 4.dp, top = 4.dp))
            if (!perms.hasFileAccess) {
                InfoCard(
                    title = "Tidy your files too",
                    body = "Sort PDFs, documents and downloads into folders like Invoices, Tickets or Contracts, " +
                        "and find leftovers like old installers and duplicate downloads. " +
                        "Android calls the permission for this \"All files access\".",
                    action = "Allow file access",
                    onAction = onAllowFiles,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigTile(
                        label = "Files to sort",
                        number = filePlan.sortCount,
                        sub = if (filePlan.groups.isEmpty()) "nothing yet" else "into ${filePlan.groups.size} folders",
                        onClick = onOpenFileSort,
                        modifier = Modifier.weight(1f),
                    )
                    BigTile(
                        label = "Junk files",
                        number = filePlan.junk.size,
                        sub = if (filePlan.junk.isEmpty()) {
                            if (filePlan.trashCount > 0) "trash: ${filePlan.trashCount}" else "all clean"
                        } else Formatter.formatShortFileSize(context, filePlan.junkBytes),
                        onClick = onOpenFileJunk,
                        accent = filePlan.junk.isNotEmpty(),
                        alwaysClickable = filePlan.trashCount > 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---- apps ----
        if (appsEnabled && appPlan.apps.isNotEmpty()) {
            SectionLabel("Apps", Modifier.padding(start = 4.dp, top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (appPlan.usageAccess) {
                    BigTile(
                        label = "Unused apps",
                        number = appPlan.unused.size,
                        sub = if (appPlan.unused.isEmpty()) "all in use" else Formatter.formatShortFileSize(context, appPlan.unusedBytes),
                        onClick = onOpenApps,
                        accent = appPlan.unused.isNotEmpty(),
                        alwaysClickable = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                BigTile(
                    label = "Installed apps",
                    number = appPlan.userApps.size,
                    sub = "${appPlan.apps.count { it.importance == app.phototidy.core.Importance.IMPORTANT }} important",
                    onClick = onOpenApps,
                    alwaysClickable = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- home screen ----
        SectionLabel("Home screen", Modifier.padding(start = 4.dp, top = 4.dp))
        Panel {
            Text("Smart widgets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Put self-sorting widgets on your normal home screen. They fill themselves with the right apps, " +
                    "most-used first, and keep up with new apps. Your other widgets and icons stay where they are.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { pinWidget(context, dashboard = true) }, modifier = Modifier.weight(1f)) {
                    Text("Add dashboard")
                }
                OutlinedButton(
                    onClick = { pinWidget(context, dashboard = false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                ) { Text("Add folder") }
            }
        }
        InfoCard(
            title = if (isDefaultHome) "Auto-sorted home screen is on" else "Optional: own home screen",
            body = if (isDefaultHome) "Your apps are kept in folders automatically. Long-press the home screen for widgets, " +
                "wallpaper or to switch back."
            else "Use System Optimizer as your whole home screen: every app in a matching folder automatically. " +
                "This replaces the Nothing home screen (widgets need to be added again once). You can switch back anytime.",
            action = if (isDefaultHome) "Change home app" else "Try it",
            onAction = onSetHome,
        )

        if (perms.hasAccess && !perms.canManageMedia) {
            InfoCard(
                title = "Fewer pop-ups",
                body = "Android asks you to confirm every move or delete. " +
                    "Turn on \"media management\" for System Optimizer to skip that (you still review everything in the app).",
                action = "Turn on",
                onAction = onEnableManageMedia,
            )
        }

        Spacer(Modifier.height(8.dp))
        SectionLabel("On-device only · nothing uploaded", Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun Panel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun BigTile(
    label: String,
    number: Int,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    alwaysClickable: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(enabled = number > 0 || alwaysClickable, onClick = onClick),
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionLabel(label)
            Spacer(Modifier.height(10.dp))
            Text(
                number.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = if (accent) Accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                if (number > 0 || alwaysClickable) "REVIEW →" else " ",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, action: String, onAction: () -> Unit, primary: Boolean = false) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (primary) {
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        } else {
            OutlinedButton(
                onClick = onAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(action)
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

private fun pinWidget(context: android.content.Context, dashboard: Boolean) {
    val ok = try { app.phototidy.home.SmartWidgets.requestPin(context, dashboard) } catch (_: Exception) { false }
    if (!ok) {
        android.widget.Toast.makeText(
            context,
            "Long-press your home screen → Widgets → System Optimizer",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
}
