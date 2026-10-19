import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ECElementEvent, EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';

import { ChartThemeService } from './chart-theme.service';

/** SVG renderer, so the chart stays crisp in print and its marks can be inspected. */
export const CHART_INIT = { renderer: 'svg' };

/**
 * An ECharts chart with an ARIA description, theme text colours, no animation under reduced motion and a
 * "Show as table" switch that replaces the chart with the projected table.
 */
@Component({
  selector: 'app-chart',
  imports: [MatSlideToggle, NgxEchartsDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-slide-toggle
      class="chart__switch"
      [checked]="asTable()"
      (change)="asTable.set($event.checked)"
      data-testid="chart-table-switch"
    >
      {{ 'charts.showTable' | transloco }}
    </mat-slide-toggle>
    @if (asTable()) {
      <div class="chart__table" data-testid="chart-table"><ng-content /></div>
    } @else {
      <div
        class="chart__canvas"
        echarts
        role="img"
        [attr.aria-label]="description()"
        [options]="themed()"
        [initOpts]="init"
        [style.height.px]="height()"
        (chartClick)="pointClicked.emit($event)"
        data-testid="chart-canvas"
      ></div>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .chart__switch {
      display: block;
      margin-bottom: 8px;
    }
    .chart__canvas {
      width: 100%;
    }
    .chart__table {
      overflow-x: auto;
    }
  `,
})
export class ChartComponent {
  private readonly theme = inject(ChartThemeService);

  readonly option = input.required<EChartsCoreOption>();
  readonly description = input.required<string>();
  readonly height = input(280);
  readonly pointClicked = output<ECElementEvent>();

  protected readonly asTable = signal(false);
  protected readonly init = CHART_INIT;
  protected readonly themed = computed<EChartsCoreOption>(() => {
    const palette = this.theme.palette();
    return {
      backgroundColor: 'transparent',
      animation: !this.theme.reducedMotion(),
      textStyle: { color: palette.text, fontFamily: palette.fontFamily || 'sans-serif' },
      aria: { enabled: true, label: { description: this.description() } },
      ...this.option(),
    };
  });
}
