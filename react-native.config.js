// The Android library's namespace is com.antnexo (the vendored SDK's), so autolinking is told where the package is.
module.exports = {
  dependency: {
    platforms: {
      android: {
        packageImportPath: 'import com.antnexo.reactnative.AntNexoPackage;',
        packageInstance: 'new AntNexoPackage()',
      },
    },
  },
};
