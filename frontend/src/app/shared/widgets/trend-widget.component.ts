import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { PaneComponent } from '../layout/pane.component';
import { BarListComponent, BarRow } from './bar-list.component';
import { peakYear } from './facts';
import { YearTotal } from './widget-data';
import { TableScrollComponent } from './table-scroll.component';

/** A year of T1 with what marks it. */
export interface TrendRow extends YearTotal {
  peak: boolean;
  partial: boolean;
}

/** The years of T1, the peak year and the partial year marked. */
export function trendRows(years: YearTotal[], partialYear: number | null): TrendRow[] {
  const peak = peakYear(years);
  return years.map((year) => ({
    ...year,
    peak: peak !== null && year.year === peak.year,
    partial: year.year === partialYear,
  }));
}

/** T1: awards per year as thick horizontal bars, the running year hatched; a table in the pane's table view. */
@Component({
  selector: 'app-trend-widget',
  imports: [BarListComponent, EmptyStateComponent, TableScrollComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!peak()) {
      <app-empty-state icon="chart-bar" [text]="'widgets.empty' | transloco" />
    } @else if (table()) {
      <app-table-scroll [label]="'widgets.trend.title' | transloco">
        <table class="analytics-table" data-testid="trend-widget-table">
          <caption>
            {{
              'widgets.trend.title' | transloco
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">{{ 'widgets.year' | transloco }}</th>
              <th scope="col">{{ 'widgets.awards' | transloco }}</th>
              <th scope="col">{{ 'widgets.note' | transloco }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.year) {
              <tr>
                <th scope="row">{{ row.year }}</th>
                <td>{{ row.total }}</td>
                <td>
                  @if (row.peak) {
                    {{ 'widgets.trend.peak' | transloco }}
                  }
                  @if (row.partial) {
                    {{ 'widgets.trend.partial' | transloco }}
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </app-table-scroll>
    } @else {
      <app-bar-list
        data-testid="trend-widget-bars"
        [rows]="bars()"
        [label]="'widgets.trend.title' | transloco"
        shortLabels
        thick
      />
      @if (caption(); as text) {
        <p class="trend__caption">{{ text }}</p>
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .trend__caption {
      margin: 10px 0 0;
      font: 400 0.8125rem / 1.45 var(--app-font-body);
      color: var(--app-muted);
    }
  `,
})
export class TrendWidgetComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  /** The yearly counts, oldest first. */
  readonly years = input.required<YearTotal[]>();
  /** The year that is not over yet, or null. */
  readonly partialYear = input<number | null>(null);

  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly rows = computed(() => trendRows(this.years(), this.partialYear()));
  protected readonly peak = computed(() => peakYear(this.years()));
  protected readonly bars = computed<BarRow[]>(() => {
    this.language.current();
    const running = this.transloco.translate('widgets.trend.running');
    const last = this.rows().at(-1)?.year;
    return this.rows().map((row) => ({
      key: row.year,
      label: String(row.year),
      value: row.total,
      current: row.year === last,
      running: row.partial ? running : undefined,
    }));
  });
  protected readonly caption = computed(() => {
    this.language.current();
    const peak = this.rows().find((row) => row.peak);
    if (!peak || this.rows().length < 2) {
      return '';
    }
    const key = peak.partial ? 'widgets.trend.peakRunning' : 'widgets.trend.peakYear';
    return this.transloco.translate(key, { year: peak.year, count: peak.total });
  });
}
