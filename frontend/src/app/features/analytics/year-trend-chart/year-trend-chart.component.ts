import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { ChartComponent } from '../../../shared/charts/chart.component';
import { categoryAxis, tooltip, valueAxis } from '../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { LEVEL_BANDS, LevelBand, YearCount, levelBand } from '../analytics.service';

export interface YearRow {
  year: number;
  total: number;
  bands: Record<LevelBand, number>;
}

/** Years to show: every year of the period, starting at the first non-zero one unless the start was chosen. */
export function yearRows(byYear: YearCount[], fromChosen: boolean): YearRow[] {
  const first = fromChosen ? 0 : byYear.findIndex((year) => year.total > 0);
  return byYear.slice(Math.max(first, 0)).map((year) => {
    const bands = Object.fromEntries(LEVEL_BANDS.map((band) => [band, 0])) as Record<
      LevelBand,
      number
    >;
    for (const [level, count] of Object.entries(year.byLevel)) {
      bands[levelBand(level as never)] += count ?? 0;
    }
    return { year: year.year, total: year.total, bands };
  });
}

/** Approved awards per year, stacked by level band. */
@Component({
  selector: 'app-year-trend-chart',
  imports: [ChartComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './year-trend-chart.component.html',
})
export class YearTrendChartComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly byYear = input.required<YearCount[]>();
  readonly fromChosen = input(false);

  protected readonly rows = computed(() => yearRows(this.byYear(), this.fromChosen()));
  protected readonly bands = computed(() =>
    LEVEL_BANDS.filter((band) => this.rows().some((row) => row.bands[band] > 0)),
  );
  protected readonly description = computed(() => {
    this.language.current();
    const rows = this.rows();
    return this.transloco.translate('analytics.trend.description', {
      from: rows[0]?.year ?? '',
      to: rows.at(-1)?.year ?? '',
      total: rows.reduce((sum, row) => sum + row.total, 0),
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    this.language.current();
    const palette = this.theme.palette();
    const rows = this.rows();
    return {
      grid: { left: 8, right: 8, top: 40, bottom: 8, containLabel: true },
      legend: { top: 0, textStyle: { color: palette.text } },
      tooltip: tooltip(palette, 'axis'),
      xAxis: categoryAxis(
        palette,
        rows.map((row) => String(row.year)),
      ),
      yAxis: valueAxis(palette),
      series: this.bands().map((band) => ({
        type: 'bar',
        stack: 'levels',
        name: this.transloco.translate(`analytics.bands.${band}`),
        barMaxWidth: 32,
        itemStyle: { color: palette.bands[LEVEL_BANDS.indexOf(band)] },
        data: rows.map((row) => row.bands[band]),
      })),
    };
  });
}
