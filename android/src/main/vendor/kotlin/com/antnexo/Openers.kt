// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

internal const val TAG = "AntNexo"

/** Opens a link outside the sheet (the browser). */
internal interface LinkOpener {
    fun open(url: String)
}

/** The system's sign-in browser for provider sign-in. */
internal interface SignInPresenter {
    /** Opens url; onClosed when the person closes it (not after finish()). */
    fun start(url: String, onClosed: () -> Unit)

    /** Closes it: the page is signed in. */
    fun finish()
}

/** A Custom Tab, else any browser, else nothing. Returns whether something opened. */
internal fun openInBrowser(activity: Activity, url: String): Boolean {
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, uri)
        return true
    } catch (e: ActivityNotFoundException) {
        // no browser with Custom Tabs: a plain view intent below
    }
    return try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no browser to open a link")
        false
    }
}

internal class CustomTabsOpener(private val activity: () -> Activity?) : LinkOpener {
    override fun open(url: String) {
        activity()?.let { openInBrowser(it, url) }
    }
}

/**
 * Provider sign-in in a Custom Tab, launched from [SignInActivity] in the app's task (the AppAuth pattern): finish()
 * brings that activity back on top, which closes the tab; the person closing the tab resumes it, which is onClosed.
 */
internal class CustomTabSignIn(private val activity: () -> Activity?) : SignInPresenter {
    override fun start(url: String, onClosed: () -> Unit) {
        val a = activity() ?: return onClosed()
        SignInActivity.pending = onClosed
        a.startActivity(Intent(a, SignInActivity::class.java).putExtra(SignInActivity.EXTRA_URL, url))
    }

    override fun finish() {
        if (SignInActivity.pending == null) return
        SignInActivity.pending = null
        val a = activity() ?: return
        a.startActivity(
            Intent(a, SignInActivity::class.java)
                .putExtra(SignInActivity.EXTRA_FINISH, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }
}
