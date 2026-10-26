package com.jondude1.orbit.builder

import com.jondude1.orbit.data.Kind
import com.jondude1.orbit.data.Thought
import org.json.JSONArray
import org.json.JSONObject

/** Turns a captured idea into a one-page build spec using Claude or a local model. */
object SpecWriter {

    private val system = """
        You turn a rough idea someone jotted down into a short, practical one-page build spec.
        The builder is a hands-on solo developer who builds with AI coding help (Claude Code).
        Android apps are native Kotlin + Jetpack Compose, sideloaded (not Play Store), and update
        themselves from GitHub Releases. Keep scope to something a first version can ship in a weekend.

        Reply with markdown only, no preamble, in exactly this shape:

        # <Short app or project name, 1-3 words>

        > <One sentence: what it is and who it's for>

        ## Problem
        ## MVP features
        (5-8 bullets, each one concrete and testable)
        ## Screens
        (one bullet per screen: name — what's on it)
        ## Data
        (what gets stored, where; prefer local storage unless sync is essential)
        ## Later, not now
        (3-5 bullets)
        ## First three build steps
        (numbered, tiny)
        ## Open questions
        (only real ones; 0-3 bullets)

        Stay under 450 words. Use the person's own words for the idea where you can.
    """.trimIndent()

    private fun userPrompt(t: Thought): String = buildString {
        appendLine("Kind: ${t.kind.label}")
        appendLine("Idea title: ${t.title}")
        if (t.body.isNotBlank()) appendLine("More from the idea:\n${t.body}")
        if (t.notes.isNotBlank()) appendLine("Notes:\n${t.notes}")
        if (t.nextStep.isNotBlank()) appendLine("Their next step: ${t.nextStep}")
        if (t.tags.isNotEmpty()) appendLine("Tags: ${t.tags.joinToString(", ")}")
        if (t.kind == Kind.APP) appendLine("Target: Android phone and Samsung Galaxy Tab (large screen) friendly.")
    }

    /** Blocking. Call from Dispatchers.IO. */
    fun write(t: Thought, cfg: BuildConfigState = BuildSetup.current): String {
        val text = when (cfg.provider) {
            AiProvider.CLAUDE -> claude(cfg, userPrompt(t))
            AiProvider.LOCAL -> local(cfg, userPrompt(t))
        }
        return clean(text)
    }

    private fun claude(cfg: BuildConfigState, prompt: String): String {
        require(cfg.claudeKey.isNotBlank()) { "Add your Claude API key in Settings → Build" }
        val body = JSONObject()
            .put("model", cfg.claudeModel.ifBlank { BuildConfigState.DEFAULT_CLAUDE_MODEL })
            .put("max_tokens", 2000)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val res = Http.json(
            "POST", "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to cfg.claudeKey, "anthropic-version" to "2023-06-01"),
            body = body,
            readTimeoutMs = 120_000,
        )
        val content = res.optJSONArray("content") ?: error("Empty reply from Claude")
        return buildString {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }

    /** Any OpenAI-compatible server: Ollama, LM Studio, llama.cpp, vLLM… */
    private fun local(cfg: BuildConfigState, prompt: String): String {
        require(cfg.localUrl.isNotBlank() && cfg.localModel.isNotBlank()) { "Add your local AI address and model in Settings → Build" }
        val base = cfg.localUrl.trim().trimEnd('/').removeSuffix("/v1")
        val body = JSONObject()
            .put("model", cfg.localModel)
            .put("stream", false)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt)))
        val res = Http.json("POST", "$base/v1/chat/completions", body = body, readTimeoutMs = 300_000)
        return res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: error("Empty reply from local AI")
    }

    /** Drop code fences and any "thinking" a local model leaks before the spec. */
    private fun clean(raw: String): String {
        var s = raw.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        s = s.removePrefix("```markdown").removePrefix("```md").removePrefix("```").removeSuffix("```").trim()
        val h1 = s.indexOf("# ")
        if (h1 > 0 && s.substring(0, h1).isNotBlank() && !s.substring(0, h1).contains("\n#")) s = s.substring(h1)
        if (s.isBlank()) error("The AI sent back an empty spec")
        return s
    }

    /** The "# Name" line of a spec, if it has one. */
    fun nameFrom(spec: String): String? =
        spec.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()?.takeIf { it.isNotBlank() }

    /** The "> one-liner" of a spec, if it has one. */
    fun summaryFrom(spec: String): String? =
        spec.lineSequence().firstOrNull { it.startsWith("> ") }?.removePrefix("> ")?.trim()?.takeIf { it.isNotBlank() }
}
