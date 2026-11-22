import { useEffect, useRef } from 'react';
import { AntNexo } from './AntNexo';
import type { FeedbackTab } from './types';

export type AntNexoSheetProps = {
  /** Shows the sheet while true. */
  visible: boolean;
  /** The sheet is gone (closed, or it could not open): set `visible` back to false here. */
  onDismiss: () => void;
  tab?: FeedbackTab;
  /** Attach a screenshot of the app as it is now (the person can remove it). Off by default. */
  screenshot?: boolean;
};

/** One AntNexo.show() this component started, until it resolves. */
type Opening = {
  done: boolean;
  /** close() was sent: the sheet is on its way out, a re-open needs a new opening. */
  closing: boolean;
  ended: Promise<void>;
  closeTimer?: ReturnType<typeof setTimeout>;
};

/**
 * The sheet as a component: `<AntNexoSheet visible={open} onDismiss={() => setOpen(false)} />`. It renders nothing
 * itself; the sheet is native.
 */
export function AntNexoSheet({
  visible,
  onDismiss,
  tab,
  screenshot,
}: AntNexoSheetProps): null {
  const dismiss = useRef(onDismiss);
  dismiss.current = onDismiss;
  const options = useRef({ tab, screenshot });
  options.current = { tab, screenshot };
  const mounted = useRef(true);
  const opening = useRef<Opening | null>(null);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!visible) return;
    const start = (after: Promise<void>): Opening => {
      const o: Opening = {
        done: false,
        closing: false,
        ended: Promise.resolve(),
      };
      o.ended = after
        .then(() => {
          // closed or unmounted while waiting for the previous sheet to go
          if (o.closing || !mounted.current) return;
          const { tab: t, screenshot: shot } = options.current;
          return AntNexo.show({
            ...(t ? { tab: t } : {}),
            ...(shot ? { screenshot: true } : {}),
          });
        })
        .catch((e) => console.error('AntNexo: the sheet could not open', e))
        .finally(() => {
          o.done = true;
          // a newer opening took over (re-opened while this one was closing): it will dismiss when it ends
          if (opening.current !== o) return;
          opening.current = null;
          if (mounted.current) dismiss.current();
        });
      return o;
    };

    let o = opening.current;
    if (o && !o.done && !o.closing) {
      // re-run right after a cleanup (StrictMode, or visible false → true at once): keep the same opening
      clearTimeout(o.closeTimer);
      o.closeTimer = undefined;
    } else {
      // nothing open, or the last one is closing: a new opening once it has gone
      o = start(o && !o.done ? o.ended : Promise.resolve());
      opening.current = o;
    }
    const cur = o;
    return () => {
      // visible turned off or unmounted while the sheet is up: close it, unless the effect runs again at once
      if (cur.done) return;
      cur.closeTimer = setTimeout(() => {
        cur.closeTimer = undefined;
        if (cur.done) return;
        cur.closing = true;
        AntNexo.close();
      }, 0);
    };
  }, [visible]);

  return null;
}
