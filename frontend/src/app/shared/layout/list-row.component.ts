import { FocusableOption, FocusKeyManager } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  contentChildren,
  DestroyRef,
  Directive,
  ElementRef,
  inject,
  Injector,
  input,
} from '@angular/core';

import { IconComponent } from '../icons/icon.component';
import { IconName } from '../icons/icons';

/**
 * One row of a split page's list, on a native link or button so that Enter and a click select it: initials, a title,
 * a meta line and the chips the caller projects with the rowEnd attribute. At least 44 px tall with a visible focus
 * ring; the selected row is marked aria-current.
 */
@Component({
  selector: 'a[appListRow], button[appListRow]',
  imports: [IconComponent],
  host: {
    class: 'list-row',
    '[class.list-row--selected]': 'selected()',
    '[attr.aria-current]': "selected() ? 'true' : null",
  },
  template: `
    @if (initials()) {
      <span class="list-row__avatar" aria-hidden="true">{{ initials() }}</span>
    } @else if (icon(); as icon) {
      <span class="list-row__icon"><app-icon [name]="icon" /></span>
    }
    <span class="list-row__text">
      <span class="list-row__title">{{ rowTitle() }}</span>
      @if (meta()) {
        <span class="list-row__meta">{{ meta() }}</span>
      }
    </span>
    <span class="list-row__end"><ng-content select="[rowEnd]" /></span>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;
      min-height: 44px;
      box-sizing: border-box;
      padding: 12px 18px;
      border: 0;
      border-bottom: 1px solid var(--app-row-divider);
      border-radius: 0;
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: inherit;
      text-align: start;
      text-decoration: none;
      cursor: pointer;
    }

    :host(:last-child) {
      border-bottom: 0;
    }

    :host(:hover) {
      background: color-mix(in srgb, var(--app-selected) 55%, transparent);
    }

    :host(:focus-visible) {
      outline: 2px solid var(--mat-sys-primary);
      outline-offset: -2px;
    }

    :host(.list-row--selected) {
      background: var(--app-selected);
      box-shadow: inset 3px 0 0 var(--app-selected-edge);
    }

    .list-row__avatar {
      display: inline-grid;
      flex: none;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: var(--app-avatar);
      color: var(--app-on-avatar);
      font: 700 0.75rem / 1 var(--app-font-heading);
    }

    .list-row__icon {
      display: inline-grid;
      flex: none;
      place-items: center;
      width: 36px;
      height: 36px;
      box-sizing: border-box;
      border: 1px solid var(--app-pane-border);
      border-radius: 10px;
      background: var(--app-pane-head);
      color: var(--mat-sys-primary);
    }

    .list-row__text {
      display: grid;
      flex: 1;
      gap: 2px;
      min-width: 0;
    }

    .list-row__title {
      font: 700 0.875rem / 1.35 var(--app-font-heading);
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .list-row__meta {
      font: 400 0.75rem / 1.4 var(--app-font-body);
      color: var(--app-muted);
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .list-row__end {
      display: inline-flex;
      flex: none;
      align-items: center;
      gap: 4px;
    }

    .list-row__end:empty {
      display: none;
    }

    :host(.list-row--wrap) .list-row__title {
      white-space: normal;
      overflow-wrap: anywhere;
    }

    @media (max-width: 599.98px) {
      .list-row__title,
      .list-row__meta {
        white-space: normal;
        overflow-wrap: anywhere;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListRowComponent implements FocusableOption {
  /** The initials in the round badge; none hides the badge. */
  readonly initials = input<string | null>(null);
  /** The icon in a square badge, shown when there are no initials. */
  readonly icon = input<IconName | null>(null);
  /** The row's title. */
  readonly rowTitle = input.required<string>();
  /** The line under the title. */
  readonly meta = input<string | null>(null);
  /** Whether this row's detail is open. */
  readonly selected = input(false);
  /** The row's link or button. */
  readonly host: HTMLElement = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  /** Moves the focus to the row. */
  focus(): void {
    this.host.focus();
  }
}

const TEXT_FIELDS = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

/**
 * Moves the focus between the list rows inside it with the arrow keys, Home and End; keys typed into a text field
 * stay with the field.
 */
@Directive({
  selector: '[appListRows]',
  host: { '(keydown)': 'onKeydown($event)' },
})
export class ListRowsDirective {
  private readonly rows = contentChildren(ListRowComponent, { descendants: true });
  private readonly manager = new FocusKeyManager(this.rows, inject(Injector))
    .withVerticalOrientation()
    .withHomeAndEnd();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.manager.destroy());
  }

  /**
   * Handles one key press inside the list.
   *
   * @param event the key press
   */
  onKeydown(event: KeyboardEvent): void {
    const target = event.target as HTMLElement;
    if (TEXT_FIELDS.has(target.tagName) || target.isContentEditable) {
      return;
    }
    const current = this.rows().findIndex((row) => row.host === target);
    if (current >= 0) {
      this.manager.updateActiveItem(current);
      this.manager.onKeydown(event);
    }
  }
}
