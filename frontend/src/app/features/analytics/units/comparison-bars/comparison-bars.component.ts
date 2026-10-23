import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../../core/i18n/language.service';
import { ChartComponent } from '../../../../shared/charts/chart.component';
import {
  BAR_RADIUS,
  barChartHeight,
  categoryAxis,
  tooltip,
  valueAxis,
  withOther,
} from '../../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../../shared/charts/chart-theme.service';

/** One row of a current-against-previous comparison. */
export interface ComparisonRow {
  key: string;
  label: string;
  current: number;
  previous: number;
}

const LABEL_WIDTH = 140;
const BAR_GROUP_SCALE = 1.5;
const CURRENT_BAND = 3;
const PREVIOUS_BAND = 0;

/** Counts of the period beside the previous period's as paired horizontal bars, the rest beyond 12 in one "Other" pair. */
@Component({
  selector: 'app-comparison-bars',
  imports: [ChartComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './comparison-bars.component.html',
})
export class ComparisonBarsComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly rows = input.required<ComparisonRow[]>();
  readonly caption = input.required<string>();
  readonly labelHeader = input.required<string>();
  readonly tableId = input.required<string>();

  protected readonly bars = computed(() => {
    this.language.current();
    return withOther(this.rows(), (rest) => ({
      key: 'other',
      label: this.transloco.translate('unitAnalytics.other'),
      current: rest.reduce((sum, row) => sum + row.current, 0),
      previous: rest.reduce((sum, row) => sum + row.previous, 0),
    }));
  });
  protected readonly height = computed(() =>
    Math.round(barChartHeight(this.bars().length) * BAR_GROUP_SCALE),
  );
  protected readonly description = computed(() => {
    this.language.current();
    const top = [...this.rows()].sort((a, b) => b.current - a.current)[0];
    return this.transloco.translate('unitAnalytics.bars.description', {
      caption: this.caption(),
      count: this.rows().length,
      top: top?.label ?? '',
      current: top?.current ?? 0,
      previous: top?.previous ?? 0,
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    this.language.current();
    const palette = this.theme.palette();
    const bottomUp = [...this.bars()].reverse();
    const series = (name: string, color: string, values: number[]) => ({
      type: 'bar',
      name,
      barMaxWidth: 14,
      itemStyle: { color, borderRadius: [0, BAR_RADIUS, BAR_RADIUS, 0] },
      label: { show: true, position: 'right', color: palette.text },
      data: values,
    });
    return {
      grid: { left: 8, right: 24, top: 32, bottom: 8, containLabel: true },
      legend: { top: 0, textStyle: { color: palette.text } },
      tooltip: tooltip(palette, 'axis'),
      xAxis: valueAxis(palette),
      yAxis: categoryAxis(
        palette,
        bottomUp.map((bar) => bar.label),
        LABEL_WIDTH,
      ),
      series: [
        series(
          this.transloco.translate('unitAnalytics.bars.previous'),
          palette.bands[PREVIOUS_BAND],
          bottomUp.map((bar) => bar.previous),
        ),
        series(
          this.transloco.translate('unitAnalytics.bars.current'),
          palette.bands[CURRENT_BAND],
          bottomUp.map((bar) => bar.current),
        ),
      ],
    };
  });
}
