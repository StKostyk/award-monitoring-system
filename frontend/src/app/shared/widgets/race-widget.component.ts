import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../core/i18n/language.service';
import { ChartComponent } from '../charts/chart.component';
import { categoryAxis, tooltip, valueAxis } from '../charts/chart-style';
import { ChartThemeService } from '../charts/chart-theme.service';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { crossing, cumulative, lastMonthOf } from './facts';
import { CalendarMonth, MonthCount } from './widget-data';

const MONTHS = 12;

/** The short names of the twelve months in the given language. */
export function monthNames(language: string): string[] {
  const format = new Intl.DateTimeFormat(language, { month: 'short', timeZone: 'UTC' });
  return Array.from({ length: MONTHS }, (_, index) =>
    format.format(new Date(Date.UTC(2000, index, 1))),
  );
}

/** T2: this year and last year as running totals by month, the month the lead changed annotated. */
@Component({
  selector: 'app-race-widget',
  imports: [ChartComponent, EmptyStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (hasData()) {
      <app-chart [option]="option()" [description]="description()" label="widgets.data">
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
                <td>{{ thisYear()[$index] ?? '' }}</td>
                <td>{{ lastYear()[$index] }}</td>
              </tr>
            }
          </tbody>
        </table>
      </app-chart>
    } @else {
      <app-empty-state icon="chart-line" [text]="'widgets.empty' | transloco" />
    }
  `,
})
export class RaceWidgetComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** The monthly counts of the year and the year before. */
  readonly byMonth = input.required<MonthCount[]>();
  /** The year to race against the one before. */
  readonly year = input.required<number>();
  /** The current month in Kyiv. */
  readonly today = input.required<CalendarMonth>();

  protected readonly thisYear = computed(() =>
    cumulative(this.byMonth(), this.year(), lastMonthOf(this.year(), this.today())),
  );
  protected readonly lastYear = computed(() => cumulative(this.byMonth(), this.year() - 1));
  protected readonly crossing = computed(() => crossing(this.thisYear(), this.lastYear()));
  protected readonly months = computed(() => monthNames(this.language.current()));
  protected readonly hasData = computed(
    () => (this.thisYear().at(-1) ?? 0) + (this.lastYear().at(-1) ?? 0) > 0,
  );
  protected readonly description = computed(() => {
    this.language.current();
    const found = this.crossing();
    const months = this.thisYear().length;
    const base = this.transloco.translate('widgets.race.description', {
      year: this.year(),
      previous: this.year() - 1,
      current: this.thisYear().at(-1) ?? 0,
      previousCount: this.lastYear()[months - 1] ?? 0,
    });
    if (!found) {
      return base;
    }
    const turn = this.transloco.translate(`widgets.race.${found.kind}`);
    return `${base} ${turn}: ${this.months()[found.month - 1]}.`;
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    const palette = this.theme.palette();
    const found = this.crossing();
    const label = found ? this.transloco.translate(`widgets.race.${found.kind}`) : '';
    return {
      grid: { left: 8, right: 8, top: 40, bottom: 8, containLabel: true },
      legend: { top: 0, textStyle: { color: palette.text } },
      tooltip: tooltip(palette, 'axis'),
      xAxis: { ...categoryAxis(palette, this.months()), boundaryGap: false },
      yAxis: valueAxis(palette),
      series: [
        {
          type: 'line',
          name: String(this.year() - 1),
          data: this.lastYear(),
          showSymbol: false,
          lineStyle: { color: palette.mutedText, type: 'dashed', width: 2 },
          itemStyle: { color: palette.mutedText },
        },
        {
          type: 'line',
          name: String(this.year()),
          data: this.thisYear(),
          showSymbol: false,
          lineStyle: { color: palette.series[0], width: 3 },
          itemStyle: { color: palette.series[0] },
          markPoint: found
            ? {
                symbol: 'pin',
                symbolSize: 40,
                itemStyle: { color: palette.series[1] },
                label: { color: palette.text, position: 'top', formatter: label },
                data: [
                  {
                    name: label,
                    coord: [found.month - 1, this.thisYear()[found.month - 1]],
                  },
                ],
              }
            : undefined,
        },
      ],
    };
  });
}
