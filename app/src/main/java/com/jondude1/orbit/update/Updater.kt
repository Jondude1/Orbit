package com.jondude1.orbit.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import com.jondude1.orbit.BuildConfig
import com.jondude1.orbit.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val versionCode: Int, val tag: String, val apkUrl: String, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** Checks this app's public GitHub repo for a newer release and installs it. */
object Updater {
    private const val REPO = "Jondude1/Orbit"
    private const val CHECK_EVERY = 30 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Banner is hidden for this release when the person taps "Later". */
    private val _dismissedTag = MutableStateFlow<String?>(null)
    val dismissedTag: StateFlow<String?> = _dismissedTag

    fun later(tag: String) { _dismissedTag.value = tag }

    fun checkQuietly() {
        val last = Settings.getLong("lastUpdateCheck")
        if (System.currentTimeMillis() - last < CHECK_EVERY) return
        check(quiet = true)
    }

    fun check(quiet: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        scope.launch {
            if (!quiet) _state.value = UpdateState.Checking
            try {
                val rel = withContext(Dispatchers.IO) { latest() }
                Settings.putLong("lastUpdateCheck", System.currentTimeMillis())
                _state.value = when {
                    rel != null && rel.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(rel)
                    quiet -> UpdateState.Idle
                    else -> UpdateState.UpToDate
                }
            } catch (e: Exception) {
                _state.value = if (quiet) UpdateState.Idle else UpdateState.Error(e.message ?: "Couldn't check for updates")
            }
        }
    }

    private fun latest(): Release? {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name")
            val code = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(code, tag, a.optString("browser_download_url"), json.optString("body"))
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    fun downloadAndInstall(context: Context, release: Release) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            return
        }
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                val file = withContext(Dispatchers.IO) {
                    download(app, release) { p -> _state.value = UpdateState.Downloading(release, p) }
                }
                _state.value = UpdateState.Available(release)
                install(app, file)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    private fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Orbit-${release.tag}.apk")

        // GitHub redirects release downloads to a CDN on another host; follow by hand.
        var url = URL(release.apkUrl)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: error("Redirect without location")
                conn.disconnect()
                url = URL(url, loc)
                if (++hops > 6) error("Too many redirects")
                continue
            }
            if (code != 200) error("Download failed (HTTP $code)")
            break
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        return out
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
