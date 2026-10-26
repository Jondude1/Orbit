package com.jondude1.orbit.builder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Starter(val label: String, val hint: String) {
    ANDROID("Android app", "Self-updating Compose app, builds on every push"),
    SPEC_ONLY("Spec only", "README, SPEC and build notes, no code yet"),
}

/** Names derived from what the person typed. */
data class AppNames(val display: String, val repo: String, val pkgSegment: String) {
    companion object {
        fun from(name: String): AppNames {
            val words = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
            val display = name.trim().ifBlank { "New App" }
            val repo = words.joinToString("") { w -> w.replaceFirstChar { it.uppercase() } }.ifBlank { "NewApp" }
            var seg = words.joinToString("").lowercase()
            if (seg.isBlank() || !seg[0].isLetter()) seg = "app$seg"
            return AppNames(display, repo.take(60), seg.take(40))
        }
    }
}

/**
 * Creates a public GitHub repo for an idea and fills it with a starter in one
 * commit. Needs a token that can create repos and write workflow files.
 */
object RepoMaker {
    private const val API = "https://api.github.com"
    private const val TEMPLATE_ROOT = "template/android"

    private fun headers(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
    )

    /** Blocking. The GitHub login the token belongs to, or a readable error. */
    fun whoAmI(cfg: BuildConfigState = BuildSetup.current): String {
        require(cfg.githubToken.isNotBlank()) { "No token yet" }
        val (code, text) = Http.send("GET", "$API/user", headers(cfg.githubToken))
        if (code != 200) throw HttpError(code, if (code == 401) "Token rejected" else "HTTP $code")
        return JSONObject(text).getString("login")
    }

    /** Blocking. Returns the repo's web URL. */
    fun create(
        context: Context,
        names: AppNames,
        spec: String,
        starter: Starter,
        cfg: BuildConfigState = BuildSetup.current,
    ): String {
        require(cfg.githubToken.isNotBlank()) { "Add a GitHub token in Settings → Build" }
        val h = headers(cfg.githubToken)
        val owner = Http.json("GET", "$API/user", h).getString("login")

        val summary = SpecWriter.summaryFrom(spec) ?: "Started from an Orbit idea."
        val (code, text) = Http.send(
            "POST", "$API/user/repos", h,
            JSONObject()
                .put("name", names.repo)
                .put("description", summary.take(300))
                .put("private", false) // the in-app updater reads public releases
                .put("auto_init", true),
        )
        if (code == 422) throw HttpError(code, "You already have a repo called ${names.repo}. Change the name and try again.")
        if (code !in 200..299) throw HttpError(code, "Couldn't create the repo (HTTP $code). ${tokenHint(code)}")
        val repo = JSONObject(text)
        val fullName = repo.getString("full_name")
        val branch = repo.optString("default_branch", "main").ifBlank { "main" }

        val vars = mapOf(
            "__APP_NAME__" to names.display,
            "__APP_FILE__" to names.repo,
            "__REPO__" to fullName,
            "__OWNER__" to owner,
            "__PKG__" to "com.${ownerSegment(owner)}.${names.pkgSegment}",
            "__PKG_PATH__" to "com/${ownerSegment(owner)}/${names.pkgSegment}",
            "__SLUG__" to names.pkgSegment,
            "__APP_NAME_KT__" to kotlinString(names.display),
            "__APP_NAME_XML__" to xmlString(names.display),
            "__SUMMARY_KT__" to kotlinString(summary),
        )
        val files = linkedMapOf<String, String>()
        files["SPEC.md"] = spec.trimEnd() + "\n"
        files["README.md"] = readme(names, summary, starter, fullName)
        files["CLAUDE.md"] = claudeMd(names, starter, fullName)
        if (starter == Starter.ANDROID) {
            // Directory names can't start with "_" in assets, so the package folder is spelled PKGPATH.
            val pkgPath = vars.getValue("__PKG_PATH__")
            templateFiles(context).forEach { (path, body) ->
                files[path.replace("PKGPATH", pkgPath)] = fill(body, vars)
            }
        }
        commit(h, fullName, branch, files, "Start ${names.display} from Orbit")
        return repo.getString("html_url")
    }

    // ---- Git data API: everything lands in one commit -----------------------

