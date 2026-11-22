package antnexo.reactnative.example

import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "ReactNativeExample"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
        // intent extras (host, workspace, identify, scenario) become the app's initial props, for scripted checks:
        // adb shell am start -n antnexo.reactnative.example/.MainActivity --es scenario show
        override fun getLaunchOptions(): Bundle =
            Bundle().apply {
              for (k in listOf("host", "workspace", "identify", "scenario")) {
                intent?.getStringExtra(k)?.let { putString(k, it) }
              }
            }
      }
}
