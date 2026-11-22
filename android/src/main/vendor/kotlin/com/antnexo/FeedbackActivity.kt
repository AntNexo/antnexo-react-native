// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.antnexo.core.FeedbackTheme
import java.lang.ref.WeakReference

/**
 * The sheet: a top bar with Done, the WebView below it, and a native retry view when the page can't load. System Back
 * is Done. It attaches to [Sessions.current]; without one (the process was killed while it was open) it just finishes.
 */
class FeedbackActivity : ComponentActivity() {
    internal companion object {
        const val EXTRA_SESSION = "com.antnexo.session"
    }

    private var session: Session? = null
    private var controller: FeedbackController? = null
    internal var webView: WebView? = null
        private set
    internal var doneButton: View? = null
        private set
    internal var failureView: View? = null
        private set
    private var content: FrameLayout? = null
    private var root: View? = null
    private var pendingFiles: ValueCallback<Array<Uri>>? = null
    private var colors = Colors(dark = false, accent = null)

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = pendingFiles
        pendingFiles = null
        cb?.onReceiveValue(chosenFiles(r.resultCode, r.data))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = Sessions.current
        val forSession = intent?.getStringExtra(EXTRA_SESSION)
        // no session (the process died with the sheet open), or a late start meant for an older one
        if (s == null || s.ended || (forSession != null && forSession != s.id)) {
            finish()
            return
        }
        session = s
        s.activity = WeakReference(this)
        // recreated by the system: the old activity's page went with it
        s.controller?.tearDown()

