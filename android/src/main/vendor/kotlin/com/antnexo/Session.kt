// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.app.Activity
import com.antnexo.core.BoardHost
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.NativeState
import com.antnexo.core.SeenStore
import java.lang.ref.WeakReference

/** What the SDK reports to the app (on the main thread). */
sealed interface AntNexoEvent {
    /** The person created a post. */
    data class Posted(val id: String) : AntNexoEvent

    /** The sheet was closed. */
    data object Closed : AntNexoEvent

    /** What's new: how many entries the person hasn't seen. */
    data class Unread(val count: Int) : AntNexoEvent

    /** Whether the person is signed in in the sheet. */
    data class SignedIn(val signedIn: Boolean) : AntNexoEvent

    /** The identify token was refused (expired, wrong secret…); the person can still sign in our way. */
    data class IdentifyFailed(val code: String) : AntNexoEvent
}

/** The platform pieces a session uses; tests replace them. */
internal class SessionDeps(
    val bridge: BridgeInstaller = WebViewCompatBridge,
    val opener: (Session) -> LinkOpener = { s -> CustomTabsOpener { s.activity?.get() } },
    val signIn: (Session) -> SignInPresenter = { s -> CustomTabSignIn { s.activity?.get() } },
)

/**
 * One open sheet: what it shows and who hears about it. It outlives a FeedbackActivity recreated by the system and ends
 * exactly once ([end]), however the sheet goes away.
 */
internal class Session(
    val host: BoardHost,
    val workspace: String,
    val page: String,
    val state: NativeState,
    val options: FeedbackOptions,
    val seenStore: SeenStore,
    val onEvent: (AntNexoEvent) -> Unit,
    /** The sheet is gone: the next show() may open one. */
    val onEnded: () -> Unit,
    val deps: SessionDeps = SessionDeps(),
    /** A page loaded with state.wipe: the logout's wipe is done. */
    val onWiped: () -> Unit = {},
) {
    /** The controller of the live activity, if any. */
    var controller: FeedbackController? = null
    var activity: WeakReference<Activity>? = null

    /** The sheet is in front (resumed): only then may the page open links or a sign-in. */
    var resumed = false

    /** A sign-in tab is open: one at a time. */
    var signingIn = false
    var ended = false
        private set

    /** Tells a late FeedbackActivity started for an older session from this one. */
    val id: String = java.util.UUID.randomUUID().toString()

    /** A page carrying the wipe loaded: later openings needn't wipe again. */
    fun wiped() {
        if (state.wipe != null) onWiped()
    }

    /** Ends the session once: the page stops, the app hears Closed. */
    fun end() {
        if (ended) return
        ended = true
        controller?.tearDown()
        controller = null
        // a sign-in tab left open must not call into this (gone) page
        SignInActivity.pending = null
        if (Sessions.current === this) Sessions.current = null
        onEnded()
        onEvent(AntNexoEvent.Closed)
    }

    /** Closes the sheet from outside (logout): the page stops now, the activity finishes, which ends the session. */
    fun close() {
        // before anything else: the page must not write the board's storage after logout cleared it
        controller?.tearDown()
        val a = activity?.get()
        if (a != null && !a.isFinishing) a.finish() else end()
    }
}

/** The one session a FeedbackActivity and SignInActivity attach to. Main thread only. */
internal object Sessions {
    var current: Session? = null
}
