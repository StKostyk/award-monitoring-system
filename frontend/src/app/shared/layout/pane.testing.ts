import { signal } from '@angular/core';

import { PaneComponent, PaneView } from './pane.component';

/**
 * A stand-in for the pane around a widget, so a spec can choose the view the widget follows.
 *
 * @param view the view the pane starts with
 * @return the stub and its provider
 */
export function paneStub(view: PaneView = 'chart') {
  const pane = {
    view: signal<PaneView>(view),
    heading: signal<string | null>(null),
    holdChart: () => () => undefined,
  };
  return { pane, provider: { provide: PaneComponent, useValue: pane } };
}
