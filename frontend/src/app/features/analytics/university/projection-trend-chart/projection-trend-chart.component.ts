import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { PaneComponent } from '../../../../shared/layout/pane.component';
import { organizationName } from '../../../../shared/organization-name';
import { YearGridComponent, YearGridRow } from '../../../../shared/widgets/year-grid.component';
import { TrendProjection, UniversityAnalytics } from '../university-analytics.service';

/** A year of the trend table: an approved count, or the estimate of the projected year. */
export interface TrendYear {
  year: number;
  total: number | null;
  projected: number | null;
}

/** A column of the chart: the approved part and the hatched rest of a year still running, as shares of the tallest. */
export interface TrendColumn extends TrendYear {
  approvedShare: number;
  restShare: number;
  running: boolean;
}

/**
 * W3 A: approved awards of the university by year as thick columns with their numbers; the projected rest of the
 * running year hatched on top of its column, and a sentence on the projection. The pane's table view shows the table.
 */
@Component({
  selector: 'app-projection-trend-chart',
  imports: [TranslocoPipe, YearGridComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './projection-trend-chart.component.html',
  styleUrl: './projection-trend-chart.component.scss',
})
export class ProjectionTrendChartComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  readonly analytics = input.required<UniversityAnalytics>();

  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly projection = computed<TrendProjection>(() => this.analytics().projection);
  protected readonly rows = computed<TrendYear[]>(() => {
    const projection = this.projection();
    const projectedYear = projection.status === 'PROJECTED' ? projection.year : null;
    const rows: TrendYear[] = this.analytics().byYear.map((entry) => ({
      year: entry.year,
      total: entry.total,
      projected: entry.year === projectedYear ? projection.estimate : null,
    }));
    if (projectedYear !== null && !rows.some((row) => row.year === projectedYear)) {
      rows.push({ year: projectedYear, total: null, projected: projection.estimate });
    }
    return rows;
  });
  protected readonly years = computed(() => this.analytics().byYear.map((entry) => entry.year));
  protected readonly gridRows = computed<YearGridRow[]>(() => {
    const language = this.language.current();
    return this.analytics().units.map((row, index) => ({
      key: `${row.unit.id}-${index}`,
      label: row.facultyLevel
        ? this.transloco.translate('universityAnalytics.comparison.universityLevel')
        : organizationName(row.unit, language),
      counts: row.byYear,
    }));
  });
  protected readonly projected = computed(
    () => this.rows().find((row) => row.projected !== null) ?? null,
  );
  protected readonly columns = computed<TrendColumn[]>(() => {
    const rows = this.rows();
    const tallest = Math.max(1, ...rows.map((row) => Math.max(row.total ?? 0, row.projected ?? 0)));
    return rows.map((row) => {
      const approved = row.total ?? 0;
      const rest = row.projected === null ? 0 : Math.max(0, row.projected - approved);
      return {
        ...row,
        approvedShare: (approved / tallest) * 100,
        restShare: (rest / tallest) * 100,
        running: row.projected !== null,
      };
    });
  });
  protected readonly hatched = computed(() =>
    this.columns().some((column) => column.restShare > 0),
  );
  protected readonly sentence = computed(() => {
    this.language.current();
    const projection = this.projection();
    if (projection.status !== 'PROJECTED' || !projection.basis) {
      return this.transloco.translate('universityAnalytics.projection.tooFewYears');
    }
    return this.transloco.translate('universityAnalytics.projection.sentence', {
      year: projection.year,
      estimate: projection.estimate,
      from: projection.basis.from,
      to: projection.basis.to,
    });
  });
}
