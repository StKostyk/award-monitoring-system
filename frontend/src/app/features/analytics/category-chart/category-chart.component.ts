import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { ChartComponent } from '../../../shared/charts/chart.component';
import { barChartHeight, horizontalBarOption, withOther } from '../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { CategoryRef, categoryName } from '../../awards/awards.service';

const LABEL_WIDTH = 140;

/** Approved awards per category as horizontal bars, the highest count on top, the rest in one bar. */
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
  protected readonly bars = computed(() => {
    this.language.current();
    const bars = [...this.rows()]
      .sort((a, b) => b.count - a.count)
      .map((row) => ({ label: row.name, value: row.count }));
    return withOther(bars, (rest) => ({
      label: this.transloco.translate('analytics.categories.other'),
      value: rest.reduce((sum, bar) => sum + bar.value, 0),
    }));
  });
  protected readonly height = computed(() => barChartHeight(this.bars().length));
  protected readonly description = computed(() => {
    this.language.current();
    const top = this.rows()[0];
    return this.transloco.translate('analytics.categories.description', {
      count: this.rows().length,
      top: top?.name ?? '',
      topCount: top?.count ?? 0,
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() =>
    horizontalBarOption(
      this.theme.palette(),
      this.bars(),
      this.transloco.translate('analytics.table.count'),
      LABEL_WIDTH,
    ),
  );
}
