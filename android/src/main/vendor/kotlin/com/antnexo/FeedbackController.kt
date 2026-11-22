// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.antnexo.core.NavigationGate
import com.antnexo.core.PageMessage
import com.antnexo.core.bridgeScript
import com.antnexo.core.isWebUrl
import com.antnexo.core.parsePageMessage

/**
 * The WebView and its bridge (spec §3/§3a): loads the app page once, injects `AntNexoApp` on the board origin, takes
 * messages only from the board's main frame, and sends every other navigation to the browser. No Activity code: the
 * activity gives it a WebView and shows what it reports.
 */
internal class FeedbackController(private val session: Session) {
    private val host = session.host
    private val page = session.page
    private val opener = session.deps.opener(session)
    private val signIn = session.deps.signIn(session)
    private var web: WebView? = null
    private var gate = NavigationGate(page)
    private var finished = false
    private var failed = false
    private var torn = false

    /** The WebView lacks what the bridge needs: the failure view says to update it. */
    var outdatedWebView = false
        private set

    /** The renderer died: this WebView can't be used again; Try again needs a new one. */
    var rendererGone = false
        private set

    /** Runs JS in the WebView (tests replace it). */
    var evaluate: (String) -> Unit = { js -> web?.evaluateJavascript(js, null) }

    /** Runs JS in the page, unless the page is gone. */
    fun runJs(js: String) {
        if (!torn && !rendererGone) evaluate(js)
    }

    /** The page didn't load (offline, refused, an error status, the renderer died): the retry view. */
    var onLoadFailed: (() -> Unit)? = null

    /** The page loaded. */
    var onLoaded: (() -> Unit)? = null

    /** The page's `<input type=file>`: the activity shows the picker. Returns whether it took the callback. */
    var onFileChooser: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(web: WebView) {
        this.web = web
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // the page has its own dark theme; never let WebView invert it
            web.settings.isAlgorithmicDarkeningAllowed = false
        }
        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        if (!session.deps.bridge.supported()) {
            outdatedWebView = true
            Log.e(TAG, "this Android System WebView can't carry the bridge safely; update it")
            return
        }
        session.deps.bridge.install(web, bridgeScript(session.state, LISTENER), host.origin, ::receive)
    }

    fun load() {
        if (outdatedWebView) return onLoadFailed?.invoke() ?: Unit
        finished = false
        failed = false
        web?.loadUrl(page)
    }

    /** Loads the page again after a failure. */
    fun retry() {
        gate = NavigationGate(page)
        load()
    }

    /** Stops the page and cuts the bridge: called when the sheet closes or the activity goes away. */
    fun tearDown() {
        if (torn) return
        torn = true
        onLoadFailed = null
        onLoaded = null
        onFileChooser = null
        val w = web ?: return
        // a WebView whose renderer died may only be removed and destroyed (the activity does that)
        if (rendererGone) return
        w.stopLoading()
        if (!outdatedWebView) session.deps.bridge.remove(w)
        w.webViewClient = Closed
        w.webChromeClient = null
        // an empty page ends the widget's timers and polls even if something still holds the WebView
        w.loadUrl("about:blank")
    }

    // messages

    fun receive(data: String?, sourceOrigin: Uri, isMainFrame: Boolean) {
        if (torn) return
        if (!isMainFrame || !host.matches(sourceOrigin.scheme ?: "", sourceOrigin.host ?: "", sourceOrigin.port)) {
            Log.i(TAG, "ignored a message from another frame or origin")
            return
        }
        val m = parsePageMessage(data, host)
        if (m == null) {
            Log.i(TAG, "ignored an invalid page message")
            return
        }
        handle(m)
    }

    fun handle(m: PageMessage) {
        when (m) {
            PageMessage.Ready -> Unit
            // only while the person is looking at the sheet: a page can't launch things behind their back
            is PageMessage.Open -> if (session.resumed) opener.open(m.url)
            is PageMessage.Auth -> if (session.resumed && !session.signingIn) {
                session.signingIn = true
                signIn.start(m.url) {
                    session.signingIn = false
                    // the page that is current now (the activity may have been recreated meanwhile)
                    (session.controller ?: this).runJs(AUTH_CLOSED)
                }
            }
            is PageMessage.Seen -> session.seenStore.set(m.raw, session.workspace)
            is PageMessage.Unread -> session.onEvent(AntNexoEvent.Unread(m.count))
            is PageMessage.Session -> {
                if (m.signedIn && session.signingIn) {
                    session.signingIn = false
                    signIn.finish()
                }
                session.onEvent(AntNexoEvent.SignedIn(m.signedIn))
            }
            is PageMessage.IdentifyFailed -> session.onEvent(AntNexoEvent.IdentifyFailed(m.code))
            is PageMessage.Posted -> session.onEvent(AntNexoEvent.Posted(m.id))
        }
    }

    // navigation (§3a: only the first load of the page; everything else to the browser or nowhere)

    /** true: cancel. A link the person tapped goes to the browser; a redirect of the page itself is a failed load. */
    fun shouldOverride(url: String, mainFrame: Boolean, isRedirect: Boolean, hasGesture: Boolean): Boolean {
        if (gate.allow(url, mainFrame)) {
            finished = false
            return false
        }
        if (mainFrame && hasGesture && !isRedirect && isWebUrl(url)) {
            if (session.resumed) opener.open(url)
        } else if (mainFrame && !finished) {
            fail()
        }
        return true
    }

    fun pageFinished() {
        if (failed || torn) return
        finished = true
        session.wiped()
        onLoaded?.invoke()
    }

    fun httpError(mainFrame: Boolean, status: Int) {
        if (mainFrame && status >= 400) fail()
    }

    fun receivedError(mainFrame: Boolean) {
        if (mainFrame) fail()
    }

    /** The renderer crashed or was killed: this WebView is dead; true tells the system the app handled it. */
    fun renderProcessGone(): Boolean {
        Log.w(TAG, "the WebView's renderer went away")
        rendererGone = true
        fail()
        return true
    }

    private fun fail() {
        if (torn) return
        failed = true
        onLoadFailed?.invoke()
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            shouldOverride(request.url.toString(), request.isForMainFrame, request.isRedirect, request.hasGesture())

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            // belt and braces: a main-frame document other than the page (history, a bypass) never stays
            if (url != null && url != page && url != "about:blank" && !torn) {
                view.stopLoading()
                fail()
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (url == page) pageFinished()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) =
            receivedError(request.isForMainFrame)

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) =
            httpError(request.isForMainFrame, response.statusCode)

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = renderProcessGone()
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean = onFileChooser?.invoke(callback, params) ?: false
    }

    companion object {
        const val AUTH_CLOSED = "window.dispatchEvent(new CustomEvent('antnexo:native',{detail:{type:'auth_closed'}}))"
    }
}

/** A closed sheet's WebView: goes nowhere, and a renderer dying before it is destroyed doesn't take the app down. */
private object Closed : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail) = true
}
