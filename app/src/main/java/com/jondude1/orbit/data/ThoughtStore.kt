package com.jondude1.orbit.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * All thoughts, kept in memory and saved to one JSON file in app storage.
 * Saves are debounced so typing in the editor doesn't hammer the disk.
 */
object ThoughtStore {
    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var saveJob: Job? = null

    private val _thoughts = MutableStateFlow<List<Thought>>(emptyList())
    val thoughts: StateFlow<List<Thought>> = _thoughts

    fun init(context: Context) {
        file = File(context.filesDir, "thoughts.json")
        _thoughts.value = readFile(file)
    }

    private fun readFile(f: File): List<Thought> {
        if (!f.exists()) return emptyList()
        return try {
            parse(f.readText())
        } catch (e: Exception) {
            // Keep a copy of anything we couldn't read rather than overwriting it.
            f.copyTo(File(f.parentFile, "thoughts-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
            emptyList()
        }
    }

    fun parse(json: String): List<Thought> {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("{")) JSONObject(trimmed).getJSONArray("thoughts") else JSONArray(trimmed)
        return (0 until arr.length()).map { Thought.fromJson(arr.getJSONObject(it)) }
    }

    fun exportJson(): String = JSONObject().apply {
        put("app", "Orbit")
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put("thoughts", JSONArray().apply { _thoughts.value.forEach { put(it.toJson()) } })
    }.toString(2)

    /** Merge a backup in. Newer edits win; returns how many thoughts were added or changed. */
    fun importJson(json: String): Int {
        val incoming = parse(json)
        var changed = 0
        _thoughts.update { list ->
            val byId = list.associateBy { it.id }.toMutableMap()
            for (t in incoming) {
                val existing = byId[t.id]
                if (existing == null || t.updatedAt > existing.updatedAt) {
                    byId[t.id] = t
                    changed++
                }
            }
            byId.values.sortedByDescending { it.createdAt }
        }
        scheduleSave(immediate = true)
        return changed
    }

    private fun scheduleSave(immediate: Boolean = false) {
        saveJob?.cancel()
        saveJob = scope.launch {
            if (!immediate) delay(400)
            mutex.withLock {
                val tmp = File(file.parentFile, "thoughts.json.tmp")
                tmp.writeText(JSONArray().apply { _thoughts.value.forEach { put(it.toJson()) } }.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }
        }
    }

    fun get(id: Long): Thought? = _thoughts.value.firstOrNull { it.id == id }

    // ---- Capture & edit ----------------------------------------------------

    fun capture(text: String, kind: Kind = Kind.THOUGHT, spark: Int = 1): Thought {
        val now = System.currentTimeMillis()
        val id = maxOf(now, (_thoughts.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val t = Thought(
            id = id,
            text = text.trim(),
            kind = kind,
            spark = spark.coerceIn(1, 3),
            createdAt = now,
            updatedAt = now,
            // A fresh idea comes back at the next review slot that's at least
            // a few hours away, so it gets a second look while it's still warm.
            surfaceAt = Orbit.slotAtOrAfter(now + 4 * 60 * 60 * 1000L),
            intervalDays = 1,
        )
        _thoughts.update { listOf(t) + it }
        scheduleSave()
        return t
    }

    fun update(id: Long, block: (Thought) -> Thought) {
        _thoughts.update { list ->
            list.map { if (it.id == id) block(it).copy(updatedAt = System.currentTimeMillis()) else it }
        }
        scheduleSave()
    }

    fun delete(id: Long) {
        _thoughts.update { list -> list.filterNot { it.id == id } }
        scheduleSave()
    }

    fun restore(t: Thought) {
        _thoughts.update { list -> (list.filterNot { it.id == t.id } + t).sortedByDescending { it.createdAt } }
        scheduleSave()
    }

    // ---- Review ------------------------------------------------------------

    private const val STALE_ACTIVE = 7 * Orbit.DAY

    fun isStaleActive(t: Thought, now: Long = System.currentTimeMillis()) =
        t.status == Status.ACTIVE && now - t.updatedAt > STALE_ACTIVE && now - t.lastSurfacedAt > STALE_ACTIVE

    /** Everything that has come back around and is waiting for a look. */
    fun due(now: Long = System.currentTimeMillis(), list: List<Thought> = _thoughts.value): List<Thought> =
        list.filter {
            ((it.status == Status.INBOX || it.status == Status.ORBITING) && it.surfaceAt <= now) || isStaleActive(it, now)
        }.sortedWith(compareByDescending<Thought> { it.spark }.thenBy { it.surfaceAt })

    private fun surfaced(t: Thought, now: Long) = t.copy(surfacedCount = t.surfacedCount + 1, lastSurfacedAt = now)

    /** "This still excites me": spark up, come back sooner. */
    fun sparked(id: Long) {
        val now = System.currentTimeMillis()
        update(id) {
            val interval = Orbit.nextShorter(it.intervalDays)
            surfaced(it, now).copy(
                spark = (it.spark + 1).coerceAtMost(3),
                status = if (it.status == Status.INBOX) Status.ORBITING else it.status,
                intervalDays = interval,
                surfaceAt = Orbit.slotInDays(interval, now),
            )
        }
    }

    /** "Not now": push it further out. */
    fun notNow(id: Long) {
        val now = System.currentTimeMillis()
        update(id) {
            val interval = Orbit.nextLonger(it.intervalDays)
            surfaced(it, now).copy(
                status = Status.ORBITING,
                intervalDays = interval,
                surfaceAt = Orbit.slotInDays(interval, now),
            )
        }
    }

    fun bringBack(id: Long, w: Orbit.When) {
        val now = System.currentTimeMillis()
        val (at, interval) = Orbit.at(w, now)
        update(id) {
            surfaced(it, now).copy(
                status = if (it.status == Status.ACTIVE || it.status == Status.INBOX || !it.isOpen) Status.ORBITING else it.status,
                intervalDays = interval,
                surfaceAt = at,
            )
        }
    }

    fun start(id: Long, nextStep: String? = null) {
        val now = System.currentTimeMillis()
        update(id) {
            surfaced(it, now).copy(
                status = Status.ACTIVE,
                nextStep = nextStep?.takeIf { s -> s.isNotBlank() } ?: it.nextStep,
            )
        }
    }

    /** For things in motion: "yes, still on it". */
    fun stillGoing(id: Long) {
        val now = System.currentTimeMillis()
        update(id) { surfaced(it, now) }
    }

    fun park(id: Long) = bringBack(id, Orbit.When.NEXT_WEEK)

    fun done(id: Long) {
        val now = System.currentTimeMillis()
        update(id) { surfaced(it, now).copy(status = Status.DONE) }
    }

    fun letGo(id: Long) {
        val now = System.currentTimeMillis()
        update(id) { surfaced(it, now).copy(status = Status.RELEASED) }
    }

    fun setStatus(id: Long, status: Status) {
        val now = System.currentTimeMillis()
        update(id) {
            when (status) {
                Status.ORBITING, Status.INBOX ->
                    if (it.isOpen && it.status != Status.ACTIVE) it.copy(status = status)
                    else it.copy(status = status, intervalDays = 7, surfaceAt = Orbit.slotInDays(7, now))
                else -> it.copy(status = status)
            }
        }
    }

    fun activeCount(): Int = _thoughts.value.count { it.status == Status.ACTIVE }
}
