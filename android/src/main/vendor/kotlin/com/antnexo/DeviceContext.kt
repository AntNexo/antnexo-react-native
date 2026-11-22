// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.antnexo.core.AppContext
import com.antnexo.core.SeenStore
import com.antnexo.core.bcp47

/**
 * What the page sends with a post about the app: its label, version and build, the OS and its version, the device
 * model and the app's locale. No identifiers.
 */
internal fun deviceContext(context: Context): AppContext {
    val info = packageInfo(context)
    val locale = context.resources.configuration.locales.get(0)
    return AppContext(
        app = context.applicationInfo.loadLabel(context.packageManager).toString().take(64).ifEmpty { null },
        appVersion = info?.versionName?.take(64),
        appBuild = info?.let { versionCode(it).toString() },
        os = "Android",
        osVersion = Build.VERSION.RELEASE?.take(64),
        device = Build.MODEL?.take(64)?.ifEmpty { null },
        locale = locale?.let { bcp47(it.toLanguageTag()) },
    )
}

private fun packageInfo(context: Context): PackageInfo? = try {
    if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private fun versionCode(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

/** What's new's read state in the app's own preferences, under the widget's key `antnexo:<workspace>:seen`. */
internal class PrefsSeenStore(context: Context) : SeenStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("antnexo", Context.MODE_PRIVATE)

    override fun get(workspace: String): String? = prefs.getString("antnexo:$workspace:seen", null)

    override fun set(iso: String, workspace: String) {
        prefs.edit { putString("antnexo:$workspace:seen", iso) }
    }
}
