import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';

import { currentKyivYear } from '../../../shared/date-format';
import { FIRST_YEAR } from '../analytics-period';
import { AnalyticsPeriod } from '../analytics.service';

/** A from–to pair of years, either side open, and a way back to all years once one is chosen. */
@Component({
  selector: 'app-period-select',
  imports: [MatButton, MatFormField, MatLabel, MatOption, MatSelect, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './period-select.component.html',
  styleUrl: './period-select.component.scss',
})
export class PeriodSelectComponent {
  readonly period = input.required<AnalyticsPeriod>();
  readonly choose = output<Partial<AnalyticsPeriod>>();
  readonly allYears = output();

  protected readonly years = yearsDescending(currentKyivYear());
  protected readonly chosen = computed(
    () => this.period().from !== null || this.period().to !== null,
  );
}

function yearsDescending(last: number): number[] {
  return Array.from({ length: last - FIRST_YEAR + 1 }, (_, index) => last - index);
}
