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
      min-height: 44px;
      background: transparent;
      cursor: pointer;

      &:hover {
        background: var(--mat-sys-surface-container-high);
      }

      &:focus-visible {
        outline: 2px solid var(--mat-sys-primary);
        outline-offset: 2px;
      }

      &.app-chip--filled {
        background: var(--mat-sys-secondary-container);
        color: var(--mat-sys-on-secondary-container);
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
