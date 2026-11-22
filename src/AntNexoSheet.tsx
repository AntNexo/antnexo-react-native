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

/**
 * The sheet as a component: `<AntNexoSheet visible={open} onDismiss={() => setOpen(false)} />`. It renders nothing
 * itself; the sheet is native.
 */
/** One AntNexo.show() this component started, until it resolves. */
type Opening = { done: boolean; closeTimer?: ReturnType<typeof setTimeout> };

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
    let o = opening.current;
    if (o && !o.done) {
      // re-run right after a cleanup (StrictMode, or visible false → true at once): keep the same opening
      clearTimeout(o.closeTimer);
      o.closeTimer = undefined;
    } else {
      const cur: Opening = { done: false };
      opening.current = o = cur;
      const { tab: t, screenshot: shot } = options.current;
      AntNexo.show({
        ...(t ? { tab: t } : {}),
        ...(shot ? { screenshot: true } : {}),
      })
        .catch((e) => console.error('AntNexo: the sheet could not open', e))
        .finally(() => {
          cur.done = true;
          if (opening.current === cur) opening.current = null;
          if (mounted.current) dismiss.current();
        });
    }
    const cur = o;
    return () => {
      // visible turned off or unmounted while the sheet is up: close it, unless the effect runs again at once
      if (cur.done) return;
      cur.closeTimer = setTimeout(() => {
        cur.closeTimer = undefined;
        if (!cur.done) AntNexo.close();
      }, 0);
    };
  }, [visible]);

  return null;
}
