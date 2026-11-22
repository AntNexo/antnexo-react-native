// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo

/** Marks API for wrappers of this library (the React Native SDK), not for apps. */
@RequiresOptIn(message = "For wrappers of the AntNexo SDK (React Native), not for apps.", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class AntNexoWrapperApi
