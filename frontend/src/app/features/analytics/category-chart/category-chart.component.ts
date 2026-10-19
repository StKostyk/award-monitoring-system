import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { ChartComponent } from '../../../shared/charts/chart.component';
import { BAR_RADIUS, categoryAxis, tooltip, valueAxis } from '../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { CategoryRef, categoryName } from '../../awards/awards.service';

const ROW_HEIGHT = 32;
const CHART_PADDING = 48;
const LABEL_WIDTH = 140;

/** Approved awards per category as horizontal bars, the highest count on top. */
@Component({
  selector: 'app-category-chart',
  imports: [ChartComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-chart.component.html',
})
export class CategoryChartComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly byCategory = input.required<{ category: CategoryRef; count: number }[]>();

  protected readonly rows = computed(() => {
    const language = this.language.current();
    return this.byCategory().map((entry) => ({
      id: entry.category.id,
      name: categoryName(entry.category, language),
      level: entry.category.level,
      count: entry.count,
    }));
  });
  protected readonly height = computed(() => this.rows().length * ROW_HEIGHT + CHART_PADDING);
  protected readonly description = computed(() => {
    this.language.current();
    const top = this.rows()[0];
    return this.transloco.translate('analytics.categories.description', {
      count: this.rows().length,
      top: top?.name ?? '',
      topCount: top?.count ?? 0,
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    const palette = this.theme.palette();
    const rows = [...this.rows()].reverse();
    return {
      grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
      tooltip: tooltip(palette, 'item'),
      xAxis: valueAxis(palette),
      yAxis: categoryAxis(
        palette,
        rows.map((row) => row.name),
        LABEL_WIDTH,
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
}
