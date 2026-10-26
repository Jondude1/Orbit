package com.jondude1.orbit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jondude1.orbit.data.Orbit
import com.jondude1.orbit.data.Settings
import com.jondude1.orbit.data.Status
import com.jondude1.orbit.data.Thought
import com.jondude1.orbit.data.ThoughtStore

private class Tally {
    var kept by mutableIntStateOf(0)
    var parked by mutableIntStateOf(0)
    var started by mutableIntStateOf(0)
    var released by mutableIntStateOf(0)
}

@Composable
fun ReviewScreen(onOpen: (Long) -> Unit, onDone: () -> Unit) {
    val all by ThoughtStore.thoughts.collectAsState()
    val settings by Settings.state.collectAsState()
    var queue by remember { mutableStateOf(ThoughtStore.due().take(settings.perReview).map { it.id }) }
    var index by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }
    val next: () -> Unit = { index++ }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("Orbit review", style = MaterialTheme.typography.headlineMedium)
            Text(
                "One at a time. Gut feeling is fine — nothing here is permanent.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            if (queue.isNotEmpty() && index < queue.size) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { index.toFloat() / queue.size },
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${index + 1} of ${queue.size}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(20.dp))
            }

            AnimatedContent(
                targetState = index,
                transitionSpec = { (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut() },
                label = "review",
            ) { i ->
                val id = queue.getOrNull(i)
                val t = id?.let { tid -> all.firstOrNull { it.id == tid } }
                when {
                    queue.isEmpty() -> EmptyNote(
                        "Nothing's due right now",
                        "Your ideas are resting. They'll swing back around ${Orbit.slotTimeText(settings)} on review days.",
                        Modifier.fillMaxWidth(),
                    )
                    i >= queue.size -> Finished(tally, onDone) {
                        queue = ThoughtStore.due().take(settings.perReview).map { it.id }
                        index = 0
                    }
                    t == null -> {
                        // Deleted mid-review; skip it.
                        androidx.compose.runtime.LaunchedEffect(i) { next() }
                    }
                    ThoughtStore.isStaleActive(t) || (t.status == Status.ACTIVE) -> StaleCard(t, tally, onOpen, next)
                    else -> OrbitCard(t, tally, settings.focusLimit, onOpen, next)
                }
            }
        }
    }
}

@Composable
private fun ThoughtFace(t: Thought, onOpen: (Long) -> Unit, eyebrow: String? = null) {
    val kc = OrbitColors.kind(t.kind)
    Panel {
        Column {
            if (eyebrow != null) {
                Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(kc))
                Spacer(Modifier.width(8.dp))
                Text(t.kind.label, style = MaterialTheme.typography.labelLarge, color = kc)
                Spacer(Modifier.width(10.dp))
                SparkDots(t.spark)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onOpen(t.id) }) { Text("Edit") }
            }
            Spacer(Modifier.height(10.dp))
            Text(t.title, style = MaterialTheme.typography.headlineSmall)
            if (t.body.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(t.body, style = MaterialTheme.typography.bodyLarge)
            }
            if (t.nextStep.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Next step: ${t.nextStep}", style = MaterialTheme.typography.bodyMedium, color = OrbitColors.teal)
            }
            if (t.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    t.notes, maxLines = 5,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Caught ${Orbit.agoText(t.createdAt)}" +
                    (if (t.surfacedCount > 0) " · back ${t.surfacedCount}× before" else " · first time back"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BigButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
    val m = modifier.height(56.dp)
    when {
        outlined -> OutlinedButton(onClick = onClick, modifier = m) { content() }
        tonal -> FilledTonalButton(onClick = onClick, modifier = m) { content() }
        else -> Button(onClick = onClick, modifier = m) { content() }
    }
}

@Composable
private fun OrbitCard(t: Thought, tally: Tally, focusLimit: Int, onOpen: (Long) -> Unit, next: () -> Unit) {
    var mode by remember(t.id) { mutableStateOf("main") }
    var step by remember(t.id) { mutableStateOf(t.nextStep) }

    Column {
        ThoughtFace(t, onOpen, eyebrow = if (t.status == Status.INBOX) "From ${Orbit.agoText(t.createdAt)}" else null)
        Spacer(Modifier.height(20.dp))
        when (mode) {
            "start" -> Column {
                val active = ThoughtStore.activeCount()
                if (active >= focusLimit) {
                    Text(
                        "You already have $active things in motion. Totally your call — just a heads-up.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    label = { Text("What's the tiniest next step?") },
                    placeholder = { Text("Something you could do in 10 minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Start it", Icons.Rounded.PlayArrow, Modifier.weight(1f)) {
                        ThoughtStore.start(t.id, step); tally.started++; next()
                    }
                    BigButton("Back", null, Modifier.weight(1f), outlined = true) { mode = "main" }
                }
            }
            "when" -> Column {
                Text("When should it come back?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                WhenChips { w -> ThoughtStore.bringBack(t.id, w); tally.parked++; next() }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { mode = "main" }) { Text("Back") }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Still excites me", Icons.Rounded.LocalFireDepartment, Modifier.weight(1f), tonal = true) {
                        ThoughtStore.sparked(t.id); tally.kept++; next()
                    }
                    BigButton("Start it", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { mode = "start" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Not now", Icons.Rounded.NightsStay, Modifier.weight(1f), outlined = true) {
                        ThoughtStore.notNow(t.id); tally.parked++; next()
                    }
                    BigButton("Pick when…", Icons.Rounded.Schedule, Modifier.weight(1f), outlined = true) { mode = "when" }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { ThoughtStore.letGo(t.id); tally.released++; next() }) {
                        Text("Let it go", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "\"Still excites me\" brings it back sooner. \"Not now\" spaces it further out each time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StaleCard(t: Thought, tally: Tally, onOpen: (Long) -> Unit, next: () -> Unit) {
    Column {
        ThoughtFace(t, onOpen, eyebrow = "Still on this? Last touched ${Orbit.agoText(t.updatedAt)}")
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Still going", Icons.Rounded.PlayArrow, Modifier.weight(1f)) {
                    ThoughtStore.stillGoing(t.id); tally.kept++; next()
                }
                BigButton("Done!", Icons.Rounded.Celebration, Modifier.weight(1f), tonal = true) {
                    ThoughtStore.done(t.id); tally.kept++; next()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Park it for now", Icons.Rounded.NightsStay, Modifier.weight(1f), outlined = true) {
                    ThoughtStore.park(t.id); tally.parked++; next()
                }
                BigButton("Let it go", null, Modifier.weight(1f), outlined = true) {
                    ThoughtStore.letGo(t.id); tally.released++; next()
                }
            }
        }
    }
}

@Composable
private fun Finished(tally: Tally, onDone: () -> Unit, onMore: () -> Unit) {
    val remaining = ThoughtStore.due().size
    Panel {
        Column {
            Text("That's the orbit for now.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val parts = buildList {
                if (tally.kept > 0) add("${tally.kept} still glowing")
                if (tally.started > 0) add("${tally.started} started")
                if (tally.parked > 0) add("${tally.parked} parked")
                if (tally.released > 0) add("${tally.released} let go")
            }
            Text(
                if (parts.isEmpty()) "Nothing changed — that's fine too." else parts.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDone) { Text("Back home") }
                if (remaining > 0) {
                    OutlinedButton(onClick = onMore) { Text("Keep going ($remaining more)") }
                }
            }
        }
    }
}
