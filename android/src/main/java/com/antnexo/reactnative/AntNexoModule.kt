package com.antnexo.reactnative

import com.facebook.react.bridge.ReactApplicationContext

class AntNexoModule(reactContext: ReactApplicationContext) :
  NativeAntNexoSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeAntNexoSpec.NAME
  }
}
