import { AntNexoError } from './errors';
import type {
  AntNexoConfig,
  FeedbackOptions,
  FeedbackTab,
  ShowOptions,
} from './types';

const TABS: readonly string[] = ['new', 'popular', 'roadmap', 'changelog'];
const THEMES: readonly string[] = ['light', 'dark', 'auto'];
const WHATS_NEW: readonly string[] = ['badge', 'popup', 'off'];

/** The validated arguments of the native configure. */
export type NativeConfig = {
  host: string;
  workspace: string;
  options: FeedbackOptions;
};

const bad = (what: string) =>
  new AntNexoError('invalid_options', `AntNexo: invalid option ${what}`);

function oneOf(
  name: string,
  v: unknown,
  allowed: readonly string[]
): string | undefined {
  if (v === undefined) return undefined;
  if (typeof v !== 'string' || !allowed.includes(v)) throw bad(name);
  return v;
}

function strings(
  name: string,
  v: unknown,
  allowed?: readonly string[]
): string[] | undefined {
  if (v === undefined) return undefined;
  if (
    !Array.isArray(v) ||
    v.some((x) => typeof x !== 'string' || (allowed && !allowed.includes(x)))
  )
    throw bad(name);
  return [...v];
}

/** The board host as a bare http(s) origin (a trailing slash allowed), or invalid_host. */
function origin(host: unknown): string {
  const invalid = new AntNexoError(
    'invalid_host',
    'AntNexo: host must be an http(s) origin like https://acme.antnexo.com'
  );
  if (typeof host !== 'string') throw invalid;
  const m = /^(https?):\/\/([^/?#@\s]+)\/?$/i.exec(host.trim());
  if (!m || !m[2] || m[2].startsWith(':')) throw invalid;
  return `${m[1]!.toLowerCase()}://${m[2]}`;
}

/** Checks configure's argument; the native SDKs check host and workspace again. */
export function validateConfig(c: AntNexoConfig): NativeConfig {
  const host = origin(c?.host);
  const workspace = typeof c.workspace === 'string' ? c.workspace.trim() : '';
  if (!workspace)
    throw new AntNexoError('invalid_workspace', 'AntNexo: workspace is empty');
  const o: FeedbackOptions = {};
  const tab = oneOf('tab', c.tab, TABS);
  if (tab) o.tab = tab as FeedbackTab;
  const tabs = strings('tabs', c.tabs, TABS);
  if (tabs) o.tabs = tabs as FeedbackTab[];
  const boards = strings('boards', c.boards);
  if (boards) o.boards = boards;
  const theme = oneOf('theme', c.theme, THEMES);
  if (theme) o.theme = theme as FeedbackOptions['theme'];
  if (c.color !== undefined) {
    if (typeof c.color !== 'string' || !c.color) throw bad('color');
    o.color = c.color;
  }
  if (c.prompts !== undefined) {
    if (typeof c.prompts !== 'boolean') throw bad('prompts');
    o.prompts = c.prompts;
  }
  const whatsNew = oneOf('whatsNew', c.whatsNew, WHATS_NEW);
  if (whatsNew) o.whatsNew = whatsNew as FeedbackOptions['whatsNew'];
  if (c.language !== undefined) {
    if (typeof c.language !== 'string' || !c.language) throw bad('language');
    o.language = c.language;
  }
  return { host, workspace, options: o };
}

/** Checks show's argument. */
export function validateShow(o: ShowOptions | undefined): {
  tab: FeedbackTab | null;
  screenshot: boolean;
} {
  const tab = oneOf('tab', o?.tab, TABS) as FeedbackTab | undefined;
  const screenshot = o?.screenshot ?? false;
  if (typeof screenshot !== 'boolean') throw bad('screenshot');
  return { tab: tab ?? null, screenshot };
}
