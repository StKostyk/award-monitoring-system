import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { currentKyivYear } from '../../../shared/date-format';
import { AnalyticsPeriod } from '../analytics.service';

/** Two shortcuts for the period of an analytics page: the current year and the whole period. */
@Component({
  selector: 'app-period-chips',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chips" role="group" [attr.aria-label]="'analytics.period.label' | transloco">
      <button
        type="button"
        class="app-chip"
        [class.app-chip--filled]="thisYear()"
        [attr.aria-pressed]="thisYear()"
        (click)="choose.emit({ from: year, to: year })"
        data-testid="period-chip-year"
      >
        {{ 'analytics.period.thisYear' | transloco }}
      </button>
      <button
        type="button"
        class="app-chip"
        [class.app-chip--filled]="wholePeriod()"
        [attr.aria-pressed]="wholePeriod()"
        (click)="allYears.emit()"
        data-testid="period-chip-all"
      >
        {{ 'analytics.period.wholePeriod' | transloco }}
      </button>
    </div>
  `,
  styles: `
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .app-chip {
      min-height: 40px;
      padding: 8px 14px;
      border-color: var(--app-pane-border);
      border-radius: 999px;
      background: var(--app-pane);
      color: var(--mat-sys-on-surface);
      font: 500 0.8125rem / 1.2 var(--app-font-body);
      cursor: pointer;

      &:hover {
        border-color: var(--mat-sys-outline);
      }

      &:focus-visible {
        outline: 2px solid var(--mat-sys-primary);
        outline-offset: 2px;
      }

      &.app-chip--filled {
        border-color: var(--app-chip-on);
        background: var(--app-chip-on);
        color: var(--app-on-chip-on);
      }
    }
  `,
})
export class PeriodChipsComponent {
  readonly period = input.required<AnalyticsPeriod>();
  readonly choose = output<AnalyticsPeriod>();
  readonly allYears = output();

  protected readonly year = currentKyivYear();
  protected readonly thisYear = computed(
    () => this.period().from === this.year && this.period().to === this.year,
  );
  protected readonly wholePeriod = computed(
    () => this.period().from === null && this.period().to === null,
  );
}
