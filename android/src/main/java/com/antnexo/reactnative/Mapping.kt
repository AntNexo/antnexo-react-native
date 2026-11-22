package com.antnexo.reactnative

import com.antnexo.AntNexoEvent
import com.antnexo.core.AntNexoException
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.FeedbackTab
import com.antnexo.core.FeedbackTheme
import com.antnexo.core.WhatsNewMode

// JS validated the values already; anything unknown is left out here rather than thrown.

internal fun tabOf(s: String?): FeedbackTab? = FeedbackTab.entries.firstOrNull { it.value == s }

internal fun feedbackOptions(m: Map<String, Any?>): FeedbackOptions {
    fun str(k: String) = m[k] as? String
    fun list(k: String) = (m[k] as? List<*>)?.filterIsInstance<String>()
    return FeedbackOptions(
        tab = tabOf(str("tab")),
        tabs = list("tabs")?.mapNotNull(::tabOf),
        boards = list("boards"),
        theme = FeedbackTheme.entries.firstOrNull { it.value == str("theme") },
        color = str("color"),
        prompts = m["prompts"] as? Boolean,
        whatsNew = WhatsNewMode.entries.firstOrNull { it.value == str("whatsNew") },
        language = str("language"),
    )
}

/** The event as JS receives it (`NativeEvent`). */
internal fun eventMap(e: AntNexoEvent): Map<String, Any> = when (e) {
    is AntNexoEvent.Posted -> mapOf("type" to "posted", "id" to e.id)
    AntNexoEvent.Closed -> mapOf("type" to "closed")
    is AntNexoEvent.Unread -> mapOf("type" to "unread", "count" to e.count)
    is AntNexoEvent.SignedIn -> mapOf("type" to "signed_in", "signedIn" to e.signedIn)
    is AntNexoEvent.IdentifyFailed -> mapOf("type" to "identify_failed", "code" to e.code)
}

/** The rejection's code (`AntNexoErrorCode` in JS) and, for http, the status. */
internal fun errorCode(t: Throwable): Pair<String, Int?> = when (t) {
    is AntNexoException.InvalidHost -> "invalid_host" to null
    is AntNexoException.InvalidWorkspace -> "invalid_workspace" to null
    is AntNexoException.NotConfigured -> "not_configured" to null
    is AntNexoException.Http -> "http" to t.status
    is AntNexoException.BadResponse -> "bad_response" to null
    else -> "network" to null
}
