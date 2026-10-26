package com.jondude1.orbit.builder

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpError(val code: Int, message: String) : Exception(message)

/** Tiny JSON-over-HTTP helper. Blocking: call from Dispatchers.IO. */
internal object Http {
    fun send(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        readTimeoutMs: Int = 60_000,
    ): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    /** Like [send] but throws a readable error for anything that isn't 2xx. */
    fun json(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        readTimeoutMs: Int = 60_000,
    ): JSONObject {
        val (code, text) = send(method, url, headers, body, readTimeoutMs)
        if (code !in 200..299) throw HttpError(code, "HTTP $code: ${errorMessage(text)}")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun errorMessage(text: String): String = runCatching {
        val o = JSONObject(text)
        o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: o.optString("message").takeIf { it.isNotBlank() }
            ?: text.take(200)
    }.getOrDefault(text.take(200))
}
