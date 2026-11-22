// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebStorage
import androidx.core.content.edit
import com.antnexo.core.AntNexoException
import com.antnexo.core.AppContext
import com.antnexo.core.BoardHost
import com.antnexo.core.BridgePlatform
import com.antnexo.core.SDK_VERSION
import com.antnexo.core.ChangelogClient
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.FeedbackTab
import com.antnexo.core.NativeState
import com.antnexo.core.SeenStore
import com.antnexo.core.appPageUrl
import com.antnexo.core.parseIsoDate
import com.antnexo.core.unreadCount
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Your backend's identify token for the person using the app, or null to open the sheet signed out. */
fun interface TokenProvider {
    suspend fun token(): String?
}

/** Boards whose storage the next opening must clear (set by logout). */
internal interface WipeStore {
    /** The nonce of a logout whose wipe hasn't happened yet. */
    fun pending(origin: String): String?

    fun mark(origin: String, nonce: String)

    /** Done, unless a newer logout came meanwhile. */
    fun done(origin: String, nonce: String)
}

internal class PrefsWipeStore(context: Context) : WipeStore {
    private val prefs = context.applicationContext.getSharedPreferences("antnexo", Context.MODE_PRIVATE)

    override fun pending(origin: String) = prefs.getString("antnexo:wipe:$origin", null)

    // commit: a process killed right after logout must still wipe on the next opening
    override fun mark(origin: String, nonce: String) = prefs.edit(commit = true) { putString("antnexo:wipe:$origin", nonce) }

    override fun done(origin: String, nonce: String) {
        if (pending(origin) == nonce) prefs.edit(commit = true) { remove("antnexo:wipe:$origin") }
    }
}

/** The SDK's state behind the [AntNexo] facade, with seams for tests. Main thread, except where noted. */
internal class Engine {
    class Configuration(val host: BoardHost, val workspace: String, val options: FeedbackOptions, val app: Context)

    var configuration: Configuration? = null
        private set
    var tokenProvider: TokenProvider? = null
    var onEvent: ((AntNexoEvent) -> Unit)? = null
    var identifyTimeoutMs = 10_000L

    /**
     * Set by a wrapper (React Native): the platform and SDK version the page reports instead of this library's.
     * Volatile: a wrapper sets it from its own thread (React Native's module thread).
     */
    @Volatile
    var wrapper: Pair<BridgePlatform, String>? = null

    /** How long FeedbackActivity has to attach before the sheet counts as never opened (a blocked start). */
    var attachTimeoutMs = 5_000L

    // seams
    var seenStore: (Context) -> SeenStore = { PrefsSeenStore(it) }
    var wipeStore: (Context) -> WipeStore = { PrefsWipeStore(it) }
    var context: (Context) -> AppContext = { deviceContext(it) }
    var capture: (Activity, (String?) -> Unit) -> Unit = { a, done -> captureScreenshot(a, done) }
    var fetchDates: suspend (BoardHost) -> List<Long> = { ChangelogClient(it).publishedDates() }
    var clearData: suspend (BoardHost) -> Unit = { h -> WebStorage.getInstance().deleteOrigin(h.origin) }
    var now: () -> Long = { System.currentTimeMillis() }
    var deps = SessionDeps()

    /** Extra listeners (the Compose sheet), besides the app's onEvent. Main thread. */
    val listeners = mutableListOf<(AntNexoEvent) -> Unit>()

    /** A sheet is up or being prepared: one at a time. Lives only in memory, so a new process starts free. */
    var presenting = false
        private set

