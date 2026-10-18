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
import app.phototidy.data.Plan

data class ScanStatus(val running: Boolean, val done: Int, val total: Int)

data class PermState(
    val hasAccess: Boolean,
    val partialOnly: Boolean,
    val canManageMedia: Boolean,
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
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PHOTO TIDY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        if (!perms.hasAccess) {
            InfoCard(
                title = "Let's look at your photos",
                body = "Photo Tidy needs access to your photos to sort them and find junk. " +
                    "Everything happens on this phone – nothing is uploaded.",
                action = "Allow photo access",
                onAction = onRequestAccess,
                primary = true,
            )
            return@Column
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
            SectionLabel(if (scan.running) "Scanning" else "Library")
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
                            plan.analyzed == 0 -> "Start first scan"
                            plan.analyzed < plan.totalPhotos -> "Scan ${plan.totalPhotos - plan.analyzed} new photos"
                            else -> "Check for new photos"
                        }
                    )
                }
            }
        }

        // ---- the two results ----
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

        if (!perms.canManageMedia) {
            InfoCard(
                title = "Fewer pop-ups",
                body = "Android asks you to confirm every move or delete. " +
                    "Turn on \"media management\" for Photo Tidy to skip that (you still review everything in the app).",
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
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(enabled = number > 0, onClick = onClick),
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
                if (number > 0) "REVIEW →" else " ",
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
