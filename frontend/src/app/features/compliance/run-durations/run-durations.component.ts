import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../core/i18n/language.service';
import { ChartComponent } from '../../../shared/charts/chart.component';
import { BAR_RADIUS, tooltip, valueAxis } from '../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { kyivDateTime } from '../../../shared/date-format';
import { RetentionRun } from '../compliance.service';
import { runDurations } from './run-durations';
import { TableScrollComponent } from '../../../shared/widgets/table-scroll.component';

const CHART_HEIGHT = 220;

/** The duration in seconds of the newest finished retention runs, a run that failed in the error colour. */
@Component({
  selector: 'app-run-durations',
  imports: [ChartComponent, TableScrollComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-chart
      [option]="option()"
      [description]="description()"
      [height]="height"
      data-testid="run-durations"
    >
      <app-table-scroll [label]="'compliance.retention.durations' | transloco">
        <table class="analytics-table" data-testid="run-durations-table">
          <caption>
            {{
              'compliance.retention.durations' | transloco
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">{{ 'compliance.retention.started' | transloco }}</th>
              <th scope="col">{{ 'compliance.retention.seconds' | transloco }}</th>
              <th scope="col">{{ 'compliance.columns.outcome' | transloco }}</th>
            </tr>
          </thead>
          <tbody>
            @for (bar of bars(); track bar.id) {
              <tr data-testid="run-duration-row" [attr.data-failed]="bar.failed">
                <th scope="row">{{ bar.label }}</th>
                <td>{{ bar.seconds }}</td>
                <td>
                  {{
                    (bar.failed ? 'compliance.retention.failed' : 'compliance.retention.ok')
                      | transloco
                  }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </app-table-scroll>
    </app-chart>
  `,
})
export class RunDurationsComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly runs = input.required<RetentionRun[]>();

  protected readonly height = CHART_HEIGHT;
  protected readonly bars = computed(() => {
    const language = this.language.current();
    return runDurations(this.runs()).map((bar) => ({
      ...bar,
      label: kyivDateTime(bar.startedAt, language),
    }));
  });
  protected readonly description = computed(() => {
    const bars = this.bars();
    return this.transloco.translate('compliance.retention.durationsDescription', {
      count: bars.length,
      longest: Math.max(0, ...bars.map((bar) => bar.seconds)),
      failed: bars.filter((bar) => bar.failed).length,
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    const palette = this.theme.palette();
    const bars = this.bars();
    return {
      grid: { left: 8, right: 8, top: 24, bottom: 8, containLabel: true },
      tooltip: tooltip(palette, 'axis'),
      xAxis: {
        type: 'category',
        data: bars.map((bar) => bar.label),
        axisLabel: { color: palette.mutedText, hideOverlap: true },
        axisLine: { lineStyle: { color: palette.grid } },
        axisTick: { show: false },
      },
      yAxis: valueAxis(palette),
      series: [
        {
          type: 'bar',
          name: this.transloco.translate('compliance.retention.seconds'),
          barMaxWidth: 28,
          label: { show: true, position: 'top', color: palette.text },
          data: bars.map((bar) => ({
            value: bar.seconds,
            itemStyle: {
              color: bar.failed ? palette.error : palette.bar,
              borderRadius: [BAR_RADIUS, BAR_RADIUS, 0, 0],
            },
          })),
        },
      ],
    };
  });
}
