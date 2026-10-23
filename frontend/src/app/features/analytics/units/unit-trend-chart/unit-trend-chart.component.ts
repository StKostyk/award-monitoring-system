import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../../core/i18n/language.service';
import { ChartComponent } from '../../../../shared/charts/chart.component';
import { categoryAxis, tooltip, valueAxis } from '../../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../../shared/charts/chart-theme.service';
import { organizationName } from '../../../../shared/organization-name';
import { UnitAnalytics } from '../unit-analytics.service';

const LINE_TYPES = ['solid', 'dashed', 'dotted'] as const;
const SYMBOLS = ['circle', 'rect', 'triangle', 'diamond'] as const;
const PERSONAL_BAND = 3;
const UNIT_BAND = 0;

/** A line of the faculty trend: one department, or the faculty level. */
export interface TrendLine {
  id: number;
  label: string;
  counts: number[];
}

/** Approved awards by year: one line per department for a faculty, personal and unit awards as bars for a department. */
@Component({
  selector: 'app-unit-trend-chart',
  imports: [ChartComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './unit-trend-chart.component.html',
})
export class UnitTrendChartComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly analytics = input.required<UnitAnalytics>();

  protected readonly faculty = computed(() => this.analytics().unit.type === 'FACULTY');
  protected readonly years = computed(() => this.analytics().byYear.map((entry) => entry.year));
  protected readonly lines = computed<TrendLine[]>(() => {
    const language = this.language.current();
    return this.analytics().units.map((row) => ({
      id: row.unit.id,
      label: row.facultyLevel
        ? this.transloco.translate('unitAnalytics.comparison.facultyLevel')
        : organizationName(row.unit, language),
      counts: row.byYear,
    }));
  });
  protected readonly description = computed(() => {
    this.language.current();
    const byYear = this.analytics().byYear;
    return this.transloco.translate(
      this.faculty() ? 'unitAnalytics.trend.facultyDescription' : 'unitAnalytics.trend.description',
      {
        from: byYear[0]?.year ?? '',
        to: byYear.at(-1)?.year ?? '',
        total: byYear.reduce((sum, entry) => sum + entry.total, 0),
        count: this.lines().length,
      },
    );
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    this.language.current();
    const palette = this.theme.palette();
    const colors = [palette.bar, ...palette.bands];
    const base = {
      grid: { left: 8, right: 8, top: 48, bottom: 8, containLabel: true },
      legend: { type: 'scroll', top: 0, textStyle: { color: palette.text } },
      tooltip: tooltip(palette, 'axis'),
      xAxis: categoryAxis(palette, this.years().map(String)),
      yAxis: valueAxis(palette),
    };
    if (this.faculty()) {
      return {
        ...base,
        series: this.lines().map((line, index) => ({
          type: 'line',
          name: line.label,
          symbol: SYMBOLS[index % SYMBOLS.length],
          symbolSize: 7,
          itemStyle: { color: colors[index % colors.length] },
          lineStyle: {
            color: colors[index % colors.length],
            width: 2,
            type: LINE_TYPES[Math.floor(index / colors.length) % LINE_TYPES.length],
          },
          data: line.counts,
        })),
      };
    }
    const byYear = this.analytics().byYear;
    return {
      ...base,
      series: [
        {
          type: 'bar',
          stack: 'kinds',
          name: this.transloco.translate('unitAnalytics.trend.personal'),
          barMaxWidth: 32,
          itemStyle: { color: palette.bands[PERSONAL_BAND] },
          data: byYear.map((entry) => entry.personal),
        },
        {
          type: 'bar',
          stack: 'kinds',
          name: this.transloco.translate('unitAnalytics.trend.unitAwards'),
          barMaxWidth: 32,
          itemStyle: { color: palette.bands[UNIT_BAND] },
          data: byYear.map((entry) => entry.unitAwards),
        },
      ],
    };
  });
}
