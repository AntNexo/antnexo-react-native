import { validateConfig, validateShow } from '../options';
import { AntNexoError } from '../errors';

const ok = { host: 'https://splynx.antnexo.com', workspace: 'splynx' };

function codeOf(f: () => unknown): string | undefined {
  try {
    f();
  } catch (e) {
    expect(e).toBeInstanceOf(AntNexoError);
    return (e as AntNexoError).code;
  }
  return undefined;
}

describe('validateConfig', () => {
  test('a bare http(s) origin', () => {
    expect(validateConfig(ok)).toEqual({
      host: 'https://splynx.antnexo.com',
      workspace: 'splynx',
      options: {},
    });
    expect(
      validateConfig({ ...ok, host: 'https://splynx.antnexo.com/' }).host
    ).toBe('https://splynx.antnexo.com');
    expect(
      validateConfig({ ...ok, host: 'http://acme.localhost:8090' }).host
    ).toBe('http://acme.localhost:8090');
  });

  test.each([
    'ftp://x',
    'https://x.com/path',
    'https://x.com?q=1',
    'https://x.com#f',
    'https://u:p@x.com',
    '',
    'splynx.antnexo.com',
    'https://',
  ])('host %p is invalid', (host) => {
    expect(codeOf(() => validateConfig({ ...ok, host }))).toBe('invalid_host');
  });

  test('a blank workspace is invalid; it is trimmed', () => {
    expect(codeOf(() => validateConfig({ ...ok, workspace: '' }))).toBe(
      'invalid_workspace'
    );
    expect(codeOf(() => validateConfig({ ...ok, workspace: '  ' }))).toBe(
      'invalid_workspace'
    );
    expect(validateConfig({ ...ok, workspace: ' splynx ' }).workspace).toBe(
      'splynx'
    );
  });

  test('options pass through, unset ones are omitted, unknown keys dropped', () => {
    const c = validateConfig({
      ...ok,
      tab: 'roadmap',
      tabs: ['new', 'roadmap'],
      boards: ['ideas'],
      theme: 'dark',
      color: '#4f46e5',
      prompts: false,
      whatsNew: 'off',
      language: 'de',
      tab2: 'x',
      theme2: undefined,
    } as never);
    expect(c.options).toEqual({
      tab: 'roadmap',
      tabs: ['new', 'roadmap'],
      boards: ['ideas'],
      theme: 'dark',
      color: '#4f46e5',
      prompts: false,
      whatsNew: 'off',
      language: 'de',
    });
    expect(
      Object.keys(validateConfig({ ...ok, theme: undefined }).options)
    ).toEqual([]);
  });

  test.each([
    { tab: 'home' },
    { theme: 'blue' },
    { tabs: 'new' },
    { tabs: ['new', 'home'] },
    { boards: [1] },
    { prompts: 'on' },
    { whatsNew: 'sometimes' },
    { color: 5 },
    { language: '' },
  ])('option %p is invalid', (o) => {
    expect(codeOf(() => validateConfig({ ...ok, ...(o as object) }))).toBe(
      'invalid_options'
    );
  });
});

describe('validateShow', () => {
  test('defaults and values', () => {
    expect(validateShow(undefined)).toEqual({ tab: null, screenshot: false });
    expect(validateShow({ tab: 'changelog', screenshot: true })).toEqual({
      tab: 'changelog',
      screenshot: true,
    });
  });

  test('bad values', () => {
    expect(codeOf(() => validateShow({ tab: 'home' } as never))).toBe(
      'invalid_options'
    );
    expect(codeOf(() => validateShow({ screenshot: 'yes' } as never))).toBe(
      'invalid_options'
    );
  });
});
