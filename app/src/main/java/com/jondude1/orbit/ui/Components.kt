package com.jondude1.orbit.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jondude1.orbit.data.Kind
import com.jondude1.orbit.data.Orbit
import com.jondude1.orbit.data.Status
import com.jondude1.orbit.data.Thought
import com.jondude1.orbit.data.ThoughtStore
import com.jondude1.orbit.update.UpdateState
import com.jondude1.orbit.update.Updater

// ---- Voice --------------------------------------------------------------------

/** Returns a function that opens Android's speech-to-text and hands back the words. */
@Composable
fun rememberVoiceInput(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) latest(text)
    }
    return remember(launcher) {
        {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "What's the thought?")
            }
            try {
                launcher.launch(i)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No speech-to-text app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ---- Small pieces -------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindChips(selected: Kind, onSelect: (Kind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Kind.entries.forEach { k ->
            val c = OrbitColors.kind(k)
            FilterChip(
                selected = k == selected,
                onClick = { onSelect(k) },
                label = { Text(k.label) },
                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(c)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.copy(alpha = 0.22f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

private val sparkWords = listOf("Curious", "Ooh", "Can't stop thinking about it")

@Composable
fun SparkPicker(spark: Int, onChange: (Int) -> Unit, showLabel: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..3).forEach { i ->
            IconButton(onClick = { onChange(i) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = "Spark $i",
                    tint = if (i <= spark) OrbitColors.amber else MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (showLabel) {
            Text(
                sparkWords[(spark - 1).coerceIn(0, 2)],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SparkDots(spark: Int) {
    Row {
        (1..spark).forEach {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = OrbitColors.amber, modifier = Modifier.size(14.dp))
        }
    }
}

fun metaText(t: Thought): String = when (t.status) {
    Status.INBOX -> "new · back ${Orbit.whenText(t.surfaceAt)}"
    Status.ORBITING -> "back ${Orbit.whenText(t.surfaceAt)}"
    Status.ACTIVE -> if (t.nextStep.isNotBlank()) "next: ${t.nextStep}" else "in motion"
    Status.DONE -> "done"
    Status.RELEASED -> "let go"
}

@Composable
fun ThoughtRow(t: Thought, selected: Boolean = false, onClick: () -> Unit) {
    val kc = OrbitColors.kind(t.kind)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(1.dp, kc.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(kc))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).weight(1f)) {
                Text(
                    t.title.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (t.isOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${t.kind.label} · ${metaText(t)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    SparkDots(t.spark)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhenChips(onPick: (Orbit.When) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Orbit.When.entries.forEach { w ->
            SuggestionChip(onClick = { onPick(w) }, label = { Text(w.label) })
        }
    }
}

// ---- Capture box (home screen and quick-capture popup) --------------------------

@Composable
fun CaptureBox(
    modifier: Modifier = Modifier,
    initialText: String = "",
    autoFocus: Boolean = false,
    startWithVoice: Boolean = false,
    minLines: Int = 4,
    onSaved: (Thought) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var kindName by rememberSaveable { mutableStateOf(Kind.THOUGHT.name) }
    var spark by rememberSaveable { mutableIntStateOf(1) }
    val kind = Kind.from(kindName)
    val focus = remember { FocusRequester() }

    val voice = rememberVoiceInput { heard ->
        text = if (text.isBlank()) heard else text.trimEnd() + " " + heard
    }

    fun save() {
        if (text.isBlank()) return
        val t = ThoughtStore.capture(text, kind, spark)
        text = ""
        spark = 1
        onSaved(t)
    }

    LaunchedEffect(Unit) {
        if (startWithVoice) voice() else if (autoFocus) runCatching { focus.requestFocus() }
    }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Dump it here. Messy is fine.", style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    // Book Cover Keyboard: Ctrl+Enter saves.
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && e.isCtrlPressed) {
                        save(); true
                    } else false
                },
        )
        Spacer(Modifier.height(12.dp))
        KindChips(kind) { kindName = it.name }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SparkPicker(spark, { spark = it })
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = voice, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = "Speak a thought")
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = { save() }, enabled = text.isNotBlank(), modifier = Modifier.height(48.dp)) {
                Text("Catch it", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---- Update banner ------------------------------------------------------------

@Composable
fun UpdateBanner() {
    val state by Updater.state.collectAsState()
    val dismissed by Updater.dismissedTag.collectAsState()
    val context = LocalContext.current
    val s = state
    val show = (s is UpdateState.Available && s.release.tag != dismissed) || s is UpdateState.Downloading
    if (!show) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                val label = when (s) {
                    is UpdateState.Downloading -> "Downloading ${s.release.tag}…"
                    is UpdateState.Available -> "Update available — ${s.release.tag}"
                    else -> ""
                }
                Text(label, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                if (s is UpdateState.Available) {
                    TextButton(onClick = { Updater.later(s.release.tag) }) { Text("Later") }
                    Button(onClick = { Updater.downloadAndInstall(context, s.release) }) { Text("Update") }
                }
            }
            if (s is UpdateState.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tappable card container used on the home dashboard. */
@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(20.dp)
    Box(m) { content() }
}