    private fun commit(h: Map<String, String>, repo: String, branch: String, files: Map<String, String>, message: String) {
        // A brand new repo's first commit can take a moment to appear.
        var head: String? = null
        for (attempt in 1..8) {
            val (code, text) = Http.send("GET", "$API/repos/$repo/git/ref/heads/$branch", h)
            if (code == 200) {
                head = JSONObject(text).getJSONObject("object").getString("sha")
                break
            }
            Thread.sleep(750L * attempt)
        }
        val parent = head ?: error("GitHub didn't finish setting up the repo. Try again in a minute.")
        val baseTree = Http.json("GET", "$API/repos/$repo/git/commits/$parent", h)
            .getJSONObject("tree").getString("sha")

        val entries = JSONArray()
        files.forEach { (path, content) ->
            entries.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("content", content))
        }
        val tree = Http.json("POST", "$API/repos/$repo/git/trees", h, JSONObject().put("base_tree", baseTree).put("tree", entries))
            .getString("sha")
        val newCommit = Http.json(
            "POST", "$API/repos/$repo/git/commits", h,
            JSONObject().put("message", message).put("tree", tree).put("parents", JSONArray().put(parent)),
        ).getString("sha")
        val (code, _) = Http.send("PATCH", "$API/repos/$repo/git/refs/heads/$branch", h, JSONObject().put("sha", newCommit))
        if (code !in 200..299) {
            throw HttpError(code, "Repo created, but the starter files couldn't be pushed (HTTP $code). ${tokenHint(code)}")
        }
    }

    private fun tokenHint(code: Int) = when (code) {
        401 -> "The GitHub token was rejected; make a new one."
        403, 404 -> "Your GitHub token needs the \"repo\" and \"workflow\" scopes."
        else -> ""
    }

    // ---- Template ------------------------------------------------------------

    /** Every file under assets/template/android, keyed by its path in the new repo. */
    private fun templateFiles(context: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        fun walk(dir: String) {
            val children = context.assets.list(dir).orEmpty()
            if (children.isEmpty()) {
                val rel = dir.removePrefix("$TEMPLATE_ROOT/")
                    .split('/')
                    .joinToString("/") { if (it.startsWith("dot.")) "." + it.removePrefix("dot.") else it }
                    .removeSuffix(".tmpl")
                out[rel] = context.assets.open(dir).bufferedReader().use { it.readText() }
            } else {
                children.forEach { walk("$dir/$it") }
            }
        }
        walk(TEMPLATE_ROOT)
        return out
    }

    private fun fill(s: String, vars: Map<String, String>): String =
        vars.entries.fold(s) { acc, (k, v) -> acc.replace(k, v) }

    private fun kotlinString(s: String) = s
        .replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        .replace("\n", " ").replace("\r", " ")

    private fun xmlString(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "\\\"").replace("'", "\\'")

    private fun ownerSegment(owner: String): String {
        var s = owner.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (s.isBlank() || !s[0].isLetter()) s = "app$s"
        return s
    }

    private fun readme(names: AppNames, summary: String, starter: Starter, repo: String) = buildString {
        appendLine("# ${names.display}")
        appendLine()
        appendLine(summary)
        appendLine()
        appendLine("The plan is in [SPEC.md](SPEC.md). Started from an idea in Orbit.")
        if (starter == Starter.ANDROID) {
            appendLine()
            appendLine("Every push to `main` builds a signed APK and publishes it as a GitHub Release. ")
            appendLine("The app checks `https://github.com/$repo/releases/latest` and updates itself, so install it once from the Releases page and never again.")
        }
    }

    private fun claudeMd(names: AppNames, starter: Starter, repo: String) = buildString {
        appendLine("# ${names.display}: notes for Claude")
        appendLine()
        appendLine("- What to build is in SPEC.md. Start with its MVP features and \"First three build steps\"; leave \"Later, not now\" alone unless asked.")
        if (starter == Starter.ANDROID) {
            appendLine("- Native Android: Kotlin + Jetpack Compose, minSdk 26. Must work on phones and Galaxy Tab large screens.")
            appendLine("- Self-updating is already wired up. Keep it working:")
            appendLine("  - The repo stays public; `update/Updater.kt` reads `releases/latest` of $repo without a login.")
            appendLine("  - The permanent signing key is made by CI on the first build and committed to `keystore/`. Never replace or regenerate it, or installed copies can't update.")
            appendLine("  - Version numbers come from `GITHUB_RUN_NUMBER`. Don't hardcode them.")
            appendLine("  - Keep the update banner at the top of the app and the update card in Settings.")
            appendLine("- CI uses Gradle from `gradle/actions/setup-gradle` (no wrapper). Adding a wrapper is fine if you commit `gradle-wrapper.jar` too.")
            appendLine("- After pushing, watch the Actions run and fix any failure until the release has an APK.")
            appendLine("- Not for the Play Store: if it ever goes there, remove the self-updater first (Play restricts REQUEST_INSTALL_PACKAGES).")
        } else {
            appendLine("- No code yet. When starting, ask what platform it should be if SPEC.md doesn't say.")
            appendLine("- Whatever gets built must update itself: Android via GitHub Releases + in-app updater, web via auto-deploy + a \"new version, reload\" bar.")
        }
    }
}
