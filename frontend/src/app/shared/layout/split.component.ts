import {
  afterNextRender,
  afterRenderEffect,
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  viewChild,
} from '@angular/core';

const RESTORE_WAIT_MS = 5000;
const CLICK_TO_OPEN_MS = 2000;
const USER_SCROLL_EVENTS = ['wheel', 'touchstart', 'keydown', 'pointerdown'] as const;

/** How a split page behaves below 960 px. */
export type SplitMode = 'stack' | 'detail';

/**
 * A page of a side column (at most 380 px, a list or summaries) beside a main column, both scrolling with the page
 * unless the side column is sticky.
 * The caller projects them with the splitSide and splitMain attributes. Below 960 px the columns stack with the side
 * column first; in the detail mode an open detail takes the whole width instead and the list comes back when it
 * closes.
 */
@Component({
  selector: 'app-split',
  host: {
    '[class.split--detail]': "mode() === 'detail'",
    '[class.split--detail-open]': "mode() === 'detail' && detailOpen()",
    '[class.split--sticky]': 'stickySide()',
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

    @media (min-width: 960px) {
      :host(.split--sticky) .split__side {
        position: sticky;
        top: 72px;
        max-height: calc(100dvh - 88px);
        overflow-y: auto;
        scrollbar-width: thin;
      }
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
  /** Whether the side column stays in view beside the main column from 960 px, scrolling on its own when taller. */
  readonly stickySide = input(false, { transform: booleanAttribute });
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly side = viewChild.required<ElementRef<HTMLElement>>('side');
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');
  private readonly open = computed(() => this.mode() === 'detail' && this.detailOpen());
  private lastFocusInSide: HTMLElement | null = null;
  private opener: HTMLElement | null = null;
  private lastClick: { scrollTop: number; at: number } | null = null;
  private listScroll: number | null = null;
  private stopRestoring: () => void = () => undefined;

  constructor() {
    let previous = false;
    afterRenderEffect(() => {
      const open = this.open();
      if (open !== previous) {
        previous = open;
        this.moveFocus(open);
      }
    });
    afterNextRender(() =>
      this.side().nativeElement.addEventListener(
        'click',
        () => {
          this.lastClick = {
            scrollTop: scroller(this.host.nativeElement).scrollTop,
            at: Date.now(),
          };
        },
        { capture: true },
      ),
    );
    inject(DestroyRef).onDestroy(() => this.stopRestoring());
  }

  private moveFocus(open: boolean): void {
    const side = this.side().nativeElement;
    const main = this.main().nativeElement;
    this.stopRestoring();
    if (open) {
      const click = this.lastClick;
      this.listScroll = click && Date.now() - click.at < CLICK_TO_OPEN_MS ? click.scrollTop : null;
      this.lastClick = null;
    }
    if (open && hidden(side)) {
      main.focus();
    } else if (!open && hidden(main)) {
      const opener = this.opener;
      const href = opener?.getAttribute('href') ?? null;
      const row = (): HTMLElement | null => (opener?.isConnected ? opener : linkTo(side, href));
      const giveBack = (): boolean =>
        main.contains(document.activeElement) || isLost() || this.onOwnHeading();
      const placeholder = giveBack()
        ? (row() ?? side.querySelector<HTMLElement>('a, button'))
        : null;
      placeholder?.focus({ preventScroll: true });
      const target = this.listScroll;
      this.listScroll = null;
      if (target === null) {
        placeholder?.scrollIntoView?.({ block: 'nearest' });
      } else {
        this.restoreListScroll(target, () => {
          const again = row();
          if (
            again &&
            placeholder &&
            again !== placeholder &&
            (isLost() || document.activeElement === placeholder)
          ) {
            again.focus({ preventScroll: true });
          }
        });
      }
    }
    this.opener = open ? this.lastFocusInSide : null;
  }

  private restoreListScroll(target: number, settle: () => void): void {
    const element = scroller(this.host.nativeElement);
    const apply = (): void => {
      element.scrollTop = target;
      settle();
    };
    apply();
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(apply);
    const timer = setTimeout(() => this.stopRestoring(), RESTORE_WAIT_MS);
    const stop = (): void => {
      observer.disconnect();
      clearTimeout(timer);
      USER_SCROLL_EVENTS.forEach((type) => element.removeEventListener(type, stop));
      this.stopRestoring = () => undefined;
    };
    observer.observe(this.side().nativeElement);
    USER_SCROLL_EVENTS.forEach((type) => element.addEventListener(type, stop, { passive: true }));
    this.stopRestoring = stop;
  }

  private onOwnHeading(): boolean {
    const active = document.activeElement;
    return !!active?.matches('h1') && !!this.host.nativeElement.parentElement?.contains(active);
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

function linkTo(side: HTMLElement, href: string | null): HTMLElement | null {
  if (href === null) {
    return null;
  }
  const links = side.querySelectorAll<HTMLElement>('a[href]');
  return [...links].find((link) => link.getAttribute('href') === href) ?? null;
}

function scroller(from: HTMLElement): HTMLElement {
  for (let element = from.parentElement; element; element = element.parentElement) {
    const overflow = getComputedStyle(element).overflowY;
    if (overflow === 'auto' || overflow === 'scroll') {
      return element;
    }
  }
  return (document.scrollingElement as HTMLElement | null) ?? document.documentElement;
}
