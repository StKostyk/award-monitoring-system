import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { BarListComponent, BarRow } from '../../../shared/widgets/bar-list.component';
import { currentKyivMonth } from '../../../shared/widgets/facts';
import { LEVEL_BANDS, LevelBand, YearCount, levelBand } from '../analytics.service';
import { TableScrollComponent } from '../../../shared/widgets/table-scroll.component';

export interface YearRow {
  year: number;
  total: number;
  bands: Record<LevelBand, number>;
}

/** The three number cards over the year bars. */
export interface YearSummary {
  year: number;
  count: number;
  previousYear: number;
  previousCount: number;
  /** The mean per year of the years before `year`, or null when the period has none. */
  average: number | null;
  averageFrom: number | null;
}

/** Years to show: every year of the period, starting at the first non-zero one unless the start was chosen. */
export function yearRows(byYear: YearCount[], fromChosen: boolean): YearRow[] {
  const first = fromChosen ? 0 : byYear.findIndex((year) => year.total > 0);
  return byYear.slice(Math.max(first, 0)).map((year) => {
    const bands = Object.fromEntries(LEVEL_BANDS.map((band) => [band, 0])) as Record<
      LevelBand,
      number
    >;
    for (const [level, count] of Object.entries(year.byLevel)) {
      bands[levelBand(level as never)] += count ?? 0;
    }
    return { year: year.year, total: year.total, bands };
  });
}

/**
 * The cards of the last year of the period, the year before it and the average of the earlier years.
 *
 * @param rows the years shown, oldest first
 * @return the summary, or null when there are no years
 */
export function yearSummary(rows: YearRow[]): YearSummary | null {
  const last = rows.at(-1);
  if (!last) {
    return null;
  }
  const earlier = rows.slice(0, -1);
  return {
    year: last.year,
    count: last.total,
    previousYear: last.year - 1,
    previousCount: rows.find((row) => row.year === last.year - 1)?.total ?? 0,
    average: earlier.length
      ? earlier.reduce((sum, row) => sum + row.total, 0) / earlier.length
      : null,
    averageFrom: earlier[0]?.year ?? null,
  };
}

/** W1: approved awards per year as three comparison cards over thick bars, newest year on top. */
@Component({
  selector: 'app-year-trend-chart',
  imports: [BarListComponent, TableScrollComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './year-trend-chart.component.html',
  styleUrl: './year-trend-chart.component.scss',
})
export class YearTrendChartComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  readonly byYear = input.required<YearCount[]>();
  readonly fromChosen = input(false);

  protected readonly rows = computed(() => yearRows(this.byYear(), this.fromChosen()));
  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly summary = computed(() => yearSummary(this.rows()));
  protected readonly isNow = computed(() => this.summary()?.year === currentKyivMonth().year);
  protected readonly average = computed(() => {
    const average = this.summary()?.average;
    if (average === null || average === undefined) {
      return '—';
    }
    const locale = this.language.current() === 'en' ? 'en-GB' : 'uk-UA';
    return new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(average);
  });
  protected readonly bars = computed<BarRow[]>(() => {
    const last = this.summary()?.year;
    return [...this.rows()].reverse().map((row) => ({
      key: row.year,
      label: String(row.year),
      value: row.total,
      current: row.year === last,
    }));
  });
  protected readonly barsLabel = computed(() => {
    this.language.current();
    return this.transloco.translate('analytics.trend.bars');
  });

  constructor() {
    const release = this.pane?.holdChart();
    if (release) {
      inject(DestroyRef).onDestroy(release);
    }
  }
}
