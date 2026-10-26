package com.jondude1.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jondude1.orbit.data.Kind
import com.jondude1.orbit.data.Orbit
import com.jondude1.orbit.data.Status
import com.jondude1.orbit.data.ThoughtStore

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThoughtEditor(id: Long, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val all by ThoughtStore.thoughts.collectAsState()
    val t = all.firstOrNull { it.id == id }
    if (t == null) {
        EmptyNote("Gone", "This thought was deleted.", modifier)
        return
    }

    // Local copies keep the cursor steady while edits stream into the store.
    var text by remember(id) { mutableStateOf(t.text) }
    var nextStep by remember(id) { mutableStateOf(t.nextStep) }
    var notes by remember(id) { mutableStateOf(t.notes) }
    var tags by remember(id) { mutableStateOf(t.tags.joinToString(", ")) }
    var confirmDelete by remember(id) { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Caught ${Orbit.agoText(t.createdAt)}" +
                    if (t.surfacedCount > 0) " · came back ${t.surfacedCount}×" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v -> text = v; ThoughtStore.update(id) { it.copy(text = v) } },
            label = { Text("The thought") },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("What is it")
        KindChips(t.kind) { k -> ThoughtStore.update(id) { it.copy(kind = k) } }

        Spacer(Modifier.height(16.dp))
        SectionLabel("How much does it pull at you")
        SparkPicker(t.spark, { s -> ThoughtStore.update(id) { it.copy(spark = s) } })

        Spacer(Modifier.height(16.dp))
        SectionLabel("Where it's at")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Status.entries.forEach { s ->
                FilterChip(
                    selected = t.status == s,
                    onClick = { ThoughtStore.setStatus(id, s) },
                    label = { Text(s.label) },
                )
            }
        }
        if (t.status == Status.INBOX || t.status == Status.ORBITING) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Comes back ${Orbit.whenText(t.surfaceAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Bring it back")
        WhenChips { w -> ThoughtStore.bringBack(id, w) }

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = nextStep,
            onValueChange = { v -> nextStep = v; ThoughtStore.update(id) { it.copy(nextStep = v) } },
            label = { Text("Tiniest next step") },
            placeholder = { Text("e.g. sketch the main screen on paper") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { v -> notes = v; ThoughtStore.update(id) { it.copy(notes = v) } },
            label = { Text("Notes, links, details") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tags,
            onValueChange = { v ->
                tags = v
                ThoughtStore.update(id) { it.copy(tags = v.split(',').map(String::trim).filter(String::isNotEmpty)) }
            },
            label = { Text("Tags (comma separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (t.kind == Kind.APP || t.kind == Kind.PROJECT || t.spec.isNotBlank() || t.repoUrl.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            BuildPanel(t, LocalOpenSettings.current)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this thought?") },
            text = { Text("\"Let go\" keeps it in the archive. Delete removes it for good.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    ThoughtStore.delete(id)
                    onClose?.invoke()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    ThoughtStore.letGo(id)
                }) { Text("Let go instead") }
            },
        )
    }
}

@Composable
fun ThoughtEditorDialog(id: Long, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f),
        ) {
            ThoughtEditor(id, Modifier.fillMaxSize(), onClose = onDismiss)
        }
    }
}
