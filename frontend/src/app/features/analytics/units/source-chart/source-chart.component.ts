import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../../core/i18n/language.service';
import { ChartComponent } from '../../../../shared/charts/chart.component';
import {
  MAX_BARS,
  barChartHeight,
  horizontalBarOption,
} from '../../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../../shared/charts/chart-theme.service';
import { UnitAnalytics } from '../unit-analytics.service';

const LABEL_WIDTH = 160;

/** Approved awards per awarding organisation, the largest 12 as bars and the rest in one "Other" bar. */
@Component({
  selector: 'app-source-chart',
  imports: [ChartComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './source-chart.component.html',
})
export class SourceChartComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly bySource = input.required<UnitAnalytics['bySource']>();

  protected readonly bars = computed(() => {
    this.language.current();
    const { sources, otherCount } = this.bySource();
    const bars = sources.slice(0, MAX_BARS).map((source) => ({
      label: source.name,
      value: source.count,
    }));
    const rest =
      sources.slice(MAX_BARS).reduce((sum, source) => sum + source.count, 0) + otherCount;
    if (rest > 0) {
      bars.push({ label: this.transloco.translate('unitAnalytics.other'), value: rest });
    }
    return bars;
  });
  protected readonly height = computed(() => barChartHeight(this.bars().length));
  protected readonly description = computed(() => {
    this.language.current();
    const top = this.bySource().sources[0];
    return this.transloco.translate('unitAnalytics.sources.description', {
      count: this.bySource().sources.length,
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
