import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../core/i18n/language.service';
import { ChartComponent } from '../charts/chart.component';
import { tooltip } from '../charts/chart-style';
import { ChartThemeService } from '../charts/chart-theme.service';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { LabelledCount } from './widget-data';

/** V6: the levels as a treemap, each area as large as its count. */
@Component({
  selector: 'app-level-map-widget',
  imports: [ChartComponent, EmptyStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rows().length > 0) {
      <app-chart [option]="option()" [description]="description()" label="widgets.data">
        <table class="analytics-table" data-testid="level-map-table">
          <caption>
            {{
              'widgets.map.title' | transloco
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">{{ 'widgets.level' | transloco }}</th>
              <th scope="col">{{ 'widgets.awards' | transloco }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.key) {
              <tr>
                <th scope="row">{{ row.key | transloco }}</th>
                <td>{{ row.count }}</td>
              </tr>
            }
          </tbody>
        </table>
      </app-chart>
    } @else {
      <app-empty-state icon="chart-column" [text]="'widgets.empty' | transloco" />
    }
  `,
})
export class LevelMapWidgetComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** The levels with awards, largest first, from `levelAreas()` in `facts.ts`. */
  readonly areas = input.required<LabelledCount[]>();

  protected readonly rows = computed(() => this.areas().filter((area) => area.count > 0));
  protected readonly description = computed(() => {
    this.language.current();
    const largest = this.rows()[0];
    return this.transloco.translate('widgets.map.description', {
      level: largest ? this.transloco.translate(largest.key) : '',
      count: largest?.count ?? 0,
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() => {
    this.language.current();
    const palette = this.theme.palette();
    return {
      tooltip: tooltip(palette, 'item'),
      series: [
        {
          type: 'treemap',
          name: this.transloco.translate('widgets.map.title'),
          roam: false,
          nodeClick: false,
          breadcrumb: { show: false },
          width: '100%',
          height: '100%',
          top: 0,
          left: 0,
          itemStyle: { borderColor: palette.surface, borderWidth: 2, gapWidth: 2 },
          label: { show: true, formatter: '{b}\n{c}' },
          data: this.rows().map((row, index) => ({
            name: this.transloco.translate(row.key),
            value: row.count,
            itemStyle: { color: palette.series[index % palette.series.length] },
          })),
        },
      ],
    };
  });
}
