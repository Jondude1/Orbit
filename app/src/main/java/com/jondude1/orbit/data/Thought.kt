package com.jondude1.orbit.data

import org.json.JSONArray
import org.json.JSONObject

enum class Kind(val label: String) {
    APP("App idea"),
    PROJECT("Project"),
    THOUGHT("Thought"),
    LEARN("Learn");

    companion object {
        fun from(s: String?): Kind = entries.firstOrNull { it.name == s } ?: THOUGHT
    }
}

enum class Status(val label: String) {
    INBOX("New"),
    ORBITING("Orbiting"),
    ACTIVE("In motion"),
    DONE("Done"),
    RELEASED("Let go");

    companion object {
        fun from(s: String?): Status = entries.firstOrNull { it.name == s } ?: INBOX
    }
}

/**
 * One captured thought. Everything about when it comes back lives here:
 * [surfaceAt] is the next time it shows up in review, [intervalDays] is how
 * far apart its returns are currently spaced.
 */
data class Thought(
    val id: Long,
    val text: String,
    val kind: Kind = Kind.THOUGHT,
    val spark: Int = 1,
    val status: Status = Status.INBOX,
    val nextStep: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val surfaceAt: Long,
    val intervalDays: Int = 1,
    val surfacedCount: Int = 0,
    val lastSurfacedAt: Long = 0L,
) {
    val title: String
        get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    val body: String
        get() {
            val lines = text.lines()
            val first = lines.indexOfFirst { it.isNotBlank() }
            return if (first < 0) "" else lines.drop(first + 1).joinToString("\n").trim()
        }

    val isOpen: Boolean
        get() = status == Status.INBOX || status == Status.ORBITING || status == Status.ACTIVE

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("kind", kind.name)
        put("spark", spark)
        put("status", status.name)
        put("nextStep", nextStep)
        put("notes", notes)
        put("tags", JSONArray(tags))
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("surfaceAt", surfaceAt)
        put("intervalDays", intervalDays)
        put("surfacedCount", surfacedCount)
        put("lastSurfacedAt", lastSurfacedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Thought {
            val tagsArr = o.optJSONArray("tags")
            val tags = buildList {
                if (tagsArr != null) for (i in 0 until tagsArr.length()) add(tagsArr.optString(i))
            }.filter { it.isNotBlank() }
            val now = System.currentTimeMillis()
            return Thought(
                id = o.getLong("id"),
                text = o.optString("text"),
                kind = Kind.from(o.optString("kind")),
                spark = o.optInt("spark", 1).coerceIn(1, 3),
                status = Status.from(o.optString("status")),
                nextStep = o.optString("nextStep"),
                notes = o.optString("notes"),
                tags = tags,
                createdAt = o.optLong("createdAt", now),
                updatedAt = o.optLong("updatedAt", now),
                surfaceAt = o.optLong("surfaceAt", now),
                intervalDays = o.optInt("intervalDays", 1),
                surfacedCount = o.optInt("surfacedCount", 0),
                lastSurfacedAt = o.optLong("lastSurfacedAt", 0L),
            )
        }
    }
}
