import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { DeltaView, deltaView } from '../../features/analytics/units/delta';
import { monthNames } from '../date-format';
import { CountUpDirective } from '../effects/count-up.directive';
import { YearToDate } from './facts';

const SPARK_WIDTH = 100;
const SPARK_HEIGHT = 32;
const SPARK_MARGIN = 2;
const ARROWS = { up: '↑', down: '↓', same: '→' };

/** The points of a sparkline of the values in a box of 100 by 32. */
export function sparkPoints(values: number[]): string {
  const max = Math.max(...values, 1);
  const step = values.length > 1 ? SPARK_WIDTH / (values.length - 1) : 0;
  return values
    .map((value, index) => {
      const y = SPARK_HEIGHT - SPARK_MARGIN - (value / max) * (SPARK_HEIGHT - 2 * SPARK_MARGIN);
      return `${Math.round(index * step * 10) / 10},${Math.round(y * 10) / 10}`;
    })
    .join(' ');
}

/** The months from January to the given one as a range of full names, e.g. "January–October". */
export function monthSpan(language: string, months: number): string {
  const names = monthNames(language, 'long');
  return months > 1 ? `${names[0]}–${names[months - 1]}` : names[0];
}

/** K1: one number counting up, a sparkline of the years and the change against the same months of last year. */
@Component({
  selector: 'app-kpi-widget',
  imports: [CountUpDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="kpi__label">{{ label() }}</p>
    <p class="kpi__value" [appCountUp]="value()" data-testid="kpi-value">0</p>
    @if (years().length > 1) {
      <svg
        class="kpi__spark"
        viewBox="0 0 100 32"
        preserveAspectRatio="none"
        aria-hidden="true"
        data-testid="kpi-spark"
      >
        <polyline [attr.points]="points()" />
      </svg>
    }
    @if (delta(); as ytd) {
      <p class="kpi__delta" data-testid="kpi-delta">
        @if (change(); as view) {
          <span class="kpi__change" [attr.data-direction]="view.direction">
            <span aria-hidden="true">{{ arrow() }} {{ percent() }}</span>
            <span class="cdk-visually-hidden">{{
              'widgets.kpi.' + view.direction | transloco: { percent: percent() }
            }}</span>
          </span>
        }
        <span class="kpi__current">{{
          'widgets.kpi.monthsSoFar'
            | transloco: { count: ytd.current, months: span(), year: ytd.year }
        }}</span>
        <span class="kpi__previous">{{
          'widgets.kpi.samePeriod' | transloco: { count: ytd.previous, year: ytd.year - 1 }
        }}</span>
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .kpi__label {
      margin: 0;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .kpi__value {
      margin: 4px 0;
      font: var(--mat-sys-display-small);
      font-variant-numeric: tabular-nums;
    }

    .kpi__spark {
      display: block;
      width: 100%;
      height: 32px;

      polyline {
        fill: none;
        stroke: var(--app-chart-bar);
        stroke-width: 2;
        vector-effect: non-scaling-stroke;
      }
    }

    .kpi__delta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin: 8px 0 0;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .kpi__change {
      font-weight: 600;
      color: var(--mat-sys-on-surface);
    }
  `,
})
export class KpiWidgetComponent {
  /** The translated name of the number. */
  readonly label = input.required<string>();
  /** The number. */
  readonly value = input.required<number>();
  /** The yearly counts the sparkline draws, oldest first. */
  readonly years = input<number[]>([]);
  /** The year to date against the same months of last year, or null to leave the change out. */
  readonly delta = input<YearToDate | null>(null);

  private readonly language = inject(LanguageService);

  protected readonly points = computed(() => sparkPoints(this.years()));
  protected readonly span = computed(() => {
    const ytd = this.delta();
    return ytd ? monthSpan(this.language.current(), ytd.months) : '';
  });
  protected readonly change = computed<DeltaView | null>(() => {
    const ytd = this.delta();
    return ytd && ytd.deltaPercent !== null ? deltaView(ytd) : null;
  });
  protected readonly percent = computed(() => this.change()?.percent?.replace(/^[+−]/, '') ?? '');
  protected readonly arrow = computed(() => ARROWS[this.change()?.direction ?? 'same']);
}
