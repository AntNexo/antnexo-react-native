// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/** Puts the bridge into a WebView: the script at document start and the message listener, both for one origin. */
internal interface BridgeInstaller {
    /** Whether this WebView can carry the bridge safely (no fallback: spec §3a, the plan's Decisions). */
    fun supported(): Boolean

    fun install(web: WebView, script: String, origin: String, onMessage: (data: String?, sourceOrigin: Uri, isMainFrame: Boolean) -> Unit)

    fun remove(web: WebView)
}

/** The listener object the page sees (`window.antnexoNative`), only on the board origin. */
internal const val LISTENER = "antnexoNative"

/** androidx.webkit: `addDocumentStartJavaScript` + `addWebMessageListener`, never `addJavascriptInterface`. */
internal object WebViewCompatBridge : BridgeInstaller {
    private val scripts = WeakHashMap<WebView, ScriptHandler>()

    override fun supported() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    override fun install(web: WebView, script: String, origin: String, onMessage: (String?, Uri, Boolean) -> Unit) {
        // both or nothing: a page without the bridge is never loaded (the caller checked supported())
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val rules = setOf(origin)
        // the listener first: the script captures window.antnexoNative when it runs
        WebViewCompat.addWebMessageListener(web, LISTENER, rules) { _, message, sourceOrigin, isMainFrame, _ ->
            onMessage(message.data, sourceOrigin, isMainFrame)
        }
        scripts[web] = WebViewCompat.addDocumentStartJavaScript(web, script, rules)
    }

    override fun remove(web: WebView) {
        scripts.remove(web)?.remove()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(web, LISTENER)
        }
    }
}
