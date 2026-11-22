export type AntNexoErrorCode =
  | 'invalid_host'
  | 'invalid_workspace'
  | 'invalid_options'
  | 'not_configured'
  | 'http'
  | 'bad_response'
  | 'network'
  | 'unavailable';

/**
 * What AntNexo calls reject with. `status` is set for `http`. `unavailable`: the native module isn't in this build
 * (web, Expo Go, or `pod install` / a rebuild is missing).
 */
export class AntNexoError extends Error {
  readonly code: AntNexoErrorCode;
  readonly status?: number;

  constructor(code: AntNexoErrorCode, message: string, status?: number) {
    super(message);
    this.name = 'AntNexoError';
    this.code = code;
    if (status !== undefined) this.status = status;
  }
}
