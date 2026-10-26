package com.jondude1.orbit.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jondude1.orbit.builder.AppNames
import com.jondude1.orbit.builder.BuildSetup
import com.jondude1.orbit.builder.RepoMaker
import com.jondude1.orbit.builder.SpecWriter
import com.jondude1.orbit.builder.Starter
import com.jondude1.orbit.data.Kind
import com.jondude1.orbit.data.Status
import com.jondude1.orbit.data.Thought
import com.jondude1.orbit.data.ThoughtStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Jumps to Settings (closing any open editor). Provided by the app shell. */
val LocalOpenSettings = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Prompt to paste into Claude Code once the repo exists. */
fun buildPrompt(repoUrl: String): String =
    "Clone $repoUrl and build the app described in SPEC.md, following CLAUDE.md. " +
        "Start with the MVP features and the first three build steps. Push to main and keep going until the " +
        "GitHub Actions build passes and the release has an APK."

/**
 * Idea → spec → GitHub repo. Lives in the thought editor.
 * [onNeedSetup] takes the person to Settings when keys are missing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildPanel(t: Thought, onNeedSetup: (() -> Unit)?) {
    val cfg by BuildSetup.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current

    var spec by remember(t.id) { mutableStateOf(t.spec) }
    var writing by remember(t.id) { mutableStateOf(false) }
    var creating by remember(t.id) { mutableStateOf(false) }
    var error by remember(t.id) { mutableStateOf<String?>(null) }
    var confirmRewrite by remember(t.id) { mutableStateOf(false) }
    var repoName by remember(t.id) { mutableStateOf("") }
    var starter by remember(t.id) { mutableStateOf(if (t.kind == Kind.APP) Starter.ANDROID else Starter.SPEC_ONLY) }

    fun writeSpec() {
        if (writing) return
        error = null
        writing = true
        scope.launch {
            try {
                val latest = ThoughtStore.get(t.id) ?: t
                val result = withContext(Dispatchers.IO) { SpecWriter.write(latest) }
                spec = result
                repoName = ""
                ThoughtStore.update(t.id) { it.copy(spec = result) }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't write the spec"
            } finally {
                writing = false
            }
        }
    }

    fun createRepo(names: AppNames) {
        if (creating) return
        error = null
        creating = true
        scope.launch {
            try {
                val url = withContext(Dispatchers.IO) { RepoMaker.create(context, names, spec, starter) }
                ThoughtStore.update(t.id) {
                    it.copy(
                        repoUrl = url,
                        status = Status.ACTIVE,
                        nextStep = it.nextStep.ifBlank { "Open Claude Code on ${names.repo} and paste the build prompt" },
                        notes = (it.notes.trimEnd() + "\n\nRepo: $url").trim(),
                    )
                }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't create the repo"
            } finally {
                creating = false
            }
        }
    }

    Panel {
        Column {
            SectionLabel("Build it")

            if (spec.isBlank()) {
                Text(
                    "Turn this into a one-page spec, then a GitHub repo with a self-updating starter app, ready for Claude Code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (writing) {
                    Working("Writing the spec with ${cfg.provider.label}…")
                } else if (!cfg.aiReady) {
                    SetupHint("Add a Claude API key or your local AI in Settings → Build first.", onNeedSetup)
                } else {
                    Button(onClick = { writeSpec() }) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Write a spec")
                    }
                }
            } else {
                OutlinedTextField(
                    value = spec,
                    onValueChange = { v -> spec = v; ThoughtStore.update(t.id) { it.copy(spec = v) } },
                    label = { Text("Spec (edit freely)") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 6,
                    maxLines = 22,
                    enabled = !writing,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (writing) {
                        Working("Rewriting…")
                    } else {
                        TextButton(onClick = { if (cfg.aiReady) confirmRewrite = true else onNeedSetup?.invoke() }) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rewrite")
                        }
                        TextButton(onClick = {
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, SpecWriter.nameFrom(spec) ?: t.title)
                                    .putExtra(Intent.EXTRA_TEXT, spec),
                                "Share spec",
                            ))
                        }) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (t.repoUrl.isNotBlank()) {
                    RepoDone(t.repoUrl, onOpen = { uri.openUri(it) }, onCopy = { clipboard.setText(AnnotatedString(it)) })
                } else {
                    val suggested = SpecWriter.nameFrom(spec) ?: t.title.take(40)
                    val nameText = repoName.ifBlank { suggested }
                    val names = AppNames.from(nameText)
                    Text("Make the repo", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { repoName = it },
                        label = { Text("App name") },
                        supportingText = { Text("github.com/…/${names.repo} · public, so the app can update itself") },
                        singleLine = true,
                        enabled = !creating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Starter.entries.forEach { s ->
                            FilterChip(selected = starter == s, onClick = { starter = s }, label = { Text(s.label) }, enabled = !creating)
                        }
                    }
                    Text(starter.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    when {
                        creating -> Working("Creating ${names.repo} on GitHub…")
                        !cfg.githubReady -> SetupHint("Add a GitHub token in Settings → Build to create repos.", onNeedSetup)
                        else -> Button(onClick = { createRepo(names) }, enabled = nameText.isNotBlank()) {
                            Icon(Icons.Rounded.RocketLaunch, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create GitHub repo")
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmRewrite) {
        AlertDialog(
            onDismissRequest = { confirmRewrite = false },
            title = { Text("Write a fresh spec?") },
            text = { Text("This replaces the current spec, including your edits. It uses the latest thought, notes and tags.") },
            confirmButton = { TextButton(onClick = { confirmRewrite = false; writeSpec() }) { Text("Rewrite") } },
            dismissButton = { TextButton(onClick = { confirmRewrite = false }) { Text("Keep it") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepoDone(url: String, onOpen: (String) -> Unit, onCopy: (String) -> Unit) {
    var copied by remember(url) { mutableStateOf(false) }
    Text("Repo ready", style = MaterialTheme.typography.titleSmall)
    Text(url.removePrefix("https://"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(4.dp))
    Text(
        "Next: open Claude Code, paste the build prompt, and the first push publishes an APK you install once. After that it updates itself.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onCopy(buildPrompt(url)); copied = true }) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(if (copied) "Copied" else "Copy build prompt")
        }
        OutlinedButton(onClick = { onOpen(url) }) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open repo")
        }
        OutlinedButton(onClick = { onOpen("$url/releases") }) { Text("Releases") }
    }
}

@Composable
private fun Working(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SetupHint(text: String, onNeedSetup: (() -> Unit)?) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (onNeedSetup != null) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onNeedSetup) { Text("Open Build settings") }
    }
}
