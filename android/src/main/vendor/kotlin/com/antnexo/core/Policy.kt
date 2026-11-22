// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import java.net.URI
import java.net.URISyntaxException
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** A message from the page (`AntNexoApp.post`), after validation. Anything else is ignored. */
sealed interface PageMessage {
    data object Ready : PageMessage

    /** A link to open in the browser (http(s) only). */
    data class Open(val url: String) : PageMessage

    /** The board's own OAuth start URL, to open in the system's sign-in browser. */
    data class Auth(val url: String) : PageMessage

    /** The person saw What's new up to this entry. */
    data class Seen(val atMillis: Long, val raw: String) : PageMessage

    data class Unread(val count: Int) : PageMessage

    data class Session(val signedIn: Boolean) : PageMessage

    data class IdentifyFailed(val code: String) : PageMessage

    data class Posted(val id: String) : PageMessage
}

/** Messages are a few hundred bytes; anything bigger isn't one of ours. */
private const val MAX_MESSAGE = 64 * 1024

private val oauthPath = Regex("^/api/v1/public/oauth/[a-z0-9_-]{1,32}/start$")
private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val wholeNumber = Regex("^-?\\d{1,9}$")

/**
 * Parses and validates a page message (the JSON string `AntNexoApp.post` sent). The caller has already checked that it
 * came from the main frame on the board host. null: ignore it.
 */
fun parsePageMessage(data: String?, host: BoardHost): PageMessage? {
    if (data == null || data.length > MAX_MESSAGE) return null
    val m = try {
        Json.parseToJsonElement(data) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    } ?: return null
    return when (m.string("type")) {
        "ready" -> PageMessage.Ready
        "open" -> m.string("url")?.takeIf(::isWebUrl)?.let { PageMessage.Open(it) }
        "auth" -> m.string("url")?.takeIf { isWebUrl(it) && isOAuthStart(it, host) }?.let { PageMessage.Auth(it) }
        "seen" -> m.string("at")?.takeIf { it.length <= 64 }?.let { s -> parseIsoDate(s)?.let { PageMessage.Seen(it, s) } }
        "unread" -> m.int("count")?.takeIf { it in 0..999 }?.let { PageMessage.Unread(it) }
        "session" -> (m["signed_in"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull?.let { PageMessage.Session(it) }
        "identify_failed" -> m.string("code")?.let { PageMessage.IdentifyFailed(it.take(64)) }
        "posted" -> m.string("id")?.takeIf { uuid.matches(it) }?.let { PageMessage.Posted(it) }
        else -> null
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A JS integer: a number literal with no fraction or exponent (not a string, not a boolean). */
private fun JsonObject.int(key: String): Int? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p.isString || !wholeNumber.matches(p.content)) return null
    return p.content.toIntOrNull()
}

/** An http(s) URL with a host. */
internal fun isWebUrl(s: String): Boolean {
    val u = uri(s) ?: return false
    val scheme = u.scheme?.lowercase(Locale.ROOT)
    return (scheme == "https" || scheme == "http") && authority(u) != null
}

/** Whether u is the board's own `/api/v1/public/oauth/<provider>/start` (the path compared as sent, still encoded). */
internal fun isOAuthStart(s: String, host: BoardHost): Boolean {
    val u = uri(s) ?: return false
    val a = authority(u) ?: return false
    if (a.userInfo || !host.matches(u.scheme ?: return false, a.host, a.port)) return false
    return oauthPath.matches(u.rawPath ?: return false)
}

private fun uri(s: String): URI? = try {
    URI(s)
} catch (e: URISyntaxException) {
    null
}

private val isoDate = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?(Z|z|[+-]\\d{2}:\\d{2})$")

/**
 * An RFC 3339 date-time (`2026-10-13T14:47:34.251324Z`, `…+02:00`) as epoch milliseconds (fractions past
 * milliseconds dropped); null when it isn't one or names a day that doesn't exist. No java.time: it needs API 26.
 */
fun parseIsoDate(s: String): Long? {
    val m = isoDate.matchEntire(s) ?: return null
    val g = m.groupValues
    val (year, month, day, hour, minute, second) = listOf(g[1], g[2], g[3], g[4], g[5], g[6]).map(String::toInt)
    if (year < 1 || month !in 1..12 || hour > 23 || minute > 59 || second > 59) return null
    val offset = when (val z = g[8]) {
        "Z", "z" -> 0
        else -> {
            val oh = z.substring(1, 3).toInt()
            val om = z.substring(4, 6).toInt()
            if (oh > 23 || om > 59) return null
            (if (z[0] == '-') -1 else 1) * (oh * 60 + om)
        }
    }
    return try {
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT)
        // proleptic Gregorian, like RFC 3339 (and java.time): no switch to the Julian calendar before 1582
        c.gregorianChange = Date(Long.MIN_VALUE)
        c.isLenient = false
        c.clear()
        c.set(year, month - 1, 1)
        if (day !in 1..c.getActualMaximum(Calendar.DAY_OF_MONTH)) return null
        c.set(year, month - 1, day, hour, minute, second)
        c.timeInMillis + g[7].padEnd(3, '0').take(3).toInt() - offset * 60_000L
    } catch (e: IllegalArgumentException) {
        null
    }
}

private operator fun <T> List<T>.component6() = this[5]

/**
 * Lets through only the first load of the app page in the main frame: every later navigation (a link, a redirect, a
 * history step) is refused, and so is anything in a subframe.
 */
class NavigationGate(private val page: String) {
    private var loaded = false

    fun allow(url: String, mainFrame: Boolean): Boolean {
        if (!mainFrame || loaded || url != page) return false
        loaded = true
        return true
    }
}
