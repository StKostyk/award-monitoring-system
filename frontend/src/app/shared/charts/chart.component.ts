import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ECElementEvent, EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';

import { PaneComponent } from '../layout/pane.component';
import { ChartThemeService } from './chart-theme.service';

/** SVG renderer, so the chart stays crisp in print and its marks can be inspected. */
export const CHART_INIT = { renderer: 'svg' };

/**
 * An ECharts chart with an ARIA description, theme text colours and no animation under reduced motion. Inside a
 * pane with a heading it follows the pane's «Chart / Table» control; elsewhere a "Show as table" switch above it
 * replaces the chart with the projected table.
 */
@Component({
  selector: 'app-chart',
  imports: [MatSlideToggle, NgxEchartsDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!inPane()) {
      <mat-slide-toggle
        class="chart__switch"
        [checked]="ownTable()"
        (change)="ownTable.set($event.checked)"
        data-testid="chart-table-switch"
      >
        {{ label() | transloco }}
      </mat-slide-toggle>
    }
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
  private readonly pane = inject(PaneComponent, { optional: true });
  /** Whether the enclosing pane has a heading to hold the «Chart / Table» control. */
  protected readonly inPane = computed(() => !!this.pane?.heading());

  readonly option = input.required<EChartsCoreOption>();
  readonly description = input.required<string>();
  readonly height = input(280);
  /** The translation key of the switch label. */
  readonly label = input('charts.showTable');
  readonly pointClicked = output<ECElementEvent>();

  protected readonly ownTable = signal(false);
  protected readonly asTable = computed(() =>
    this.inPane() ? this.pane?.view() === 'table' : this.ownTable(),
  );
  protected readonly init = CHART_INIT;
  protected readonly themed = computed<EChartsCoreOption>(() => {
    const palette = this.theme.palette();
    return {
      backgroundColor: 'transparent',
      animation: !this.theme.reducedMotion(),
      color: palette.series,
      textStyle: { color: palette.text, fontFamily: palette.fontFamily || 'sans-serif' },
      aria: { enabled: true, label: { description: this.description() } },
      ...this.option(),
    };
  });

  constructor() {
    if (this.pane) {
      inject(DestroyRef).onDestroy(this.pane.holdChart());
    }
  }
}
