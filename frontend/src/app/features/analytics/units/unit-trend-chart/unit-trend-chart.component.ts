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
import { PaneComponent } from '../../../../shared/layout/pane.component';
import { organizationName } from '../../../../shared/organization-name';
import { YearGridComponent, YearGridRow } from '../../../../shared/widgets/year-grid.component';
import { UnitAnalytics } from '../unit-analytics.service';

const PERCENT = 100;
const MIN_COLUMN_PERCENT = 6;

/** One column of a small chart: a year and its count. */
export interface YearColumn {
  year: number;
  count: number;
  height: number;
  current: boolean;
}

/** A small chart of the faculty trend: one department, the faculty level, or the department itself. */
export interface TrendLine {
  id: number;
  label: string;
  counts: number[];
  total: number;
  columns: YearColumn[];
}

/** W2 A: approved awards by year as one small column chart per department, each on its own scale. */
@Component({
  selector: 'app-unit-trend-chart',
  imports: [TranslocoPipe, YearGridComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './unit-trend-chart.component.html',
  styleUrl: './unit-trend-chart.component.scss',
})
export class UnitTrendChartComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  readonly analytics = input.required<UnitAnalytics>();

  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly faculty = computed(() => this.analytics().unit.type === 'FACULTY');
  protected readonly years = computed(() => this.analytics().byYear.map((entry) => entry.year));
  protected readonly lines = computed<TrendLine[]>(() => {
    const language = this.language.current();
    const years = this.years();
    if (!this.faculty()) {
      const counts = this.analytics().byYear.map((entry) => entry.total);
      return [
        line(
          this.analytics().unit.id,
          organizationName(this.analytics().unit, language),
          years,
          counts,
        ),
      ];
    }
    return this.analytics().units.map((row) =>
      line(
        row.unit.id,
        row.facultyLevel
          ? this.transloco.translate('unitAnalytics.comparison.facultyLevel')
          : organizationName(row.unit, language),
        years,
        row.byYear,
      ),
    );
  });
  protected readonly rowHeader = computed(() =>
    this.faculty() ? 'unitAnalytics.comparison.unit' : 'widgets.awards',
  );
  protected readonly gridRows = computed<YearGridRow[]>(() => {
    this.language.current();
    if (this.faculty()) {
      return this.lines().map((entry, index) => ({
        key: `${entry.id}-${index}`,
        label: entry.label,
        counts: entry.counts,
      }));
    }
    const byYear = this.analytics().byYear;
    return [
      {
        key: 'personal',
        label: this.transloco.translate('unitAnalytics.trend.personal'),
        counts: byYear.map((entry) => entry.personal),
      },
      {
        key: 'unit',
        label: this.transloco.translate('unitAnalytics.trend.unitAwards'),
        counts: byYear.map((entry) => entry.unitAwards),
      },
    ];
  });
  protected readonly range = computed(() => {
    const years = this.years();
    return years.length > 1 ? `${years[0]}–${years.at(-1)}` : String(years[0] ?? '');
  });

  constructor() {
    const release = this.pane?.holdChart();
    if (release) {
      inject(DestroyRef).onDestroy(release);
    }
  }
}

function line(id: number, label: string, years: number[], counts: number[]): TrendLine {
  const max = Math.max(0, ...counts);
  const last = years.length - 1;
  return {
    id,
    label,
    counts,
    total: counts.reduce((sum, count) => sum + count, 0),
    columns: years.map((year, index) => {
      const count = counts[index] ?? 0;
      return {
        year,
        count,
        height: count > 0 && max > 0 ? Math.max(MIN_COLUMN_PERCENT, (count * PERCENT) / max) : 0,
        current: index === last,
      };
    }),
  };
}
