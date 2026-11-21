import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  viewChild,
} from '@angular/core';

/** How a split page behaves below 960 px. */
export type SplitMode = 'stack' | 'detail';

/**
 * A page of a side column (at most 380 px, a list or summaries) beside a main column, both scrolling with the page.
 * The caller projects them with the splitSide and splitMain attributes. Below 960 px the columns stack with the side
 * column first; in the detail mode an open detail takes the whole width instead and the list comes back when it
 * closes.
 */
@Component({
  selector: 'app-split',
  host: {
    '[class.split--detail]': "mode() === 'detail'",
    '[class.split--detail-open]': "mode() === 'detail' && detailOpen()",
  },
  template: `
    <div class="split__side" #side (focusin)="onFocusIn($event)">
      <ng-content select="[splitSide]" />
    </div>
    <div class="split__main" #main tabindex="-1"><ng-content select="[splitMain]" /></div>
  `,
  styles: `
    :host {
      display: grid;
      grid-template-columns: minmax(0, 380px) minmax(0, 1fr);
      align-items: start;
      gap: 24px;
    }

    .split__side,
    .split__main {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 16px;
      min-width: 0;
    }

    .split__main:focus {
      outline: none;
    }

    @media (max-width: 959.98px) {
      :host {
        grid-template-columns: minmax(0, 1fr);
        gap: 16px;
      }

      :host(.split--detail-open) .split__side,
      :host(.split--detail:not(.split--detail-open)) .split__main {
        display: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SplitComponent {
  /** Stack both columns below 960 px, or show either the list or the open detail. */
  readonly mode = input<SplitMode>('stack');
  /** Whether a detail is open in the main column (the detail mode only). */
  readonly detailOpen = input(false);
  private readonly side = viewChild.required<ElementRef<HTMLElement>>('side');
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');
  private readonly open = computed(() => this.mode() === 'detail' && this.detailOpen());
  private lastFocusInSide: HTMLElement | null = null;
  private opener: HTMLElement | null = null;

  constructor() {
    let previous = false;
    afterRenderEffect(() => {
      const open = this.open();
      if (open !== previous) {
        previous = open;
        this.moveFocus(open);
      }
    });
  }

  private moveFocus(open: boolean): void {
    const side = this.side().nativeElement;
    const main = this.main().nativeElement;
    if (open && hidden(side)) {
      main.focus();
    } else if (!open && hidden(main) && (main.contains(document.activeElement) || isLost())) {
      const back = this.opener?.isConnected
        ? this.opener
        : side.querySelector<HTMLElement>('a, button');
      back?.focus();
    }
    this.opener = open ? this.lastFocusInSide : null;
  }

  /**
   * Remembers the element in the side column that last held the focus, so that closing a narrow detail returns
   * there.
   *
   * @param event the focus event
   */
  protected onFocusIn(event: FocusEvent): void {
    this.lastFocusInSide = event.target as HTMLElement;
  }
}

function hidden(element: HTMLElement): boolean {
  return getComputedStyle(element).display === 'none';
}

function isLost(): boolean {
  return document.activeElement === null || document.activeElement === document.body;
}
