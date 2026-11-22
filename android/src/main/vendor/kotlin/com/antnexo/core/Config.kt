// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo.core

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** What went wrong in an AntNexo call. */
sealed class AntNexoException(message: String) : Exception(message) {
    /** The host isn't a bare http(s) origin like `https://acme.antnexo.com`. */
    class InvalidHost : AntNexoException("the host must be a bare http(s) origin, like https://acme.antnexo.com")

    /** The workspace slug is empty. */
    class InvalidWorkspace : AntNexoException("the workspace slug is empty")

    /** `AntNexo.configure` wasn't called. */
    class NotConfigured : AntNexoException("AntNexo.configure(host, workspace) wasn't called")

    /** The API answered with this HTTP status. */
    class Http(val status: Int) : AntNexoException("the API answered $status")

    /** The API's answer wasn't what the SDK expected. */
    class BadResponse : AntNexoException("the API's answer wasn't what the SDK expected")
}

/** The widget's color scheme: [AUTO] follows the system. */
enum class FeedbackTheme(val value: String) { LIGHT("light"), DARK("dark"), AUTO("auto") }

/** The widget's tabs (`TabKey` in the widget). */
enum class FeedbackTab(val value: String) { NEW("new"), POPULAR("popular"), ROADMAP("roadmap"), CHANGELOG("changelog") }

/** How the widget shows What's new: a badge on its tab, a popup with the newest entry, or not at all. */
enum class WhatsNewMode(val value: String) { BADGE("badge"), POPUP("popup"), OFF("off") }

/** The board host the app opens: the workspace's `<slug>.antnexo.com` (recommended) or its custom domain. */
class BoardHost private constructor(
    val scheme: String,
    /** ASCII (punycode), lowercase: the form WebView reports origins in. */
    val host: String,
    /** null: the scheme's default port. */
    val port: Int?,
) {
    /** `https://splynx.antnexo.com`, with `:port` only when it isn't the default. */
    val origin: String get() = "$scheme://$host" + (port?.let { ":$it" } ?: "")

    /** A workspace's own `<slug>.antnexo.com` host (not a custom domain). */
    val isAntNexoSubdomain: Boolean get() = scheme == "https" && host.endsWith(".antnexo.com")

    /** Whether an origin is exactly this host; port -1, 0 or the scheme's default mean the default. */
    fun matches(scheme: String, host: String, port: Int): Boolean {
        val s = scheme.lowercase(Locale.ROOT)
        val p = if (port <= 0 || port == defaultPort(s)) null else port
        return s == this.scheme && host.lowercase(Locale.ROOT) == this.host && p == this.port
    }

    override fun equals(other: Any?) =
        other is BoardHost && other.scheme == scheme && other.host == host && other.port == port

    override fun hashCode() = origin.hashCode()

    override fun toString() = origin

    companion object {
        /** Parses `https://<host>[:port]` (a trailing `/` is fine); anything else throws [AntNexoException.InvalidHost]. */
        @JvmStatic
        fun parse(url: String): BoardHost {
            val u = try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw AntNexoException.InvalidHost()
            }
            val scheme = u.scheme?.lowercase(Locale.ROOT)
            if (scheme != "https" && scheme != "http") throw AntNexoException.InvalidHost()
            if (u.rawQuery != null || u.rawFragment != null) throw AntNexoException.InvalidHost()
            if (!(u.rawPath.isNullOrEmpty() || u.rawPath == "/")) throw AntNexoException.InvalidHost()
            val a = authority(u) ?: throw AntNexoException.InvalidHost()
            if (a.userInfo) throw AntNexoException.InvalidHost()
            // the page gets the person's identify token and maybe a screenshot: plain http only on this machine
            if (scheme == "http" && !isLocal(a.host)) throw AntNexoException.InvalidHost()
            return BoardHost(scheme, a.host, a.port.takeIf { it > 0 && it != defaultPort(scheme) })
        }

        internal fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

        private val loopback4 = Regex("^127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")

        /**
         * A host for local development: this machine (localhost, *.localhost, 127/8, ::1), or the host machine as an
         * emulator sees it (Android emulator 10.0.2.2, Genymotion 10.0.3.2).
         */
        private fun isLocal(host: String) =
            host == "localhost" || host.endsWith(".localhost") || loopback4.matches(host) || host == "[::1]" ||
                host == "::1" || host == "10.0.2.2" || host == "10.0.3.2"
    }
}

