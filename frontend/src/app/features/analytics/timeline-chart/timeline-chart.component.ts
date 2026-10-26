import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ECElementEvent, EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { ChartComponent } from '../../../shared/charts/chart.component';
import { categoryAxis, escapeHtml, tooltip } from '../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { kyivDate } from '../../../shared/date-format';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { RECOGNITION_LEVELS, awardTitle, categoryName } from '../../awards/awards.service';
import { TimelineEntry } from '../analytics.service';

const MARKER_SIZE = 12;
const AXIS_PADDING_MS = 60 * 24 * 60 * 60 * 1000;

/** Every counted award on a time axis by level; a point or a table row opens the award. */
@Component({
  selector: 'app-timeline-chart',
  imports: [ChartComponent, KyivDatePipe, MatIcon, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './timeline-chart.component.html',
})
export class TimelineChartComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly router = inject(Router);

  readonly timeline = input.required<TimelineEntry[]>();
  readonly truncated = input(false);

  protected readonly rows = computed(() => {
    const language = this.language.current();
    return this.timeline().map((entry) => ({
      ...entry,
      shownTitle: awardTitle(entry, language),
      categoryName: categoryName(entry.category, language),
    }));
  });
  protected readonly levels = computed(() =>
    RECOGNITION_LEVELS.filter((level) =>
      this.timeline().some((entry) => entry.category.level === level),
    ),
  );
  protected readonly description = computed(() => {
    this.language.current();
    const rows = this.rows();
    return this.transloco.translate('analytics.timeline.description', {
      count: rows.length,
      latest: rows[0]?.shownTitle ?? '',
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    const language = this.language.current();
    const palette = this.theme.palette();
    const levels = this.levels();
    const rows = this.rows();
    return {
      grid: { left: 8, right: 16, top: 8, bottom: 8, containLabel: true },
      tooltip: {
        ...tooltip(palette, 'item'),
        formatter: (params: { dataIndex: number }) => {
          const row = rows[params.dataIndex];
          return row
            ? `<strong>${escapeHtml(row.shownTitle)}</strong><br>${kyivDate(row.awardDate, language)}` +
                `<br>${escapeHtml(row.awardingOrganization)}`
            : '';
        },
      },
      xAxis: {
        type: 'time',
        min: (extent: { min: number }) => extent.min - AXIS_PADDING_MS,
        max: (extent: { max: number }) => extent.max + AXIS_PADDING_MS,
        axisLabel: { color: palette.mutedText, hideOverlap: true },
        splitLine: { show: false },
        axisLine: { lineStyle: { color: palette.grid } },
      },
      yAxis: categoryAxis(
        palette,
        levels.map((level) => this.transloco.translate(`categories.levels.${level}`)),
      ),
      series: [
        {
          type: 'scatter',
          name: this.transloco.translate('analytics.timeline.title'),
          symbolSize: MARKER_SIZE,
          cursor: 'pointer',
          itemStyle: { color: palette.bar, opacity: 1 },
          data: rows.map((row) => [row.awardDate, levels.indexOf(row.category.level)]),
        },
      ],
    };
  });

  protected open(event: ECElementEvent): void {
    const row = this.rows()[event.dataIndex];
    if (row) {
      void this.router.navigate(['/awards', row.awardId]);
    }
  }
}
