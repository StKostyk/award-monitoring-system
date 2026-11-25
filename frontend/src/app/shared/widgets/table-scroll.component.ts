import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** The year columns that fit into the visible part of a scrolled table. */
export interface FitCount {
  shown: number;
  total: number;
}

/** A column edge relative to the scroll area, as measured in the browser. */
export interface ColumnBox {
  left: number;
  right: number;
}

/**
 * The year columns fully visible between the pinned column and the right edge of the scroll area.
 *
 * @param columns the year columns
 * @param start the left edge of the free area, after the pinned column
 * @param end the right edge of the scroll area
 * @return how many fit and how many there are
 */
export function fitCount(columns: ColumnBox[], start: number, end: number): FitCount {
  const slack = 1;
  const shown = columns.filter(
    (box) => box.left >= start - slack && box.right <= end + slack,
  ).length;
  return { shown, total: columns.length };
}

/**
 * A widget table's scroll area: reachable with Tab and named for screen readers, a shadow on the pinned first column
 * while more columns wait on the right, and, for year columns, the caption of how many years fit.
 */
@Component({
  selector: 'app-table-scroll',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      #area
      class="table-scroll"
      [class.table-scroll--more]="more()"
      tabindex="0"
      role="region"
      [attr.aria-label]="'widgets.table.scrollLabel' | transloco: { caption: label() }"
      (scroll)="measure()"
      data-testid="table-scroll"
    >
      <ng-content />
    </div>
    @if (yearHint() && fit(); as count) {
      @if (count.shown < count.total) {
        <p class="table-scroll__hint" aria-hidden="true" data-testid="table-scroll-hint">
          {{ 'widgets.table.scrollHint' | transloco: count }}
        </p>
      }
    }
  `,
  host: { class: 'table-scroll-host' },
})
export class TableScrollComponent {
  /** The table's caption, part of the scroll area's accessible name. */
  readonly label = input.required<string>();
  /** Whether to count the columns marked data-year and say how many fit. */
  readonly yearHint = input(false);

  protected readonly more = signal(false);
  protected readonly fit = signal<FitCount | null>(null);
  private readonly area = viewChild.required<ElementRef<HTMLElement>>('area');

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      this.measure();
      if (typeof ResizeObserver === 'undefined') {
        return;
      }
      const area = this.area().nativeElement;
      const resized = new ResizeObserver(() => this.measure());
      const watch = () => {
        resized.disconnect();
        resized.observe(area);
        const table = area.querySelector('table');
        if (table) {
          resized.observe(table);
        }
        this.measure();
      };
      const changed = new MutationObserver(watch);
      watch();
      changed.observe(area, { childList: true, subtree: true });
      destroyRef.onDestroy(() => {
        resized.disconnect();
        changed.disconnect();
      });
    });
  }

  /** Measures the overflow and the visible year columns again. */
  measure(): void {
    const area = this.area().nativeElement;
    this.more.set(area.scrollWidth - area.scrollLeft - area.clientWidth > 1);
    if (!this.yearHint()) {
      return;
    }
    const bounds = area.getBoundingClientRect();
    const pinned = area.querySelector('thead th:first-child');
    const start = pinned ? pinned.getBoundingClientRect().right : bounds.left;
    const columns = [...area.querySelectorAll('thead th[data-year]')].map((cell) => {
      const box = cell.getBoundingClientRect();
      return { left: box.left, right: box.right };
    });
    this.fit.set(fitCount(columns, start, bounds.left + area.clientWidth));
  }
}
