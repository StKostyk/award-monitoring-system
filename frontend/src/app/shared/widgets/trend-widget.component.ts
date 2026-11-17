import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../core/i18n/language.service';
import { ChartComponent } from '../charts/chart.component';
import { BAR_RADIUS, categoryAxis, tooltip, valueAxis } from '../charts/chart-style';
import { ChartThemeService } from '../charts/chart-theme.service';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { peakYear } from './facts';
import { YearTotal } from './widget-data';

const PARTIAL_OPACITY = 0.5;

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

/** T1: awards per year as bars, the peak year and the current partial year marked. */
@Component({
  selector: 'app-trend-widget',
  imports: [ChartComponent, EmptyStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (peak()) {
      <app-chart [option]="option()" [description]="description()" label="widgets.data">
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
      </app-chart>
    } @else {
      <app-empty-state icon="chart-column" [text]="'widgets.empty' | transloco" />
    }
  `,
})
export class TrendWidgetComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** The yearly counts, oldest first. */
  readonly years = input.required<YearTotal[]>();
  /** The year that is not over yet, or null. */
  readonly partialYear = input<number | null>(null);

  protected readonly rows = computed(() => trendRows(this.years(), this.partialYear()));
  protected readonly peak = computed(() => peakYear(this.years()));
  protected readonly description = computed(() => {
    this.language.current();
    const rows = this.rows();
    return this.transloco.translate('widgets.trend.description', {
      from: rows[0]?.year ?? '',
      to: rows.at(-1)?.year ?? '',
      peakYear: this.peak()?.year ?? '',
      peak: this.peak()?.total ?? 0,
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    this.language.current();
    const palette = this.theme.palette();
    const peakLabel = this.transloco.translate('widgets.trend.peak');
    const partialLabel = this.transloco.translate('widgets.trend.partial');
    return {
      grid: { left: 8, right: 8, top: 24, bottom: 8, containLabel: true },
      tooltip: tooltip(palette, 'axis'),
      xAxis: categoryAxis(
        palette,
        this.rows().map((row) => String(row.year)),
      ),
      yAxis: valueAxis(palette),
      series: [
        {
          type: 'bar',
          name: this.transloco.translate('widgets.awards'),
          barMaxWidth: 32,
          data: this.rows().map((row) => ({
            value: row.total,
            itemStyle: {
              color: row.peak ? palette.series[1] : palette.bar,
              opacity: row.partial ? PARTIAL_OPACITY : 1,
              borderRadius: [BAR_RADIUS, BAR_RADIUS, 0, 0],
            },
            label: {
              show: row.peak || row.partial,
              position: 'top',
              color: palette.text,
              formatter: [row.peak ? peakLabel : '', row.partial ? partialLabel : '']
                .filter(Boolean)
                .join(' · '),
            },
          })),
        },
      ],
    };
  });
}
