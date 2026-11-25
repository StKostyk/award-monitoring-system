import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';

import { TableScrollComponent } from './table-scroll.component';

/** A row of the year grid: a unit or a kind of award, with one count per year. */
export interface YearGridRow {
  key: string;
  label: string;
  counts: number[];
}

/** A cell of the shown grid. */
export interface YearGridCell {
  year: number;
  count: number;
  current: boolean;
}

/** The grid as shown: years in display order, each row with its total, and the total per year. */
export interface YearGridView {
  years: { year: number; current: boolean }[];
  rows: { key: string; label: string; cells: YearGridCell[]; total: number }[];
  totals: number[];
  total: number;
}

/** The phone width below which the newest year comes first. */
export const PHONE_QUERY = '(max-width: 599.98px)';

/**
 * The grid of a year matrix: every count, a total per row and per year, the running year marked.
 *
 * @param years the years, oldest first
 * @param rows the rows, one count per year in the same order
 * @param running whether the last year is still running
 * @param newestFirst whether to show the newest year first
 * @return the grid in display order
 */
export function yearGrid(
  years: number[],
  rows: YearGridRow[],
  running: boolean,
  newestFirst: boolean,
): YearGridView {
  const last = years.length - 1;
  const order = years.map((_, index) => index);
  if (newestFirst) {
    order.reverse();
  }
  const totals = order.map((index) => rows.reduce((sum, row) => sum + (row.counts[index] ?? 0), 0));
  return {
    years: order.map((index) => ({ year: years[index], current: running && index === last })),
    rows: rows.map((row) => {
      const cells = order.map((index) => ({
        year: years[index],
        count: row.counts[index] ?? 0,
        current: running && index === last,
      }));
      return {
        key: row.key,
        label: row.label,
        cells,
        total: cells.reduce((sum, cell) => sum + cell.count, 0),
      };
    }),
    totals,
    total: totals.reduce((sum, count) => sum + count, 0),
  };
}

/**
 * A year matrix as a spreadsheet grid: every border drawn, a total per row and per year with the corner total,
 * the running year marked with an asterisk and a footnote, the first column pinned, the newest year first on a phone.
 */
@Component({
  selector: 'app-year-grid',
  imports: [TableScrollComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-table-scroll [label]="caption()" [yearHint]="true">
      <table class="analytics-table analytics-table--grid" [attr.data-testid]="testId()">
        <caption>
          {{
            caption()
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col">{{ rowHeader() }}</th>
            @for (column of view().years; track column.year) {
              <th
                scope="col"
                [attr.data-year]="column.year"
                [class.is-current]="column.current"
                data-testid="year-grid-year"
              >
                {{ column.year }}{{ column.current ? '*' : '' }}
              </th>
            }
            <th scope="col" class="is-total">{{ 'widgets.table.total' | transloco }}</th>
          </tr>
        </thead>
        <tbody>
          @for (row of view().rows; track row.key) {
            <tr [attr.data-testid]="'year-grid-row-' + row.key">
              <th scope="row">{{ row.label }}</th>
              @for (cell of row.cells; track cell.year) {
                <td [class.is-current]="cell.current">{{ cell.count }}</td>
              }
              <td class="is-total" data-testid="year-grid-row-total">{{ row.total }}</td>
            </tr>
          }
        </tbody>
        <tfoot>
          <tr data-testid="year-grid-totals">
            <th scope="row">{{ 'widgets.table.total' | transloco }}</th>
            @for (count of view().totals; track $index) {
              <td>{{ count }}</td>
            }
            <td data-testid="year-grid-total">{{ view().total }}</td>
          </tr>
        </tfoot>
      </table>
    </app-table-scroll>
    @if (running()) {
      <p class="analytics-note year-grid__note" data-testid="year-grid-running">
        * {{ 'widgets.trend.running' | transloco }}
      </p>
    }
    <ng-content />
  `,
})
export class YearGridComponent {
  /** The table's caption. */
  readonly caption = input.required<string>();
  /** The header of the first column. */
  readonly rowHeader = input.required<string>();
  /** The years, oldest first. */
  readonly years = input.required<number[]>();
  /** The rows, one count per year in the order of the years. */
  readonly rows = input.required<YearGridRow[]>();
  /** Whether the last year is still running. */
  readonly running = input(false);
  /** The test id of the table. */
  readonly testId = input<string | null>(null);

  private readonly phone = toSignal(
    inject(BreakpointObserver)
      .observe(PHONE_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected readonly view = computed(() =>
    yearGrid(this.years(), this.rows(), this.running(), this.phone()),
  );
}
