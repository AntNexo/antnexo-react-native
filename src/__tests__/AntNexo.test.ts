/* The native module is the boundary: a fake stands in for it. */
type Handler = (e: unknown) => void;

function fakeNative() {
  const handlers: Handler[] = [];
  const shows: Array<{ args: unknown[]; end: () => void }> = [];
  const n = {
    handlers,
    shows,
    setWrapper: jest.fn(),
    configure: jest.fn(async (..._a: unknown[]) => {}),
    show: jest.fn(
      (...args: unknown[]) =>
        new Promise<void>((resolve) => {
          shows.push({ args, end: resolve });
        })
    ),
    close: jest.fn(),
    unreadCount: jest.fn(async () => 3),
    logout: jest.fn(async () => {}),
    onEvent: jest.fn((h: Handler) => {
      handlers.push(h);
      return { remove: jest.fn() };
    }),
    emit: (e: unknown) => handlers.forEach((h) => h(e)),
  };
  return n;
}

let native: ReturnType<typeof fakeNative>;
let api: typeof import('../AntNexo');

const flush = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve();
};

beforeEach(() => {
  jest.resetModules();
  jest.useRealTimers();
  native = fakeNative();
  jest.doMock('../NativeAntNexo', () => ({
    __esModule: true,
    default: native,
  }));
  api = require('../AntNexo');
});

const configure = () =>
  api.AntNexo.configure({
    host: 'https://splynx.antnexo.com',
    workspace: 'splynx',
  });

test('loading the module sets the wrapper once, with this version', () => {
  expect(native.setWrapper).toHaveBeenCalledTimes(1);
  expect(native.setWrapper).toHaveBeenCalledWith(
    require('../../package.json').version
  );
});

describe('configure', () => {
  test('passes the validated arguments', async () => {
    await api.AntNexo.configure({
      host: 'https://splynx.antnexo.com/',
      workspace: ' splynx ',
      theme: 'dark',
    });
    expect(native.configure).toHaveBeenCalledWith(
      'https://splynx.antnexo.com',
      'splynx',
      { theme: 'dark' }
    );
  });

  test('rejects bad input without calling native', async () => {
    await expect(
      api.AntNexo.configure({ host: 'x', workspace: 'a' })
    ).rejects.toMatchObject({ code: 'invalid_host' });
    expect(native.configure).not.toHaveBeenCalled();
  });

  test('a native rejection keeps its code', async () => {
    native.configure.mockRejectedValueOnce(
      Object.assign(new Error('no'), { code: 'invalid_host' })
    );
    await expect(configure()).rejects.toMatchObject({
      name: 'AntNexoError',
      code: 'invalid_host',
    });
    // still not configured
    await expect(api.AntNexo.show()).rejects.toMatchObject({
      code: 'not_configured',
    });
  });
});

describe('show', () => {
  test('before configure: not_configured, nothing shown', async () => {
    await expect(api.AntNexo.show()).rejects.toMatchObject({
      code: 'not_configured',
    });
    expect(native.show).not.toHaveBeenCalled();
  });

  test('without a provider: no token; resolves when the sheet is gone', async () => {
    await configure();
    let done = false;
    const p = api.AntNexo.show({ tab: 'roadmap', screenshot: true }).then(
      () => (done = true)
    );
    await flush();
    expect(native.show).toHaveBeenCalledWith('roadmap', true, null);
    expect(api.AntNexo.isShowing).toBe(true);
    expect(done).toBe(false);
    native.shows[0]!.end();
    await p;
    expect(api.AntNexo.isShowing).toBe(false);
  });

  test('the provider token goes to that one opening', async () => {
    await configure();
    api.AntNexo.identify(async () => 'jwt');
    api.AntNexo.show();
    await flush();
    expect(native.show).toHaveBeenCalledWith(null, false, 'jwt');
  });

  test.each([
    ['throws', async () => Promise.reject(new Error('jwt-in-message'))],
    ['returns empty', async () => ''],
    ['returns null', async () => null],
    ['returns undefined', async () => undefined],
    ['returns a non-string', async () => 42 as unknown as string],
  ])('a provider that %s opens without identify', async (_name, provider) => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    await configure();
    api.AntNexo.identify(provider);
    api.AntNexo.show();
    await flush();
    expect(native.show).toHaveBeenCalledWith(null, false, null);
    for (const call of warn.mock.calls)
      expect(JSON.stringify(call)).not.toContain('jwt');
    warn.mockRestore();
  });

  test('a provider that hangs: after 10 s without identify', async () => {
    jest.useFakeTimers();
    await configure();
    api.AntNexo.identify(() => new Promise(() => {}));
    api.AntNexo.show();
    await flush();
    expect(native.show).not.toHaveBeenCalled();
    jest.advanceTimersByTime(9_999);
    await flush();
    expect(native.show).not.toHaveBeenCalled();
    jest.advanceTimersByTime(1);
    await flush();
    expect(native.show).toHaveBeenCalledWith(null, false, null);
  });

  test('one at a time: a second show while one waits for its token or is open does nothing', async () => {
    await configure();
    let release: (t: string) => void = () => {};
    api.AntNexo.identify(() => new Promise((r) => (release = r)));
    const first = api.AntNexo.show();
    await api.AntNexo.show(); // while waiting for the token: resolves at once
    release('jwt');
    await flush();
    await api.AntNexo.show(); // while open
    expect(native.show).toHaveBeenCalledTimes(1);
    native.shows[0]!.end();
    await first;
    api.AntNexo.show();
    await flush();
    release('jwt2');
    await flush();
    expect(native.show).toHaveBeenCalledTimes(2);
    expect(native.show).toHaveBeenLastCalledWith(null, false, 'jwt2');
  });

  test('close while waiting for the token: it never opens', async () => {
    await configure();
    let release: (t: string) => void = () => {};
    api.AntNexo.identify(() => new Promise((r) => (release = r)));
    const p = api.AntNexo.show();
    api.AntNexo.close();
    release('jwt');
    await p;
    expect(native.show).not.toHaveBeenCalled();
    expect(native.close).toHaveBeenCalledTimes(1);
    expect(api.AntNexo.isShowing).toBe(false);
  });

  test('a native show that rejects frees the next one', async () => {
    await configure();
    native.show.mockRejectedValueOnce(new Error('boom'));
    await expect(api.AntNexo.show()).rejects.toThrow();
    expect(api.AntNexo.isShowing).toBe(false);
  });

  test('invalid show options reject', async () => {
    await configure();
    await expect(
      api.AntNexo.show({ tab: 'home' } as never)
    ).rejects.toMatchObject({ code: 'invalid_options' });
  });
});

