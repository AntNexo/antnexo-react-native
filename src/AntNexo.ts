import Native, { type NativeEvent } from './NativeAntNexo';
import { AntNexoError, type AntNexoErrorCode } from './errors';
import { validateConfig, validateShow } from './options';
import type {
  AntNexoConfig,
  AntNexoEvent,
  ShowOptions,
  TokenProvider,
} from './types';
import { SDK_VERSION } from './version';

/** How long the app's token provider may take before the sheet opens without identify. */
const IDENTIFY_TIMEOUT_MS = 10_000;

const CODES: readonly string[] = [
  'invalid_host',
  'invalid_workspace',
  'invalid_options',
  'not_configured',
  'http',
  'bad_response',
  'network',
];

let configured = false;
let tokenProvider: TokenProvider | null = null;
let presenting = false;
/** Bumped by close() and logout(): an opening still waiting for its token never happens. */
let generation = 0;
const listeners = new Set<(e: AntNexoEvent) => void>();

Native.setWrapper(SDK_VERSION);
Native.onEvent((raw) => {
  const e = toEvent(raw);
  if (!e) return;
  for (const l of [...listeners]) {
    try {
      l(e);
    } catch (err) {
      console.error('AntNexo: an event listener threw', err);
    }
  }
});

/** A native event as the public type, or null when it is malformed. */
function toEvent(raw: NativeEvent | null | undefined): AntNexoEvent | null {
  switch (raw?.type) {
    case 'posted':
      return typeof raw.id === 'string' ? { type: 'posted', id: raw.id } : null;
    case 'closed':
      return { type: 'closed' };
    case 'unread':
      return typeof raw.count === 'number'
        ? { type: 'unread', count: raw.count }
        : null;
    case 'signed_in':
      return typeof raw.signedIn === 'boolean'
        ? { type: 'signed_in', signedIn: raw.signedIn }
        : null;
    case 'identify_failed':
      return typeof raw.code === 'string'
        ? { type: 'identify_failed', code: raw.code }
        : null;
    default:
      return null;
  }
}

/** A native rejection as an AntNexoError (its code and, for http, the status). */
function toError(e: unknown): AntNexoError {
  if (e instanceof AntNexoError) return e;
  const err = e as {
    code?: unknown;
    message?: unknown;
    userInfo?: { status?: unknown };
  } | null;
  const code =
    typeof err?.code === 'string' && CODES.includes(err.code)
      ? (err.code as AntNexoErrorCode)
      : 'network';
  const status =
    typeof err?.userInfo?.status === 'number' ? err.userInfo.status : undefined;
  const message =
    typeof err?.message === 'string' ? err.message : `AntNexo: ${code}`;
  return new AntNexoError(code, message, status);
}

/** The app's token, or null when there is no provider, it fails, gives nothing, or takes longer than the timeout. */
async function identifyToken(): Promise<string | null> {
  const provider = tokenProvider;
  if (!provider) return null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<null>((resolve) => {
    timer = setTimeout(() => {
      console.warn(
        'AntNexo: the identify token provider took longer than 10 s; opening without identify'
      );
      resolve(null);
    }, IDENTIFY_TIMEOUT_MS);
  });
  try {
    const token = await Promise.race([provider(), timeout]);
    return typeof token === 'string' && token.length > 0 ? token : null;
  } catch {
    // never log the error itself: it may carry the token
    console.warn(
      'AntNexo: the identify token provider failed; opening without identify'
    );
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export const AntNexo = {
  /**
   * Once, at launch. `host` is the board host: `https://<slug>.antnexo.com` (recommended: it works on every plan and
   * never changes with a custom domain) or the workspace's custom domain.
   */
  async configure(config: AntNexoConfig): Promise<void> {
    const c = validateConfig(config);
    try {
      await Native.configure(c.host, c.workspace, c.options);
    } catch (e) {
      throw toError(e);
    }
    configured = true;
  },

  /**
   * When your app has its own accounts: a provider of a fresh identify token signed by your backend with the
   * workspace's identify secret (never put the secret in the app). Called each time the sheet opens. `null` stops.
   */
  identify(provider: TokenProvider | null): void {
    if (provider !== tokenProvider) generation++; // an opening waiting for the old provider's token never happens
    tokenProvider = provider;
  },

  /**
   * Shows the sheet. Resolves when it is gone again (closed, or it could not open). One at a time: while a sheet is
   * up or opening, another call resolves at once.
   */
  async show(options?: ShowOptions): Promise<void> {
    if (!configured)
      throw new AntNexoError(
        'not_configured',
        'AntNexo.show() before AntNexo.configure()'
      );
    const o = validateShow(options);
    if (presenting) return;
    presenting = true;
    const gen = generation;
    try {
      const token = await identifyToken();
      if (gen !== generation) return; // closed or logged out meanwhile: the token may be the previous person's
      await Native.show(o.tab, o.screenshot, token);
    } catch (e) {
      throw toError(e);
    } finally {
      presenting = false;
    }
  },

  /** Closes the sheet, as Done does (a `closed` event follows). */
  close(): void {
    generation++;
    Native.close();
  },

  /** Whether a sheet is up or opening. */
  get isShowing(): boolean {
    return presenting;
  },

  /** What's new: how many entries the person hasn't seen (for a badge on your own button). */
  async unreadCount(): Promise<number> {
    try {
      return await Native.unreadCount();
    } catch (e) {
      throw toError(e);
    }
  },

  /** Signs the person out of the sheet (closing it if open). Call it on every account switch in your app. */
  async logout(): Promise<void> {
    generation++;
    try {
      await Native.logout();
    } catch (e) {
      throw toError(e);
    }
  },

  /** Posts, the What's new count, sign-in, closing. */
  addListener(listener: (e: AntNexoEvent) => void): { remove(): void } {
    listeners.add(listener);
    return { remove: () => listeners.delete(listener) };
  },
};
