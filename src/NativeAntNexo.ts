import {
  TurboModuleRegistry,
  type CodegenTypes,
  type TurboModule,
} from 'react-native';

/** An event from the native SDKs; `AntNexo` checks it and turns it into an `AntNexoEvent`. */
export type NativeEvent = {
  type: string;
  id?: string;
  count?: number;
  signedIn?: boolean;
  code?: string;
};

export type NativeOptions = {
  tab?: string;
  tabs?: string[];
  boards?: string[];
  theme?: string;
  color?: string;
  prompts?: boolean;
  whatsNew?: string;
  language?: string;
};

export interface Spec extends TurboModule {
  /** The bridge reports platform react-native and this package's version. */
  setWrapper(sdk: string): void;
  configure(
    host: string,
    workspace: string,
    options: NativeOptions
  ): Promise<void>;
  /** Resolves when the sheet is gone: closed, or it could not open. The token is for this opening only. */
  show(
    tab: string | null,
    screenshot: boolean,
    token: string | null
  ): Promise<void>;
  close(): void;
  unreadCount(): Promise<number>;
  logout(): Promise<void>;
  readonly onEvent: CodegenTypes.EventEmitter<NativeEvent>;
}

// null where it isn't linked (web, Expo Go, a missing pod install): AntNexo then rejects with `unavailable`
export default TurboModuleRegistry.get<Spec>('AntNexo');
