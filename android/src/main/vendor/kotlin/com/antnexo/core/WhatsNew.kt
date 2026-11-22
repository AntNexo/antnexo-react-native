// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Where the app keeps What's new's read state (the newest entry seen), per workspace. */
interface SeenStore {
    fun get(workspace: String): String?

    fun set(iso: String, workspace: String)
}

/** On a first run only this recent news counts as new, not the archive (the widget's `FIRST_VISIT_DAYS`). */
private const val FIRST_RUN_MILLIS = 30L * 24 * 60 * 60 * 1000

/**
 * The widget's rule (`unreadEntries`): entries published after the newest one seen; with nothing seen, those of the
 * last 30 days.
 */
fun unreadCount(published: List<Long>, seen: Long?, now: Long): Int {
    if (seen != null) return published.count { it > seen }
    val since = now - FIRST_RUN_MILLIS
    return published.count { it >= since }
}

private const val MAX_BODY = 1024 * 1024

/** Reads the board's public changelog (no sign-in, nothing personal sent). */
class ChangelogClient @JvmOverloads constructor(private val host: BoardHost, private val timeoutMs: Int = 15_000) {
    /**
     * The publication dates (epoch milliseconds) of the newest 20 entries; entries with an unreadable date are skipped. Throws
     * [AntNexoException.Http] on an error status, [AntNexoException.BadResponse] on an unexpected body, an
     * `IOException` when the network fails.
     */
    suspend fun publishedDates(): List<Long> = withContext(Dispatchers.IO) {
        val c = URL("${host.origin}/api/v1/public/changelog?limit=20").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            val status = c.responseCode
            if (status !in 200..299) throw AntNexoException.Http(status)
            val body = c.inputStream.use { readCapped(it) }.toString(Charsets.UTF_8)
            parseDates(body)
        } finally {
            c.disconnect()
        }
    }

    /** 20 entries are a few KB; a body past 1 MB isn't the changelog (a misconfigured domain) and isn't read. */
    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            out.write(buf, 0, n)
            if (out.size() > MAX_BODY) throw AntNexoException.BadResponse()
        }
    }

    private fun parseDates(body: String): List<Long> {
        val items = try {
            (Json.parseToJsonElement(body) as? JsonObject)?.get("items") as? JsonArray
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw AntNexoException.BadResponse()
        return items.mapNotNull { item ->
            ((item as? JsonObject)?.get("published_at") as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::parseIsoDate)
        }
    }
}
