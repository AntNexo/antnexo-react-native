// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.app.Activity
import android.content.Context
import com.antnexo.core.BridgePlatform
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.FeedbackTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AntNexo in your app: the workspace's feedback board, roadmap and What's new in a sheet.
 *
 * ```kotlin
 * AntNexo.configure(this, "https://acme.antnexo.com", "acme")   // once, in Application.onCreate
 * AntNexo.show(activity)
 * ```
 */
object AntNexo {
    private val engine get() = Engine.shared

    /**
     * For wrappers of this library (the React Native SDK): the platform and SDK version the page reports. Not for apps.
     * Any thread; call it before the first `show`.
     */
    @AntNexoWrapperApi
    @JvmStatic
    fun setWrapper(platform: BridgePlatform, sdk: String) {
        engine.wrapper = platform to sdk
    }

    /**
     * Once, at launch. `host` is the workspace's board host: `https://<slug>.antnexo.com` (recommended: it works on
     * every plan and never changes with a custom domain) or its custom domain.
     *
     * @throws com.antnexo.core.AntNexoException.InvalidHost when host isn't a bare http(s) origin
     * @throws com.antnexo.core.AntNexoException.InvalidWorkspace when workspace is blank
     */
    @JvmStatic
    @JvmOverloads
    fun configure(context: Context, host: String, workspace: String, options: FeedbackOptions = FeedbackOptions()) {
        engine.configure(context, host, workspace, options)
    }

    /**
     * When your app has its own accounts: a provider of a fresh identify token, signed by your backend with the
     * workspace's identify secret (never put the secret in the app). Called each time the sheet opens; null, an error
     * or more than 10 seconds opens it without signing the person in. Pass null to stop.
     */
    @JvmStatic
    fun identify(provider: TokenProvider?) {
        engine.tokenProvider = provider
    }

    /** What happens in the sheet: posts, the What's new count, sign-in, closing. Called on the main thread. */
    @JvmStatic
    var onEvent: ((AntNexoEvent) -> Unit)?
        get() = engine.onEvent
        set(value) {
            engine.onEvent = value
        }

    /**
     * Shows the sheet over [activity] (main thread). One at a time: while one is up, this does nothing.
     *
     * @param screenshot attach a screenshot of the app as it is now; the person sees it and can remove it before
     *   sending. Off by default: the screen can hold private data.
     * @param onClosed called once when this sheet closes, or right away when it couldn't open.
     */
    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, tab: FeedbackTab? = null, screenshot: Boolean = false, onClosed: (() -> Unit)? = null) {
        engine.show(activity, tab, screenshot, onClosed)
    }

    /** Also told what happens in the sheet, besides [onEvent] (main thread). */
    @JvmStatic
    fun addEventListener(listener: (AntNexoEvent) -> Unit) {
        engine.listeners += listener
    }

    @JvmStatic
    fun removeEventListener(listener: (AntNexoEvent) -> Unit) {
        engine.listeners -= listener
    }

    /** Whether a sheet is open or opening. */
    @JvmStatic
    val isShowing: Boolean get() = engine.presenting

    /** Closes the sheet if one is open, or drops one still being prepared. */
    @JvmStatic
    fun close() {
        engine.close()
    }

    /**
     * What's new: how many entries the person hasn't seen (for a badge on your own button).
     *
     * @throws com.antnexo.core.AntNexoException.NotConfigured before [configure]
     * @throws com.antnexo.core.AntNexoException.Http or [com.antnexo.core.AntNexoException.BadResponse] when the board
     *   answers wrongly
     * @throws java.io.IOException when the network fails
     */
    suspend fun unreadCount(): Int = engine.unreadCount()

    /**
     * Signs the person out of the sheet: closes it (or drops one still opening) and clears the board's web data before
     * the next opening. Call it on every account switch, after [identify] stops returning the old person's token.
     */
    suspend fun logout() = withContext(Dispatchers.Main.immediate) { engine.logout() }
}
