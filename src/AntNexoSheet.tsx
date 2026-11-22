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
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!visible) return;
    let open = true;
    const o = options.current;
    AntNexo.show({
      ...(o.tab ? { tab: o.tab } : {}),
      ...(o.screenshot ? { screenshot: true } : {}),
    })
      .catch((e) => console.error('AntNexo: the sheet could not open', e))
      .finally(() => {
        open = false;
        if (mounted.current) dismiss.current();
      });
    return () => {
      // visible turned off, or the component went away, while the sheet was still up
      if (open) AntNexo.close();
    };
  }, [visible]);

  return null;
}
