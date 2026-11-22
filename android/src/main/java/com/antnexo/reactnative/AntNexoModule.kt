package com.antnexo.reactnative

import android.util.Log
import com.antnexo.AntNexo
import com.antnexo.AntNexoEvent
import com.antnexo.AntNexoWrapperApi
import com.antnexo.TokenProvider
import com.antnexo.core.BridgePlatform
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** The TurboModule: maps arguments and calls the vendored Android SDK (`com.antnexo.AntNexo`). */
class AntNexoModule(reactContext: ReactApplicationContext) : NativeAntNexoSpec(reactContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listener: (AntNexoEvent) -> Unit = { e -> emitOnEvent(Arguments.makeNativeMap(eventMap(e))) }

    init {
        // the SDK's listener list is main-thread only
        UiThreadUtil.runOnUiThread { AntNexo.addEventListener(listener) }
    }

    @OptIn(AntNexoWrapperApi::class)
    override fun setWrapper(sdk: String) {
        AntNexo.setWrapper(BridgePlatform.REACT_NATIVE, sdk)
    }

    override fun configure(host: String, workspace: String, options: ReadableMap, promise: Promise) {
        val o = feedbackOptions(options.toHashMap())
        UiThreadUtil.runOnUiThread {
            try {
                AntNexo.configure(reactApplicationContext, host, workspace, o)
                promise.resolve(null)
            } catch (t: Throwable) {
                reject(promise, t)
            }
        }
    }

    override fun show(tab: String?, screenshot: Boolean, token: String?, promise: Promise) {
        UiThreadUtil.runOnUiThread {
            val activity = reactApplicationContext.currentActivity
            if (activity == null) {
                Log.w(TAG, "AntNexo.show(): no current activity")
                promise.resolve(null)
                return@runOnUiThread
            }
            // this opening's token only: the next show() sets its own (or none)
            AntNexo.identify(token?.takeIf { it.isNotEmpty() }?.let { t -> TokenProvider { t } })
            // resolves when the sheet is gone: closed, or it couldn't open; then the token goes, so no later opening
            // (native or Compose) reuses it
            AntNexo.show(activity, tabOf(tab), screenshot, onClosed = {
                AntNexo.identify(null)
                promise.resolve(null)
            })
        }
    }

    override fun close() {
        AntNexo.close()
    }

    override fun unreadCount(promise: Promise) {
        scope.launch {
            try {
                promise.resolve(AntNexo.unreadCount())
            } catch (t: Throwable) {
                reject(promise, t)
            }
        }
    }

    override fun logout(promise: Promise) {
        scope.launch {
            try {
                AntNexo.identify(null)
                AntNexo.logout()
                promise.resolve(null)
            } catch (t: Throwable) {
                reject(promise, t)
            }
        }
    }

    /** The JS bundle reloaded or the app is going away: no events into a dead context. An open sheet stays usable. */
    override fun invalidate() {
        UiThreadUtil.runOnUiThread { AntNexo.removeEventListener(listener) }
        scope.cancel()
        super.invalidate()
    }

    private fun reject(promise: Promise, t: Throwable) {
        val (code, status) = errorCode(t)
        val info = Arguments.createMap()
        status?.let { info.putInt("status", it) }
        // the SDK's messages never carry the token or the app's data
        promise.reject(code, t.message ?: code, t, info)
    }

    companion object {
        const val NAME = NativeAntNexoSpec.NAME
        private const val TAG = "AntNexo"
    }
}
