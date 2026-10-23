import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { ChartComponent } from '../../../shared/charts/chart.component';
import { barChartHeight, horizontalBarOption } from '../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { RecognitionLevel } from '../../awards/awards.service';

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

  protected readonly height = computed(() => barChartHeight(this.byLevel().length));
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
    return horizontalBarOption(
      this.theme.palette(),
      this.byLevel().map((entry) => ({ label: this.levelName(entry.level), value: entry.count })),
      this.transloco.translate('analytics.table.count'),
    );
  });

  private levelName(level: RecognitionLevel): string {
    return this.transloco.translate(`categories.levels.${level}`);
  }
}
