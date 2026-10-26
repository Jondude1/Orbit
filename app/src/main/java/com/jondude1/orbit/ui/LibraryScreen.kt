package com.jondude1.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jondude1.orbit.data.Kind
import com.jondude1.orbit.data.Status
import com.jondude1.orbit.data.Thought
import com.jondude1.orbit.data.ThoughtStore

private enum class Filter(val label: String, val test: (Thought) -> Boolean) {
    OPEN("Open", { it.isOpen }),
    NEW("New", { it.status == Status.INBOX }),
    ORBITING("Orbiting", { it.status == Status.ORBITING }),
    ACTIVE("In motion", { it.status == Status.ACTIVE }),
    DONE("Done", { it.status == Status.DONE }),
    RELEASED("Let go", { it.status == Status.RELEASED }),
    ALL("Everything", { true }),
}

private enum class Sort(val label: String) { RECENT("Newest"), SPARK("Most spark"), SOON("Coming back soon") }

@Composable
fun LibraryScreen(wide: Boolean, selectedId: Long?, onSelect: (Long?) -> Unit) {
    val all by ThoughtStore.thoughts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.OPEN) }
    var kind by rememberSaveable { mutableStateOf<Kind?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.RECENT) }

    val shown = remember(all, query, filter, kind, sort) {
        val q = query.trim().lowercase()
        all.asSequence()
            .filter(filter.test)
            .filter { kind == null || it.kind == kind }
            .filter {
                q.isEmpty() || it.text.lowercase().contains(q) || it.notes.lowercase().contains(q) ||
                    it.nextStep.lowercase().contains(q) || it.tags.any { tag -> tag.lowercase().contains(q) }
            }
            .let { seq ->
                when (sort) {
                    Sort.RECENT -> seq.sortedByDescending { it.createdAt }
                    Sort.SPARK -> seq.sortedWith(compareByDescending<Thought> { it.spark }.thenByDescending { it.createdAt })
                    Sort.SOON -> seq.sortedBy { if (it.isOpen && it.status != Status.ACTIVE) it.surfaceAt else Long.MAX_VALUE }
                }
            }
            .toList()
    }

    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp)) {
                Text("All thoughts", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Search ideas, notes, tags") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries) { f ->
                    val count = all.count(f.test)
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} $count") })
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Kind.entries) { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = if (kind == k) null else k },
                        label = { Text(k.label) },
                    )
                }
                item { Spacer(Modifier.height(1.dp)) }
                items(Sort.entries) { s ->
                    FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (shown.isEmpty()) {
                    item { EmptyNote("Nothing here", "Try another filter, or catch something new on the home screen.") }
                }
                items(shown, key = { it.id }) { t ->
                    ThoughtRow(t, selected = wide && t.id == selectedId) { onSelect(t.id) }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.weight(0.45f).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(0.55f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                if (selectedId != null && all.any { it.id == selectedId }) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        ThoughtEditor(selectedId, Modifier.fillMaxSize(), onClose = { onSelect(null) })
                    }
                } else {
                    EmptyNote(
                        "Pick a thought",
                        "Tap anything on the left to flesh it out, give it a next step, or decide when it comes back.",
                        Modifier.padding(top = 120.dp),
                    )
                }
            }
        }
    } else {
        list(Modifier.fillMaxSize())
        if (selectedId != null) ThoughtEditorDialog(selectedId) { onSelect(null) }
    }
}
