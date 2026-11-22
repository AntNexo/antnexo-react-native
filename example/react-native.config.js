const path = require('path');
const pkg = require('../package.json');

module.exports = {
  project: {
    ios: {
      automaticPodsInstallation: true,
    },
  },
  dependencies: {
    [pkg.name]: {
      root: path.join(__dirname, '..'),
      platforms: {
        // Codegen script incorrectly fails without this
        // So we explicitly specify the platforms with empty object
        ios: {},
        // the library's namespace is com.antnexo: see ../react-native.config.js
        android: {
          packageImportPath: 'import com.antnexo.reactnative.AntNexoPackage;',
          packageInstance: 'new AntNexoPackage()',
        },
      },
    },
  },
};
