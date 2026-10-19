import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { ChartComponent } from '../../../shared/charts/chart.component';
import { BAR_RADIUS, categoryAxis, tooltip, valueAxis } from '../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { RecognitionLevel } from '../../awards/awards.service';

const ROW_HEIGHT = 32;
const CHART_PADDING = 48;

/** Approved awards per recognition level as horizontal bars, the highest level on top. */
@Component({
  selector: 'app-level-chart',
  imports: [ChartComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './level-chart.component.html',
})
export class LevelChartComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly byLevel = input.required<{ level: RecognitionLevel; count: number }[]>();

  protected readonly height = computed(() => this.byLevel().length * ROW_HEIGHT + CHART_PADDING);
  protected readonly description = computed(() => {
    this.language.current();
    return this.transloco.translate('analytics.levels.description', {
      list: this.byLevel()
        .map((entry) => `${this.levelName(entry.level)}: ${entry.count}`)
        .join(', '),
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    this.language.current();
    const palette = this.theme.palette();
    const rows = [...this.byLevel()].reverse();
    return {
      grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
      tooltip: tooltip(palette, 'item'),
      xAxis: valueAxis(palette),
      yAxis: categoryAxis(
        palette,
        rows.map((row) => this.levelName(row.level)),
      ),
      series: [
        {
          type: 'bar',
          name: this.transloco.translate('analytics.table.count'),
          barMaxWidth: 20,
          itemStyle: { color: palette.bar, borderRadius: [0, BAR_RADIUS, BAR_RADIUS, 0] },
          label: { show: true, position: 'right', color: palette.text },
          data: rows.map((row) => row.count),
        },
      ],
    };
  });

  private levelName(level: RecognitionLevel): string {
    return this.transloco.translate(`categories.levels.${level}`);
  }
}
