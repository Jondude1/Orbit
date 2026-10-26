package com.jondude1.orbit.builder

import com.jondude1.orbit.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AiProvider(val label: String) {
    CLAUDE("Claude"),
    LOCAL("Local AI");

    companion object {
        fun from(s: String?): AiProvider = entries.firstOrNull { it.name == s } ?: CLAUDE
    }
}

/** Keys and endpoints the Build feature needs. Kept on this device only. */
data class BuildConfigState(
    val provider: AiProvider = AiProvider.CLAUDE,
    val claudeKey: String = "",
    val claudeModel: String = DEFAULT_CLAUDE_MODEL,
    /** Base URL of an OpenAI-compatible server, e.g. http://192.168.1.50:11434 (Ollama). */
    val localUrl: String = "",
    val localModel: String = "",
    val githubToken: String = "",
) {
    val aiReady: Boolean
        get() = when (provider) {
            AiProvider.CLAUDE -> claudeKey.isNotBlank()
            AiProvider.LOCAL -> localUrl.isNotBlank() && localModel.isNotBlank()
        }
    val githubReady: Boolean get() = githubToken.isNotBlank()

    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5"
    }
}

object BuildSetup {
    private val _state = MutableStateFlow(BuildConfigState())
    val state: StateFlow<BuildConfigState> = _state
    val current: BuildConfigState get() = _state.value

    /** Call after Settings.init. */
    fun init() {
        _state.value = BuildConfigState(
            provider = AiProvider.from(Settings.getString("build.provider")),
            claudeKey = Settings.getString("build.claudeKey"),
            claudeModel = Settings.getString("build.claudeModel", BuildConfigState.DEFAULT_CLAUDE_MODEL)
                .ifBlank { BuildConfigState.DEFAULT_CLAUDE_MODEL },
            localUrl = Settings.getString("build.localUrl"),
            localModel = Settings.getString("build.localModel"),
            githubToken = Settings.getString("build.githubToken"),
        )
    }

    fun update(block: (BuildConfigState) -> BuildConfigState) {
        val s = block(_state.value)
        _state.value = s
        Settings.putString("build.provider", s.provider.name)
        Settings.putString("build.claudeKey", s.claudeKey.trim())
        Settings.putString("build.claudeModel", s.claudeModel.trim())
        Settings.putString("build.localUrl", s.localUrl.trim())
        Settings.putString("build.localModel", s.localModel.trim())
        Settings.putString("build.githubToken", s.githubToken.trim())
    }
}
