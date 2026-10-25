import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { LanguageService } from '../../../../core/i18n/language.service';
import { categoryName } from '../../../awards/awards.service';
import { readPeriod } from '../../analytics-period';
import { AnalyticsPeriod, RECOGNITION_LEVELS } from '../../analytics.service';
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
import { CompliancePanelComponent } from '../compliance-panel/compliance-panel.component';
import { ProjectionTrendChartComponent } from '../projection-trend-chart/projection-trend-chart.component';
import { UniversityAnalyticsActions } from '../store/university-analytics.actions';
import { universityAnalyticsFeature } from '../store/university-analytics.feature';
import { TrendListsComponent } from '../trend-lists/trend-lists.component';

const UNIVERSITY_LABELS: ComparisonLabels = {
  caption: 'universityAnalytics.sections.comparison',
  levelRow: 'universityAnalytics.comparison.universityLevel',
  chartDescription: 'universityAnalytics.comparison.chartDescription',
};

/** Analytics of the whole university for a period, with the compliance summary on top. */
@Component({
  selector: 'app-university-analytics',
  imports: [
    ComparisonBarsComponent,
    CompliancePanelComponent,
    DeltaTilesComponent,
    HighlightsComponent,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatProgressBar,
    PeriodSelectComponent,
    ProjectionTrendChartComponent,
    SourceChartComponent,
    TranslocoPipe,
    TrendListsComponent,
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
  readonly compliance = this.store.selectSignal(universityAnalyticsFeature.selectCompliance);
  readonly complianceLoading = this.store.selectSignal(
    universityAnalyticsFeature.selectComplianceLoading,
  );
  readonly complianceProblem = this.store.selectSignal(
    universityAnalyticsFeature.selectComplianceProblem,
  );
  readonly invalid = signal(false);

  protected readonly labels = UNIVERSITY_LABELS;
  protected readonly linkable = isFaculty;
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
    this.store.dispatch(UniversityAnalyticsActions.complianceRequested());
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

  protected refreshCompliance(): void {
    this.store.dispatch(UniversityAnalyticsActions.complianceRequested());
  }
}

function isFaculty(row: UnitComparisonRow): boolean {
  return !row.facultyLevel && row.unit.type === 'FACULTY';
}

function years(period: { from: number; to: number }): string {
  return period.from === period.to ? String(period.from) : `${period.from}–${period.to}`;
}