    /** Bumped by close() and logout(): a sheet still being prepared from before is dropped. */
    private var generation = 0
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.e(TAG, "AntNexo failed", e) },
    )

    /** Runs on its own threads: a token provider that blocks can't hold the sheet past the timeout. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun configure(context: Context, host: String, workspace: String, options: FeedbackOptions = FeedbackOptions()) {
        val h = BoardHost.parse(host)
        val ws = workspace.trim()
        if (ws.isEmpty()) throw AntNexoException.InvalidWorkspace()
        configuration = Configuration(h, ws, options, context.applicationContext ?: context)
    }

    /** Events reach the app on the main thread. */
    fun dispatch(e: AntNexoEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { dispatch(e) }
            return
        }
        onEvent?.invoke(e)
        for (l in listeners.toList()) l(e)
    }

    /**
     * Everything a sheet needs, or null when it can't open (not configured, one already up, something failed). The
     * screenshot is taken here, before the sheet covers the app. The session's end frees the next one.
     */
    suspend fun prepare(activity: Activity, tab: FeedbackTab?, screenshot: Boolean, onClosed: (() -> Unit)? = null): Session? {
        val c = configuration
        if (c == null) {
            Log.e(TAG, "AntNexo.show() before AntNexo.configure(context, host, workspace)")
            return null
        }
        if (presenting) return null
        presenting = true
        try {
            val shot: String? = if (screenshot) suspendCancellableCoroutine<String?> { cont -> capture(activity) { cont.resume(it) } } else null
            val token = identifyToken()
            val options = if (tab != null) c.options.copy(tab = tab) else c.options
            val store = seenStore(c.app)
            val wipes = wipeStore(c.app)
            val origin = c.host.origin
            val wipe = wipes.pending(origin)
            val state = NativeState(wrapper?.first ?: BridgePlatform.ANDROID, context(activity), token, store.get(c.workspace), shot,
                wipe, sdk = wrapper?.second ?: SDK_VERSION)
            return Session(
                host = c.host, workspace = c.workspace, page = appPageUrl(c.host, c.workspace, options), state = state,
                options = options, seenStore = store, onEvent = ::dispatch,
                onEnded = {
                    presenting = false
                    onClosed?.invoke()
                },
                deps = deps,
                onWiped = { if (wipe != null) wipes.done(origin, wipe) },
            )
        } catch (e: Throwable) {
            presenting = false
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "AntNexo.show(): couldn't prepare the sheet", e)
            return null
        }
    }

    /** The app's token, or null when it gives none, fails, or takes longer than the timeout. */
    private suspend fun identifyToken(): String? {
        val provider = tokenProvider ?: return null
        val work = background.async { provider.token() }
        val token = try {
            withTimeoutOrNull(identifyTimeoutMs) { work.await() }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException && !work.isCancelled) throw e
            Log.w(TAG, "the identify token provider failed; opening without identify")
            null
        }
        work.cancel()
        return token?.takeIf { it.isNotEmpty() }
    }

    /** Opens the sheet over activity; onClosed when it closes, or at once when it can't open. */
    fun show(activity: Activity, tab: FeedbackTab?, screenshot: Boolean, onClosed: (() -> Unit)? = null) {
        val gen = generation
        scope.launch {
            val s = prepare(activity, tab, screenshot, onClosed)
            if (s == null) {
                onClosed?.invoke()
                return@launch
            }
            if (gen != generation) {
                // closed or logged out while it was being prepared (it may carry the previous person's token)
                return@launch discard(s)
            }
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "AntNexo.show(): the activity went away before the sheet opened")
                return@launch discard(s)
            }
            Sessions.current = s
            try {
                activity.startActivity(Intent(activity, FeedbackActivity::class.java).putExtra(FeedbackActivity.EXTRA_SESSION, s.id))
            } catch (e: RuntimeException) {
                Log.e(TAG, "AntNexo.show(): couldn't start the sheet", e)
                Sessions.current = null
                return@launch discard(s)
            }
            // the system can refuse a start without an error (the app went to the background meanwhile)
            main.postDelayed({
                if (Sessions.current === s && s.activity == null && !s.ended) {
                    Log.w(TAG, "AntNexo.show(): the sheet didn't open")
                    Sessions.current = null
                    discard(s)
                }
            }, attachTimeoutMs)
        }
    }

    /** A sheet that never appeared: frees the next show() without a Closed event. */
    private fun discard(s: Session) {
        presenting = false
        s.onEnded()
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { close() }
            return
        }
        generation++
        Sessions.current?.close()
    }

    suspend fun unreadCount(): Int {
        val c = configuration ?: throw AntNexoException.NotConfigured()
        val dates = fetchDates(c.host)
        val seen = seenStore(c.app).get(c.workspace)?.let(::parseIsoDate)
        return unreadCount(dates, seen, now())
    }

    /** Main thread. */
    suspend fun logout() {
        val c = configuration ?: return
        generation++
        // the next page clears the board's storage before the widget reads it (deleteOrigin leaves localStorage)
        wipeStore(c.app).mark(c.host.origin, java.util.UUID.randomUUID().toString())
        // an open page keeps its session in memory: close it first
        Sessions.current?.close()
        clearData(c.host)
    }

    companion object {
        val shared = Engine()
    }
}
