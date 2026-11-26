import { Directive, ElementRef, booleanAttribute, inject, input } from '@angular/core';

const CONTROLS =
  'a, button, input, select, textarea, label, summary, [role="button"], [contenteditable="true"]';

/**
 * Makes a whole table row open the detail its first link points to. The link stays the keyboard and
 * assistive-technology path; a click on the rest of the row follows it, in a new tab with Ctrl, Cmd, Shift or the middle button, while clicks on
 * controls inside the row and clicks that end a text selection keep their own meaning.
 */
@Directive({
  selector: 'tr[appRowLink]',
  host: {
    '[class.app-row-link]': 'appRowLink()',
    '(click)': 'open($event)',
    '(auxclick)': 'open($event)',
    '(mousedown)': 'keepMiddleButton($event)',
  },
})
export class RowLinkDirective {
  private readonly row = inject<ElementRef<HTMLTableRowElement>>(ElementRef).nativeElement;

  readonly appRowLink = input(true, { transform: booleanAttribute });

  /**
   * Repeats a click on the row on its link.
   *
   * @param event the click or middle click on the row
   */
  open(event: MouseEvent): void {
    if (
      !this.appRowLink() ||
      event.defaultPrevented ||
      (event.type === 'auxclick' && event.button !== 1)
    ) {
      return;
    }
    const target = event.target instanceof Element ? event.target : null;
    if (!target || target.closest(CONTROLS) || this.selecting()) {
      return;
    }
    const link = this.row.querySelector<HTMLAnchorElement>('a[href]');
    if (!link) {
      return;
    }
    const newTab = event.ctrlKey || event.metaKey || event.shiftKey || event.button === 1;
    if (newTab) {
      window.open(link.href, '_blank', 'noopener');
      return;
    }
    link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  }

  /**
   * Stops the middle button from starting autoscroll on the row, so its click opens the link in a new tab.
   *
   * @param event the button press on the row
   */
  keepMiddleButton(event: MouseEvent): void {
    const target = event.target instanceof Element ? event.target : null;
    if (this.appRowLink() && event.button === 1 && target && !target.closest(CONTROLS)) {
      event.preventDefault();
    }
  }

  private selecting(): boolean {
    const selection = window.getSelection();
    return !!selection && !selection.isCollapsed && this.row.contains(selection.anchorNode);
  }
}
