import { SDK_VERSION } from '../version';

test('SDK_VERSION is the package version', () => {
  expect(SDK_VERSION).toBe(require('../../package.json').version);
});
