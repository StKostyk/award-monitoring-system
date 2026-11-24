import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { LanguageService } from '../../../../core/i18n/language.service';
import { BentoComponent, BentoTileComponent } from '../../../../shared/layout/bento.component';
import { PageHeaderComponent } from '../../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../../shared/layout/pane.component';
import { RECOGNITION_LEVELS, categoryName } from '../../../awards/awards.service';
import { readPeriod } from '../../analytics-period';
import { AnalyticsPeriod } from '../../analytics.service';
import { PeriodChipsComponent } from '../../period-chips/period-chips.component';
import { PeriodSelectComponent } from '../../period-select/period-select.component';
import {
  ComparisonBarsComponent,
  ComparisonRow,
} from '../../units/comparison-bars/comparison-bars.component';
import { DeltaTilesComponent } from '../../units/delta-tiles/delta-tiles.component';
import { HighlightsComponent } from '../../units/highlights/highlights.component';
import { SourceChartComponent } from '../../units/source-chart/source-chart.component';
import { UnitComparisonRow } from '../../units/unit-analytics.service';
import {
  ComparisonLabels,
  UnitComparisonComponent,
} from '../../units/unit-comparison/unit-comparison.component';
import { BoardReportButtonComponent } from '../board-report-button/board-report-button.component';
import { FacultyBarsComponent } from '../faculty-bars/faculty-bars.component';
import { ProjectionTrendChartComponent } from '../projection-trend-chart/projection-trend-chart.component';
import { UniversityAnalyticsActions } from '../store/university-analytics.actions';
import { universityAnalyticsFeature } from '../store/university-analytics.feature';

const HIGHLIGHT_LIMIT = 4;

const UNIVERSITY_LABELS: ComparisonLabels = {
  caption: 'universityAnalytics.sections.comparison',
  levelRow: 'universityAnalytics.comparison.universityLevel',
};

/** Analytics of the whole university for a period as a bento page (W3 A). */
@Component({
  selector: 'app-university-analytics',
  imports: [
    BentoComponent,
    BentoTileComponent,
    BoardReportButtonComponent,
    ComparisonBarsComponent,
    DeltaTilesComponent,
    FacultyBarsComponent,
    HighlightsComponent,
    MatButton,
    MatProgressBar,
    PageHeaderComponent,
    PaneComponent,
    PeriodChipsComponent,
    PeriodSelectComponent,
    ProjectionTrendChartComponent,
    SourceChartComponent,
    TranslocoPipe,
    UnitComparisonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './university-analytics.component.html',
  styleUrl: './university-analytics.component.scss',
})
export class UniversityAnalyticsComponent {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly period = this.store.selectSignal(universityAnalyticsFeature.selectPeriod);
  readonly analytics = this.store.selectSignal(universityAnalyticsFeature.selectAnalytics);
  readonly loading = this.store.selectSignal(universityAnalyticsFeature.selectLoading);
  readonly problem = this.store.selectSignal(universityAnalyticsFeature.selectProblem);
  readonly invalid = signal(false);

  protected readonly labels = UNIVERSITY_LABELS;
  protected readonly linkable = isFaculty;
  protected readonly highlightLimit = HIGHLIGHT_LIMIT;
  protected readonly yearCounts = computed(
    () => this.analytics()?.byYear.map((entry) => entry.total) ?? [],
  );
  protected readonly reportDisabled = computed(
    () => this.invalid() || this.loading() || this.problem() !== null || !this.analytics(),
  );
  protected readonly shownPeriod = computed<AnalyticsPeriod>(
    () => this.analytics()?.period ?? this.period(),
  );
  protected readonly comparedWith = computed(() => {
    this.language.current();
    const data = this.analytics();
    if (!data) {
      return '';
    }
    return this.transloco.translate('unitAnalytics.comparedWith', {
      period: years(data.period),
      previous: years(data.previousPeriod),
    });
  });
  protected readonly byCategory = computed<ComparisonRow[]>(() => {
    const language = this.language.current();
    return [...(this.analytics()?.byCategory ?? [])]
      .sort((a, b) => b.count - a.count)
      .map((entry) => ({
        key: String(entry.category.id),
        label: categoryName(entry.category, language),
        current: entry.count,
        previous: entry.previous,
      }));
  });
  protected readonly byLevel = computed<ComparisonRow[]>(() => {
    this.language.current();
    const entries = this.analytics()?.byLevel ?? [];
    return [...RECOGNITION_LEVELS]
      .reverse()
      .map((level) => entries.find((entry) => entry.level === level))
      .filter((entry) => entry !== undefined)
      .map((entry) => ({
        key: entry.level,
        label: this.transloco.translate(`categories.levels.${entry.level}`),
        current: entry.count,
        previous: entry.previous,
      }));
  });

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((query) => {
      const period = readPeriod(query);
      this.invalid.set(period === null);
      if (period) {
        this.store.dispatch(UniversityAnalyticsActions.periodSelected({ period }));
      }
    });
  }

  protected choose(change: Partial<AnalyticsPeriod>): void {
    const period = { ...this.period(), ...change };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { from: period.from, to: period.to },
    });
  }

  protected allYears(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected retry(): void {
    this.store.dispatch(UniversityAnalyticsActions.periodSelected({ period: this.period() }));
  }
}

function isFaculty(row: UnitComparisonRow): boolean {
  return !row.facultyLevel && row.unit.type === 'FACULTY';
}

function years(period: { from: number; to: number }): string {
  return period.from === period.to ? String(period.from) : `${period.from}–${period.to}`;
}