/** A URL's host (ASCII, lowercase) and port (-1: none), and whether it carries user info. */
internal class Authority(val host: String, val port: Int, val userInfo: Boolean)

/**
 * The host and port of u. java.net.URI reads a host it doesn't like (non-ASCII, an underscore, a bad port) as a
 * "registry" authority with no host and no port: then they come from the raw authority, so the port is never lost.
 * null: no host, or a port that isn't 1…65535.
 */
internal fun authority(u: URI): Authority? {
    if (u.host != null) {
        val host = asciiHost(u.host) ?: return null
        if (u.port == 0 || u.port > 65535) return null
        return Authority(host, u.port, u.rawUserInfo != null)
    }
    val raw = u.rawAuthority ?: return null
    val userInfo = raw.contains('@')
    val hostPort = raw.substringAfterLast('@')
    val colon = hostPort.lastIndexOf(':')
    val host = asciiHost(if (colon >= 0) hostPort.substring(0, colon) else hostPort) ?: return null
    val port = if (colon < 0) -1 else hostPort.substring(colon + 1).let { p ->
        if (p.isEmpty() || p.length > 5 || !p.all { it in '0'..'9' }) return null
        p.toInt().takeIf { it in 1..65535 } ?: return null
    }
    return Authority(host, port, userInfo)
}

/** A host name in ASCII (`xn--bcher-kva.example`), lowercase; null when it has none or can't be converted. */
internal fun asciiHost(host: String?): String? {
    if (host.isNullOrEmpty()) return null
    return try {
        IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** What the sheet shows; null leaves the widget's own default. */
data class FeedbackOptions @JvmOverloads constructor(
    /** The tab it opens on. */
    val tab: FeedbackTab? = null,
    /** Which tabs, in that order. */
    val tabs: List<FeedbackTab>? = null,
    /** Which boards (their slugs). */
    val boards: List<String>? = null,
    val theme: FeedbackTheme? = null,
    /** The accent color, like `#4f46e5`. */
    val color: String? = null,
    /** false: never ask the team's prompt questions. */
    val prompts: Boolean? = null,
    val whatsNew: WhatsNewMode? = null,
    /** The widget's language; by default it follows the app's. */
    val language: String? = null,
)

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

/** Percent-encodes everything but RFC 3986 unreserved characters (so `& = + # ? , '` and spaces are encoded). */
internal fun encodeQueryValue(v: String): String {
    val sb = StringBuilder()
    for (b in v.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xff
        if (c < 0x80 && UNRESERVED.indexOf(c.toChar()) >= 0) sb.append(c.toChar())
        else sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xf])
    }
    return sb.toString()
}

/** The app page: `<host>/app?workspace=<slug>` and the set options. Nothing personal goes in it. */
fun appPageUrl(host: BoardHost, workspace: String, o: FeedbackOptions): String {
    val q = mutableListOf("workspace" to listOf(workspace))
    o.tab?.let { q += "tab" to listOf(it.value) }
    o.tabs?.takeIf { it.isNotEmpty() }?.let { q += "tabs" to it.map(FeedbackTab::value) }
    o.boards?.takeIf { it.isNotEmpty() }?.let { q += "boards" to it }
    o.theme?.let { q += "theme" to listOf(it.value) }
    o.color?.let { q += "color" to listOf(it) }
    o.prompts?.let { q += "prompts" to listOf(if (it) "on" else "off") }
    o.whatsNew?.let { q += "whats-new" to listOf(it.value) }
    o.language?.let { q += "lang" to listOf(it) }
    // A list's commas stay commas (the widget splits on them); a comma inside a value is encoded.
    val query = q.joinToString("&") { (k, vs) -> "$k=" + vs.joinToString(",") { encodeQueryValue(it) } }
    return "${host.origin}/app?$query"
}
