package com.jondude1.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jondude1.orbit.data.Orbit
import com.jondude1.orbit.data.Settings
import com.jondude1.orbit.data.Status
import com.jondude1.orbit.data.ThoughtStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    wide: Boolean,
    snackbar: SnackbarHostState,
    onReview: () -> Unit,
    onOpen: (Long) -> Unit,
    onLibrary: () -> Unit,
) {
    val all by ThoughtStore.thoughts.collectAsState()
    val settings by Settings.state.collectAsState()
    val scope = rememberCoroutineScope()

    val due = remember(all) { ThoughtStore.due(list = all) }
    val active = remember(all) { all.filter { it.status == Status.ACTIVE }.sortedByDescending { it.updatedAt } }
    val recent = remember(all) { all.sortedByDescending { it.createdAt }.take(6) }
    val orbiting = remember(all) { all.filter { it.status == Status.ORBITING || it.status == Status.INBOX } }
    var randomId by rememberSaveable { mutableStateOf<Long?>(null) }

    val header: @Composable () -> Unit = {
        val hour = LocalTime.now().hour
        val hello = when (hour) {
            in 5..11 -> "Morning, Jon."
            in 12..16 -> "Afternoon, Jon."
            else -> "Evening, Jon."
        }
        Column {
            Text(hello, style = MaterialTheme.typography.headlineMedium)
            Text(
                "What's buzzing around up there? · " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val capture: @Composable () -> Unit = {
        Panel {
            CaptureBox(onSaved = { t ->
                scope.launch {
                    snackbar.showSnackbar("Caught. It'll swing back ${Orbit.whenText(t.surfaceAt)}.")
                }
            })
        }
    }

    val reviewCard: @Composable () -> Unit = {
        Panel(onClick = if (due.isNotEmpty()) onReview else null) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (due.size) {
                            0 -> "All quiet in orbit"
                            1 -> "1 thought came back around"
                            else -> "${due.size} thoughts came back around"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (due.isEmpty())
                        "Next nudge at ${Orbit.slotTimeText(settings)}. ${orbiting.size} ideas resting until their time."
                    else
                        "Take a minute: keep them, park them, or let them go.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (due.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onReview) { Text("Look now") }
                }
            }
        }
    }

    val motion: @Composable () -> Unit = {
        Column {
            SectionLabel("In motion")
            if (active.isEmpty()) {
                Text(
                    "Nothing started yet. When an idea keeps lighting you up in review, hit \"Start it\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (active.size > settings.focusLimit) {
                    Text(
                        "${active.size} things in motion. Parking a couple might make the rest easier to finish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    active.forEach { t -> ThoughtRow(t) { onOpen(t.id) } }
                }
            }
        }
    }

    val surprise: @Composable () -> Unit = {
        Column {
            SectionLabel("Random spark")
            val picked = randomId?.let { id -> all.firstOrNull { it.id == id } }
            if (picked != null) {
                ThoughtRow(picked) { onOpen(picked.id) }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = {
                    val pool = orbiting.filter { it.id != randomId }
                    randomId = pool.randomOrNull()?.id ?: randomId
                },
                enabled = orbiting.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Casino, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (picked == null) "Pull a forgotten idea" else "Another one")
            }
        }
    }

    val recentList: @Composable () -> Unit = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Just caught", Modifier.weight(1f))
                if (all.size > recent.size) {
                    androidx.compose.material3.TextButton(onClick = onLibrary) { Text("See all ${all.size}") }
                }
            }
            if (recent.isEmpty()) {
                Text(
                    "Nothing here yet. Type anything above — an app idea, a project, a half-thought. Sorting can wait.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { t -> ThoughtRow(t) { onOpen(t.id) } }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(
                Modifier.weight(1.25f).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                header(); capture(); recentList()
            }
            Column(
                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                reviewCard(); motion(); surprise()
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            header(); capture(); reviewCard(); motion(); recentList(); surprise()
            Spacer(Modifier.fillMaxWidth().height(24.dp))
        }
    }
}
