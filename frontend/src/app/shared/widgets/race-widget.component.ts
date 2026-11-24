import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { monthNames } from '../date-format';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { PaneComponent } from '../layout/pane.component';
import { BarListComponent, BarRow } from './bar-list.component';
import { crossing, cumulative, lastMonthOf } from './facts';
import { monthSpan } from './kpi-widget.component';
import { CalendarMonth, MonthCount } from './widget-data';

/** T2: this year to date against last year to the same month as a scoreboard, the turn or the gap in one line. */
@Component({
  selector: 'app-race-widget',
  imports: [BarListComponent, EmptyStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!hasData()) {
      <app-empty-state icon="chart-line" [text]="'widgets.empty' | transloco" />
    } @else if (table()) {
      <table class="analytics-table" data-testid="race-widget-table">
        <caption>
          {{
            'widgets.race.title' | transloco: { year: year(), previous: year() - 1 }
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col">{{ 'widgets.month' | transloco }}</th>
            <th scope="col">{{ year() }}</th>
            <th scope="col">{{ year() - 1 }}</th>
          </tr>
        </thead>
        <tbody>
          @for (name of months(); track $index) {
            <tr>
              <th scope="row">{{ name }}</th>
              <td>{{ $index < thisYear().length ? thisYear()[$index] : '' }}</td>
              <td>{{ lastYear()[$index] }}</td>
            </tr>
          }
        </tbody>
      </table>
    } @else {
      <p class="race__span">{{ span() }}</p>
      <div class="race__score" data-testid="race-score">
        <p class="race__side race__side--current">
          <b data-testid="race-current">{{ current() }}</b>
          <span>{{ year() }}</span>
        </p>
        <span class="race__vs" aria-hidden="true">vs</span>
        <p class="race__side">
          <b data-testid="race-previous">{{ previous() }}</b>
          <span>{{ year() - 1 }}</span>
        </p>
      </div>
      <app-bar-list
        class="race__bars"
        [rows]="bars()"
        [label]="'widgets.race.title' | transloco: { year: year(), previous: year() - 1 }"
        shortLabels
        thick
      />
      <p class="race__turn" data-testid="race-turn">
        <span>{{ turn() }}</span>
        <b>{{ gap() }}</b>
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .race__span {
      margin: 0 0 10px;
      font: 400 0.8125rem / 1.4 var(--app-font-body);
      color: var(--app-muted);
    }

    .race__score {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
      align-items: center;
      gap: 14px;
      text-align: center;
    }

    .race__side {
      display: grid;
      gap: 4px;
      margin: 0;

      b {
        font: 800 3.25rem / 1 var(--app-font-heading);
        font-variant-numeric: tabular-nums;
      }

      span {
        font: 400 0.8125rem / 1.3 var(--app-font-body);
        color: var(--app-muted);
      }
    }

    .race__side--current b {
      color: var(--app-bar);
    }

    .race__vs {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      border: 1px solid var(--app-pane-border, var(--app-row-divider));
      border-radius: 50%;
      font: 800 0.8125rem / 1 var(--app-font-heading);
      color: var(--app-muted);
    }

    .race__bars {
      margin-top: 14px;
    }

    .race__turn {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 10px;
      margin: 16px 0 0;
      padding: 12px 14px;
      border-radius: var(--app-radius-sm);
      background: var(--app-pane-head);
      font: 400 0.8125rem / 1.45 var(--app-font-body);

      b {
        font: 800 0.9375rem / 1.3 var(--app-font-heading);
        color: var(--app-bar);
        font-variant-numeric: tabular-nums;
      }
    }
  `,
})
export class RaceWidgetComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  /** The monthly counts of the year and the year before. */
  readonly byMonth = input.required<MonthCount[]>();
  /** The year to race against the one before. */
  readonly year = input.required<number>();
  /** The current month in Kyiv. */
  readonly today = input.required<CalendarMonth>();

  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly thisYear = computed(() =>
    cumulative(this.byMonth(), this.year(), lastMonthOf(this.year(), this.today())),
  );
  protected readonly lastYear = computed(() => cumulative(this.byMonth(), this.year() - 1));
  protected readonly months = computed(() => monthNames(this.language.current()));
  protected readonly current = computed(() => this.thisYear().at(-1) ?? 0);
  protected readonly previous = computed(() => this.lastYear()[this.thisYear().length - 1] ?? 0);
  protected readonly hasData = computed(() => this.current() + (this.lastYear().at(-1) ?? 0) > 0);
  protected readonly span = computed(() =>
    monthSpan(this.language.current(), this.thisYear().length),
  );
  protected readonly bars = computed<BarRow[]>(() => [
    { key: this.year(), label: String(this.year()), value: this.current(), current: true },
    { key: this.year() - 1, label: String(this.year() - 1), value: this.previous() },
  ]);
  protected readonly gap = computed(() => {
    const gap = this.current() - this.previous();
    return gap > 0 ? `+${gap}` : gap < 0 ? `−${-gap}` : '0';
  });
  protected readonly turn = computed(() => {
    this.language.current();
    const found = crossing(this.thisYear(), this.lastYear());
    const params = { year: this.year(), previous: this.year() - 1 };
    if (found?.kind === 'OVERTOOK') {
      const month = monthNames(this.language.current(), 'long')[found.month - 1];
      return this.transloco.translate('widgets.race.overtookIn', { ...params, month });
    }
    const gap = this.current() - this.previous();
    const key = gap > 0 ? 'ahead' : gap < 0 ? 'behind' : 'level';
    return this.transloco.translate(`widgets.race.${key}`, { ...params, gap: Math.abs(gap) });
  });
}
