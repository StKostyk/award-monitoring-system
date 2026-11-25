import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  afterNextRender,
  computed,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';

/** One stop of the tour: the element it lights up and the translation keys of its text. */
export interface TourStop {
  key: 'search' | 'navigation' | 'newAward' | 'views';
  selector: string;
}

/** The four stops on My dashboard, in order. */
export const TOUR_STOPS: readonly TourStop[] = [
  { key: 'search', selector: '[data-testid="palette-open"]' },
  { key: 'navigation', selector: '[data-testid="side"] nav' },
  { key: 'newAward', selector: '[data-testid="dashboard-new-award"]' },
  { key: 'views', selector: '[data-testid="pane-views"]' },
];

/** The space around the lit element. */
const SPOTLIGHT_GAP = 6;
/** The bubble's width and its distance from the lit element and from the window edge. */
const BUBBLE_WIDTH = 352;
const BUBBLE_OFFSET = 12;
const EDGE = 16;
/** The least distance of the lit area from the window edge, so its outline stays whole. */
const EDGE_GAP = 3;

/** The lit area, in window coordinates. */
interface Box {
  top: number;
  left: number;
  width: number;
  height: number;
}

/**
 * A spotlight tour over the page: each stop dims the screen around one element and explains it in a bubble with
 * «Крок N з 4», back, next and skip; a stop whose element is not on screen (a phone with the menu closed, a page
 * without charts) shows its bubble in the middle. Escape ends the tour.
 */
@Component({
  selector: 'app-onboarding-tour',
  imports: [CdkTrapFocus, MatButton, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './onboarding-tour.component.html',
  styleUrl: './onboarding-tour.component.scss',
  host: {
    '(document:keydown.escape)': 'end()',
  },
})
export class OnboardingTourComponent implements OnDestroy {
  /** Emitted once when the tour ends, by the last «Далі», «Пропустити» or Escape. */
  readonly ended = output();

  protected readonly stops = TOUR_STOPS;
  protected readonly index = signal(0);
  protected readonly stop = computed(() => this.stops[this.index()]);
  protected readonly last = computed(() => this.index() === this.stops.length - 1);
  protected readonly spotlight = signal<Box | null>(null, { equal: sameBox });
  protected readonly bubble = signal<{ top: number; left: number } | null>(null, {
    equal: (a, b) => a?.top === b?.top && a?.left === b?.left,
  });

  private readonly document = inject(DOCUMENT);
  private readonly bubbleRef = viewChild.required<ElementRef<HTMLElement>>('bubbleBox');
  private readonly next = viewChild.required<string, ElementRef>('nextButton', {
    read: ElementRef,
  });
  private readonly returnFocus = this.document.activeElement as HTMLElement | null;
  private finished = false;

  private frame = 0;

  constructor() {
    afterNextRender(() => {
      this.show(0);
      this.follow();
    });
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.frame);
    this.returnFocus?.focus?.();
  }

  forward(): void {
    if (this.last()) {
      this.end();
    } else {
      this.show(this.index() + 1);
    }
  }

  back(): void {
    if (this.index() > 0) {
      this.show(this.index() - 1);
    }
  }

  end(): void {
    if (!this.finished) {
      this.finished = true;
      this.ended.emit();
    }
  }

  /** Places the spotlight and the bubble for the current stop; repeated on every frame while the tour is open. */
  measure(): void {
    const target = this.target();
    const viewport = { width: window.innerWidth, height: window.innerHeight };
    const height = this.bubbleRef().nativeElement.offsetHeight;
    const width = Math.min(BUBBLE_WIDTH, viewport.width - 2 * EDGE);
    if (!target) {
      this.spotlight.set(null);
      this.bubble.set({
        top: Math.max(EDGE, (viewport.height - height) / 2),
        left: (viewport.width - width) / 2,
      });
      return;
    }
    const rect = target.getBoundingClientRect();
    const left = Math.max(EDGE_GAP, rect.left - SPOTLIGHT_GAP);
    const top = Math.max(EDGE_GAP, rect.top - SPOTLIGHT_GAP);
    const right = Math.min(viewport.width - EDGE_GAP, rect.right + SPOTLIGHT_GAP);
    const bottom = Math.min(viewport.height - EDGE_GAP, rect.bottom + SPOTLIGHT_GAP);
    const box = { top, left, width: right - left, height: bottom - top };
    this.spotlight.set(box);
    this.bubble.set(placeBubble(box, { width, height }, viewport));
  }

  private show(index: number): void {
    this.index.set(index);
    this.target()?.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'instant' });
    this.measure();
    (this.next().nativeElement as HTMLElement).focus();
  }

  /** Keeps the spotlight on its element while the page scrolls, smooth-scrolls or changes its layout. */
  private follow(): void {
    this.measure();
    this.frame = requestAnimationFrame(() => this.follow());
  }

  private target(): HTMLElement | null {
    const element = this.document.querySelector<HTMLElement>(this.stop().selector);
    if (!element) {
      return null;
    }
    const rect = element.getBoundingClientRect();
    const onScreen =
      rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.left < window.innerWidth;
    return onScreen ? element : null;
  }
}

function sameBox(a: Box | null, b: Box | null): boolean {
  return (
    a === b ||
    (a !== null &&
      b !== null &&
      a.top === b.top &&
      a.left === b.left &&
      a.width === b.width &&
      a.height === b.height)
  );
}

/**
 * Puts the bubble beside a tall element (the navigation) when it fits, otherwise below the element or above it, and
 * keeps it inside the window.
 *
 * @param box the lit area
 * @param size the bubble's width and height
 * @param viewport the window's width and height
 * @return the bubble's top left corner
 */
export function placeBubble(
  box: Box,
  size: { width: number; height: number },
  viewport: { width: number; height: number },
): { top: number; left: number } {
  const clampLeft = (left: number) =>
    Math.min(Math.max(EDGE, left), viewport.width - size.width - EDGE);
  const clampTop = (top: number) =>
    Math.min(Math.max(EDGE, top), viewport.height - size.height - EDGE);
  const right = box.left + box.width + BUBBLE_OFFSET;
  if (box.height > size.height && right + size.width + EDGE <= viewport.width) {
    return { top: clampTop(box.top), left: right };
  }
  const below = box.top + box.height + BUBBLE_OFFSET;
  if (below + size.height + EDGE <= viewport.height) {
    return { top: below, left: clampLeft(box.left) };
  }
  return { top: clampTop(box.top - size.height - BUBBLE_OFFSET), left: clampLeft(box.left) };
}
