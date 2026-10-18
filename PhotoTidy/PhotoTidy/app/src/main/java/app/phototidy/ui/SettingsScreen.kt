package app.phototidy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.phototidy.core.Sensitivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sensitivity: Sensitivity,
    eventsEnabled: Boolean,
    nightlyEnabled: Boolean,
    canManageMedia: Boolean,
    onBack: () -> Unit,
    onSensitivity: (Sensitivity) -> Unit,
    onEvents: (Boolean) -> Unit,
    onNightly: (Boolean) -> Unit,
    onManageMedia: () -> Unit,
    onResetDecisions: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                SectionLabel("Junk detection")
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow {
                    Sensitivity.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = s == sensitivity,
                            onClick = { onSensitivity(s) },
                            shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                        ) { Text(s.title) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (sensitivity) {
                        Sensitivity.CAREFUL -> "Only flags obvious junk: black frames, very blurry shots, exact duplicates, screenshots older than a year."
                        Sensitivity.NORMAL -> "Flags blurry and dark shots, near-duplicates from bursts, screenshots older than 6 months."
                        Sensitivity.THOROUGH -> "Also catches slightly soft shots, looser duplicates and screenshots older than 2 months. Review carefully."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ToggleRow(
                title = "Group trips & events",
                body = "15+ camera photos taken close together go into one dated folder you can rename.",
                checked = eventsEnabled,
                onChange = onEvents,
            )
            ToggleRow(
                title = "Check every night",
                body = "While charging, look at new photos and send a notification when there's something to review. Nothing is moved or deleted without you.",
                checked = nightlyEnabled,
                onChange = onNightly,
            )

            Panel {
                SectionLabel("Media management")
                Spacer(Modifier.height(6.dp))
                Text(
                    if (canManageMedia) "On – moves and deletes happen without extra Android pop-ups."
                    else "Off – Android will ask you to confirm each batch of moves or deletes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onManageMedia) { Text(if (canManageMedia) "Change" else "Turn on") }
            }

            Panel {
                SectionLabel("Your decisions")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Photos you marked \"Not junk\" or \"Never suggest\" are remembered.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onResetDecisions) { Text("Forget my decisions") }
            }

            Panel {
                SectionLabel("Privacy")
                Spacer(Modifier.height(6.dp))
                Text(
                    "All analysis runs on this phone with offline models built into the app. " +
                        "Your photos are never uploaded. (Google's ML Kit library may send anonymous " +
                        "usage statistics – never images.)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
