import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { withOther } from '../../../../shared/charts/chart-style';
import { PaneComponent } from '../../../../shared/layout/pane.component';

/** One row of a current-against-previous comparison. */
export interface ComparisonRow {
  key: string;
  label: string;
  current: number;
  previous: number;
}

const PERCENT = 100;
const MIN_BAR_PERCENT = 4;

/** W2 A: two thick bars per row, the previous period muted above the current one; beyond 12 rows one "Other" pair. */
@Component({
  selector: 'app-comparison-bars',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './comparison-bars.component.html',
  styleUrl: './comparison-bars.component.scss',
})
export class ComparisonBarsComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  readonly rows = input.required<ComparisonRow[]>();
  readonly caption = input.required<string>();
  readonly labelHeader = input.required<string>();
  readonly tableId = input.required<string>();

  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly bars = computed(() => {
    this.language.current();
    const rows = withOther(this.rows(), (rest) => ({
      key: 'other',
      label: this.transloco.translate('unitAnalytics.other'),
      current: rest.reduce((sum, row) => sum + row.current, 0),
      previous: rest.reduce((sum, row) => sum + row.previous, 0),
    }));
    const max = Math.max(0, ...rows.flatMap((row) => [row.current, row.previous]));
    return rows.map((row) => ({
      ...row,
      currentWidth: width(row.current, max),
      previousWidth: width(row.previous, max),
    }));
  });

  constructor() {
    const release = this.pane?.holdChart();
    if (release) {
      inject(DestroyRef).onDestroy(release);
    }
  }
}

function width(value: number, max: number): number {
  return value > 0 && max > 0 ? Math.max(MIN_BAR_PERCENT, (value * PERCENT) / max) : 0;
}