        colors = Colors(dark = isDark(s.options.theme), accent = parseColor(s.options.color))
        applySystemBars()
        setContentView(buildViews())
        startPage(s)
        onBackPressedDispatcher.addCallback(this) { finish() }
    }

    /** A controller on the current WebView, loading the page. */
    private fun startPage(s: Session) {
        val c = FeedbackController(s)
        controller = c
        s.controller = c
        c.onLoadFailed = { showFailure(c.outdatedWebView) }
        c.onLoaded = { hideFailure() }
        c.onFileChooser = ::chooseFiles
        c.attach(webView!!)
        c.load()
    }

    /** Try again: the same WebView, or a new one when its renderer died (a dead WebView can't be used again). */
    internal fun retry() {
        val s = session ?: return
        val c = controller ?: return
        hideFailure()
        if (!c.rendererGone) return c.retry()
        c.tearDown()
        val old = webView!!
        val fresh = WebView(this).apply { setBackgroundColor(colors.background) }
        content!!.addView(fresh, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content!!.removeView(old)
        old.destroy()
        webView = fresh
        startPage(s)
    }

    override fun onResume() {
        super.onResume()
        session?.resumed = true
    }

    override fun onPause() {
        session?.resumed = false
        super.onPause()
    }

    /** Dark mode switched with the sheet open (uiMode is in configChanges, so the page and the draft stay). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val s = session ?: return
        val dark = isDark(s.options.theme)
        if (dark == colors.dark) return
        colors = colors.copy(dark = dark)
        applySystemBars()
        root?.setBackgroundColor(colors.background)
        webView?.setBackgroundColor(colors.background)
        (doneButton as? TextView)?.setTextColor(colors.action)
        failureView?.let {
            hideFailure()
            showFailure(controller?.outdatedWebView == true)
        }
    }

    private fun applySystemBars() {
        val bar = if (colors.dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        setBarIcons()
        // on a dark mode switch the system re-applies the window's own appearance after onConfigurationChanged
        window.decorView.post { setBarIcons() }
    }

    private fun setBarIcons() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !colors.dark
            isAppearanceLightNavigationBars = !colors.dark
        }
    }

    override fun onDestroy() {
        val s = session
        if (s != null) {
            if (isFinishing) {
                // Done, Back, or logout: the sheet is gone
                s.end()
            } else {
                // recreated (a configuration change we don't handle): the session goes on with the next activity
                controller?.tearDown()
                if (s.controller === controller) s.controller = null
            }
            if (s.activity?.get() === this) s.activity = null
        }
        pendingFiles?.onReceiveValue(null)
        pendingFiles = null
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    // views

    private data class Colors(val dark: Boolean, val accent: Int?) {
        val background get() = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
        val text get() = if (dark) Color.WHITE else 0xFF111111.toInt()
        val secondary get() = if (dark) 0xFFAEAEB2.toInt() else 0xFF6B6B70.toInt()
        val action get() = accent ?: text
    }

    private fun isDark(theme: FeedbackTheme?) = when (theme) {
        FeedbackTheme.LIGHT -> false
        FeedbackTheme.DARK -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun parseColor(c: String?): Int? = c?.let { runCatching { it.toColorInt() }.getOrNull() }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun buildViews(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
        }
        val bar = FrameLayout(this)
        val done = TextView(this).apply {
            text = getString(R.string.antnexo_done)
            setTextColor(colors.action)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            minWidth = dp(48)
            isClickable = true
            isFocusable = true
            val ripple = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                setBackgroundResource(ripple.resourceId)
            }
            setOnClickListener { finish() }
        }
        bar.addView(done, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val content = FrameLayout(this)
        val web = WebView(this).apply { setBackgroundColor(colors.background) }
        content.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        webView = web
        doneButton = done
        this.content = content
        this.root = root
        return root
    }

    private fun showFailure(outdated: Boolean) {
        if (failureView != null) return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colors.background)
            isClickable = true
            setPadding(dp(32), 0, dp(32), 0)
        }
        // wrap_content: a vertical LinearLayout's children default to its full width, which left-aligns their text
        val wrap = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        box.addView(TextView(this).apply {
            text = getString(if (outdated) R.string.antnexo_outdated_webview else R.string.antnexo_load_failed)
            setTextColor(colors.secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }, wrap())
        if (!outdated) {
            box.addView(TextView(this).apply {
                text = getString(R.string.antnexo_try_again)
                setTextColor(colors.action)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                minimumHeight = dp(48)
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener { retry() }
            }, wrap())
        }
        content?.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        failureView = box
    }

    private fun hideFailure() {
        failureView?.let { content?.removeView(it) }
        failureView = null
    }

    // files (the widget's attach button)

    private fun chooseFiles(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        // one chooser at a time; WebView needs an answer to the previous one
        pendingFiles?.onReceiveValue(null)
        pendingFiles = cb
        return try {
            // not params.createIntent(): it asks only for the first accepted type (PNG for the widget)
            fileChooser.launch(chooserIntent(params.acceptTypes, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE))
            true
        } catch (e: ActivityNotFoundException) {
            pendingFiles = null
            cb.onReceiveValue(null)
            true
        }
    }

    private fun chosenFiles(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != RESULT_OK || data == null) return null
        val clip = data.clipData
        val uris = if (clip != null && clip.itemCount > 0) Array(clip.itemCount) { clip.getItemAt(it).uri }
        else data.data?.let { arrayOf(it) } ?: return null
        return safeUris(this, uris)
    }
}

/** A picker for every type the page accepts (`accept` values may hold several, comma-separated). */
internal fun chooserIntent(acceptTypes: Array<String>?, multiple: Boolean): Intent {
    val all = acceptTypes.orEmpty().flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
    val types = all.filter { it.contains('/') }.distinct()
    val i = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
    when {
        // an extension (".pdf") we can't map reliably: offer everything rather than hide what the page takes
        all.size != types.size -> return i.setType("*/*").also { if (multiple) it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
        types.isEmpty() -> i.type = "*/*"
        types.all { it.startsWith("image/") } -> i.type = "image/*"
        else -> i.type = "*/*"
    }
    if (types.isNotEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
    if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    return i
}

/**
 * Only content the person picked from another app: content:// URIs whose provider isn't this app's own. A picker app
 * could otherwise hand back file:// paths or the app's own provider and have the page upload the app's private files.
 * null when nothing is left.
 */
internal fun safeUris(context: android.content.Context, uris: Array<Uri>): Array<Uri>? {
    val ok = uris.filter { u ->
        if (u.scheme != "content") return@filter false
        val authority = u.authority ?: return@filter false
        val owner = runCatching { context.packageManager.resolveContentProvider(authority, 0)?.packageName }.getOrNull()
        owner != context.packageName && !authority.startsWith(context.packageName + ".") && authority != context.packageName
    }
    return ok.takeIf { it.isNotEmpty() }?.toTypedArray()
}