describe('events', () => {
  test('mapped 1:1, malformed ones dropped', () => {
    const got: unknown[] = [];
    api.AntNexo.addListener((e) => got.push(e));
    native.emit({ type: 'posted', id: 'p1' });
    native.emit({ type: 'closed' });
    native.emit({ type: 'unread', count: 4 });
    native.emit({ type: 'signed_in', signedIn: true });
    native.emit({ type: 'identify_failed', code: 'expired' });
    native.emit({ type: 'unread' });
    native.emit({ type: 'posted' });
    native.emit({ type: 'signed_in', signedIn: 'yes' });
    native.emit({ type: 'whatever' });
    native.emit(null);
    expect(got).toEqual([
      { type: 'posted', id: 'p1' },
      { type: 'closed' },
      { type: 'unread', count: 4 },
      { type: 'signed_in', signedIn: true },
      { type: 'identify_failed', code: 'expired' },
    ]);
  });

  test('a throwing listener does not stop the others; remove stops delivery', () => {
    const err = jest.spyOn(console, 'error').mockImplementation(() => {});
    const got: unknown[] = [];
    api.AntNexo.addListener(() => {
      throw new Error('app bug');
    });
    const sub = api.AntNexo.addListener((e) => got.push(e));
    native.emit({ type: 'closed' });
    sub.remove();
    native.emit({ type: 'closed' });
    expect(got).toHaveLength(1);
    err.mockRestore();
  });
});

test('identify() with a new provider leaves a pending opening alone (logout() is the account switch)', async () => {
  await configure();
  let release: (t: string) => void = () => {};
  api.AntNexo.identify(() => new Promise((r) => (release = r)));
  const p = api.AntNexo.show();
  api.AntNexo.identify(async () => 'again');
  release('jwt');
  await flush();
  expect(native.show).toHaveBeenCalledWith(null, false, 'jwt');
  native.shows[0]!.end();
  await p;
});

test('without the native module (web, Expo Go) importing works and calls reject with unavailable', async () => {
  jest.resetModules();
  jest.doMock('../NativeAntNexo', () => ({ __esModule: true, default: null }));
  const m: typeof import('../AntNexo') = require('../AntNexo');
  await expect(
    m.AntNexo.configure({
      host: 'https://splynx.antnexo.com',
      workspace: 'splynx',
    })
  ).rejects.toMatchObject({ code: 'unavailable' });
  await expect(m.AntNexo.unreadCount()).rejects.toMatchObject({
    code: 'unavailable',
  });
  await expect(m.AntNexo.logout()).rejects.toMatchObject({
    code: 'unavailable',
  });
  expect(() => m.AntNexo.close()).not.toThrow();
  expect(m.AntNexo.addListener(() => {}).remove).toBeInstanceOf(Function);
});

describe('unreadCount and logout', () => {
  test('unreadCount passes the number through', async () => {
    await expect(api.AntNexo.unreadCount()).resolves.toBe(3);
  });

  test('a native http error keeps its status', async () => {
    native.unreadCount.mockRejectedValueOnce(
      Object.assign(new Error('x'), { code: 'http', userInfo: { status: 503 } })
    );
    await expect(api.AntNexo.unreadCount()).rejects.toMatchObject({
      name: 'AntNexoError',
      code: 'http',
      status: 503,
    });
  });

  test('an unknown native error becomes network', async () => {
    native.unreadCount.mockRejectedValueOnce(new Error('offline'));
    await expect(api.AntNexo.unreadCount()).rejects.toMatchObject({
      code: 'network',
    });
  });

  test('logout during a token wait: that opening never happens', async () => {
    await configure();
    let release: (t: string) => void = () => {};
    api.AntNexo.identify(() => new Promise((r) => (release = r)));
    const p = api.AntNexo.show();
    await api.AntNexo.logout();
    release('old-person-jwt');
    await p;
    expect(native.logout).toHaveBeenCalledTimes(1);
    expect(native.show).not.toHaveBeenCalled();
  });
});
