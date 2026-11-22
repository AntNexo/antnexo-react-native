export type FeedbackTab = 'new' | 'popular' | 'roadmap' | 'changelog';
export type FeedbackTheme = 'light' | 'dark' | 'auto';
export type WhatsNewMode = 'badge' | 'popup' | 'off';

/** How the sheet looks and what it shows. Every field is optional. */
export type FeedbackOptions = {
  /** The tab it opens on. */
  tab?: FeedbackTab;
  /** Which tabs, in that order. */
  tabs?: FeedbackTab[];
  /** Which boards (slugs). */
  boards?: string[];
  /** `auto` (the default) follows the system. */
  theme?: FeedbackTheme;
  /** Accent color, e.g. `#4f46e5`. */
  color?: string;
  /** Your team's in-app questions. */
  prompts?: boolean;
  whatsNew?: WhatsNewMode;
  /** By default the app's language. */
  language?: string;
};

export type AntNexoConfig = {
  /** The board host: `https://<slug>.antnexo.com` (recommended) or the workspace's custom domain. */
  host: string;
  /** The workspace slug. */
  workspace: string;
} & FeedbackOptions;

export type ShowOptions = {
  tab?: FeedbackTab;
  /** Attach a screenshot of the app as it is now (the person can remove it). Off by default. */
  screenshot?: boolean;
};

export type AntNexoEvent =
  | { type: 'posted'; id: string }
  | { type: 'closed' }
  | { type: 'unread'; count: number }
  | { type: 'signed_in'; signedIn: boolean }
  | { type: 'identify_failed'; code: string };

/** Returns a fresh identify token from your backend, or nothing to open without signing the person in. */
export type TokenProvider = () => Promise<string | null | undefined>;
