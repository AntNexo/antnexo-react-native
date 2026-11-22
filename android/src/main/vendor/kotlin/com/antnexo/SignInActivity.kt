// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.antnexo.core.isOAuthStart

/**
 * Hosts the provider sign-in Custom Tab in the app's task (the AppAuth pattern). It opens the tab on its first resume;
 * resumed again without being told to finish means the person closed the tab ([pending] runs: the page gets
 * `auth_closed`). Started again with [EXTRA_FINISH] and CLEAR_TOP, it clears the tab above it and goes away quietly.
 */
class SignInActivity : Activity() {
    private var launched = false
    private var finishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launched = savedInstanceState?.getBoolean(STATE_LAUNCHED) ?: false
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) {
            finishing = true
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) {
            finishing = true
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (finishing) return
        if (!launched) {
            launched = true
            val url = intent.getStringExtra(EXTRA_URL)
            // checked again here: only the open sheet's own OAuth start URL, whoever started this activity
            val host = Sessions.current?.host
            if (url == null || host == null || !isOAuthStart(url, host) || !openInBrowser(this, url)) closed()
            return
        }
        closed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LAUNCHED, launched)
    }

    private fun closed() {
        val p = pending
        pending = null
        p?.invoke()
        finishing = true
        finish()
    }

    internal companion object {
        const val EXTRA_URL = "com.antnexo.signin.url"
        const val EXTRA_FINISH = "com.antnexo.signin.finish"
        private const val STATE_LAUNCHED = "launched"

        /** The page's auth_closed, until the tab is closed or the page signs in. Main thread only. */
        var pending: (() -> Unit)? = null
    }
}
