package com.jondude1.orbit.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.jondude1.orbit.builder.AiProvider
import com.jondude1.orbit.builder.BuildSetup
import com.jondude1.orbit.builder.RepoMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jondude1.orbit.BuildConfig
import com.jondude1.orbit.data.Orbit
import com.jondude1.orbit.data.Settings
import com.jondude1.orbit.data.ThoughtStore
import com.jondude1.orbit.notify.ReviewScheduler
import com.jondude1.orbit.update.UpdateState
import com.jondude1.orbit.update.Updater
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val s by Settings.state.collectAsState()
    val update by Updater.state.collectAsState()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(context, "Notifications are off — Orbit can't nudge you.", Toast.LENGTH_LONG).show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(ThoughtStore.exportJson().toByteArray()) }
            }.onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                ThoughtStore.importJson(json)
            }.onSuccess { n -> Toast.makeText(context, "Brought in $n thoughts", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "That file didn't look like an Orbit backup", Toast.LENGTH_LONG).show() }
        }
    }

    fun reschedule() = ReviewScheduler.schedule(context)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            Panel {
                Column {
                    SectionLabel("When ideas come back")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Review time", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Thoughts only resurface at this time, on the days below. Pick when you usually have a little headspace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                Settings.update { it.copy(reviewHour = h, reviewMinute = m) }
                                reschedule()
                            }, s.reviewHour, s.reviewMinute, false).show()
                        }) { Text(Orbit.slotTimeText(s)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Review days", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, label ->
                            val day = i + 1
                            FilterChip(
                                selected = day in s.reviewDays,
                                onClick = {
                                    Settings.update {
                                        val d = if (day in it.reviewDays) it.reviewDays - day else it.reviewDays + day
                                        it.copy(reviewDays = d.ifEmpty { setOf(day) })
                                    }
                                    reschedule()
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Thoughts per review: ${s.perReview}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Small batches are easier to say yes to. You can always keep going.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = s.perReview.toFloat(),
                        onValueChange = { v -> Settings.update { it.copy(perReview = v.roundToInt()) } },
                        valueRange = 3f..12f,
                        steps = 8,
                    )
                    Text("Gentle limit on things in motion: ${s.focusLimit}", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = s.focusLimit.toFloat(),
                        onValueChange = { v -> Settings.update { it.copy(focusLimit = v.roundToInt()) } },
                        valueRange = 1f..8f,
                        steps = 6,
                    )
                }
            }

            Panel {
                Column {
                    SectionLabel("Nudges")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Notify me at review time", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Only when something has actually come back around.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = s.notifications, onCheckedChange = { on ->
                            Settings.update { it.copy(notifications = on) }
                            if (on && Build.VERSION.SDK_INT >= 33 && !ReviewScheduler.canNotify(context)) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        })
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !ReviewScheduler.canNotify(context)) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ReviewScheduler.notifyIfDue(context, force = true)
                        }
                    }) { Text("Send a test nudge") }
                }
            }

            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Catch thoughts faster")
                    Tip("Quick Settings tile", "Swipe down twice, tap the pencil, and drag \"Catch a thought\" into your tiles. One tap from anywhere.")
                    Tip("Long-press the Orbit icon", "Jump straight to New thought, Voice thought or Review.")
                    Tip("Share to Orbit", "From any app (browser, YouTube, Reddit), tap Share → Orbit to save the link or text.")
                    Tip("Keyboard cover", "Ctrl + Enter saves what you've typed.")
                }
            }

            BuildSettingsPanel()

            Panel {
                Column {
                    SectionLabel("Backup")
                    Text(
                        "Your thoughts live only on this tablet. Save a backup file now and then (Drive, Downloads, anywhere).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { exportLauncher.launch("orbit-backup-${LocalDate.now()}.json") }) { Text("Save backup") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                            Text("Restore from file")
                        }
                    }
                }
            }

            Panel {
                Column {
                    SectionLabel("Updates")
                    Text("Orbit ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    val u = update
                    Text(
                        when (u) {
                            UpdateState.Idle -> "Checks for new versions automatically."
                            UpdateState.Checking -> "Checking…"
                            UpdateState.UpToDate -> "You're on the latest version."
                            is UpdateState.Available -> "${u.release.tag} is ready to install."
                            is UpdateState.Downloading -> "Downloading ${(u.progress * 100).roundToInt()}%…"
                            is UpdateState.Error -> "Couldn't check: ${u.message}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (u is UpdateState.Available) {
                        Button(onClick = { Updater.downloadAndInstall(context, u.release) }) { Text("Install ${u.release.tag}") }
                    } else {
                        OutlinedButton(onClick = { Updater.check() }, enabled = u !is UpdateState.Checking && u !is UpdateState.Downloading) {
                            Text("Check for updates")
                        }
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(24.dp))
        }
    }
}

@Composable
private fun Tip(title: String, body: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildSettingsPanel() {
    val cfg by BuildSetup.state.collectAsState()
    val scope = rememberCoroutineScope()
    var ghStatus by remember { mutableStateOf<String?>(null) }

    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Build")
            Text(
                "Lets an idea become a spec and a GitHub repo with a self-updating starter app. Keys stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Who writes the spec", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p ->
                    FilterChip(
                        selected = cfg.provider == p,
                        onClick = { BuildSetup.update { it.copy(provider = p) } },
                        label = { Text(p.label) },
                    )
                }
            }
            when (cfg.provider) {
                AiProvider.CLAUDE -> {
                    SecretField("Claude API key", cfg.claudeKey) { v -> BuildSetup.update { it.copy(claudeKey = v) } }
                    OutlinedTextField(
                        value = cfg.claudeModel,
                        onValueChange = { v -> BuildSetup.update { it.copy(claudeModel = v) } },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Get a key at console.anthropic.com → API keys.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AiProvider.LOCAL -> {
                    OutlinedTextField(
                        value = cfg.localUrl,
                        onValueChange = { v -> BuildSetup.update { it.copy(localUrl = v) } },
                        label = { Text("Server address") },
                        placeholder = { Text("http://192.168.1.50:11434") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cfg.localModel,
                        onValueChange = { v -> BuildSetup.update { it.copy(localModel = v) } },
                        label = { Text("Model name") },
                        placeholder = { Text("e.g. qwen2.5:14b") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Any OpenAI-compatible server works: Ollama, LM Studio, llama.cpp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("GitHub", style = MaterialTheme.typography.titleMedium)
            SecretField("GitHub token", cfg.githubToken) { v ->
                ghStatus = null
                BuildSetup.update { it.copy(githubToken = v) }
            }
            Text(
                "github.com → Settings → Developer settings → Personal access tokens → Tokens (classic). Tick the \"repo\" and \"workflow\" scopes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = cfg.githubReady,
                    onClick = {
                        ghStatus = "Checking…"
                        scope.launch {
                            ghStatus = try {
                                "Connected as " + withContext(Dispatchers.IO) { RepoMaker.whoAmI() }
                            } catch (e: Exception) {
                                "Didn't work: ${e.message}"
                            }
                        }
                    },
                ) { Text("Check token") }
                Spacer(Modifier.width(12.dp))
                ghStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
