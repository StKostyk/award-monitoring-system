import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../../core/i18n/language.service';
import { ChartComponent } from '../../../../shared/charts/chart.component';
import { categoryAxis, tooltip, valueAxis } from '../../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../../shared/charts/chart-theme.service';
import { TrendProjection, UniversityAnalytics } from '../university-analytics.service';

const PROJECTION_BAND = 4;

/** A year of the trend table: an approved count, or the estimate of the projected year. */
export interface TrendYear {
  year: number;
  total: number | null;
  projected: number | null;
}

/** Approved awards of the university by year, with the projected year as a dashed segment and a sentence. */
@Component({
  selector: 'app-projection-trend-chart',
  imports: [ChartComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './projection-trend-chart.component.html',
  styleUrl: './projection-trend-chart.component.scss',
})
export class ProjectionTrendChartComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly analytics = input.required<UniversityAnalytics>();

  protected readonly projection = computed<TrendProjection>(() => this.analytics().projection);
  protected readonly rows = computed<TrendYear[]>(() => {
    const projection = this.projection();
    const projectedYear = projection.status === 'PROJECTED' ? projection.year : null;
    const rows: TrendYear[] = this.analytics().byYear.map((entry) => ({
      year: entry.year,
      total: entry.total,
      projected: entry.year === projectedYear ? projection.estimate : null,
    }));
    if (projectedYear !== null && !rows.some((row) => row.year === projectedYear)) {
      rows.push({ year: projectedYear, total: null, projected: projection.estimate });
    }
    return rows;
  });
  protected readonly sentence = computed(() => {
    this.language.current();
    const projection = this.projection();
    if (projection.status !== 'PROJECTED' || !projection.basis) {
      return this.transloco.translate('universityAnalytics.projection.tooFewYears');
    }
    return this.transloco.translate('universityAnalytics.projection.sentence', {
      year: projection.year,
      estimate: projection.estimate,
      from: projection.basis.from,
      to: projection.basis.to,
    });
  });
  protected readonly description = computed(() => {
    this.language.current();
    const byYear = this.analytics().byYear;
    return this.transloco.translate('universityAnalytics.projection.description', {
      from: byYear[0]?.year ?? '',
      to: byYear.at(-1)?.year ?? '',
      total: byYear.reduce((sum, entry) => sum + entry.total, 0),
      projection: this.sentence(),
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    this.language.current();
    const palette = this.theme.palette();
    const rows = this.rows();
    const projection = this.projection();
    const projectionColor = palette.bands[PROJECTION_BAND] ?? palette.bar;
    const series: Record<string, unknown>[] = [
      {
        type: 'line',
        name: this.transloco.translate('universityAnalytics.projection.approved'),
        symbol: 'circle',
        symbolSize: 7,
        itemStyle: { color: palette.bar },
        lineStyle: { color: palette.bar, width: 2 },
        data: rows.map((row) => row.total),
      },
    ];
    if (projection.status === 'PROJECTED' && projection.basis) {
      const start = projection.basis.to;
      series.push({
        type: 'line',
        name: this.transloco.translate('universityAnalytics.projection.projected'),
        symbol: 'diamond',
        symbolSize: 9,
        connectNulls: true,
        itemStyle: { color: projectionColor },
        lineStyle: { color: projectionColor, width: 2, type: 'dashed' },
        data: rows.map((row) => {
          if (row.projected !== null) {
            return row.projected;
          }
          return row.year === start ? row.total : null;
        }),
      });
    }
    return {
      grid: { left: 8, right: 8, top: 48, bottom: 8, containLabel: true },
      legend: { top: 0, textStyle: { color: palette.text } },
      tooltip: tooltip(palette, 'axis'),
      xAxis: categoryAxis(
        palette,
        rows.map((row) => String(row.year)),
      ),
      yAxis: valueAxis(palette),
      series,
    };
  });
}
