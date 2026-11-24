import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';

import { LanguageService } from '../../../../core/i18n/language.service';
import { PageHeaderComponent } from '../../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../../shared/layout/pane.component';
import { SplitComponent } from '../../../../shared/layout/split.component';
import { organizationName } from '../../../../shared/organization-name';
import { StatusPageComponent } from '../../../../shared/status-page/status-page.component';
import { RECOGNITION_LEVELS, categoryName } from '../../../awards/awards.service';
import { readPeriod } from '../../analytics-period';
import { AnalyticsPeriod } from '../../analytics.service';
import { PeriodChipsComponent } from '../../period-chips/period-chips.component';
import { PeriodSelectComponent } from '../../period-select/period-select.component';
import {
  ComparisonBarsComponent,
  ComparisonRow,
} from '../comparison-bars/comparison-bars.component';
import { DeltaTilesComponent } from '../delta-tiles/delta-tiles.component';
import { ExportMenuComponent } from '../export-menu/export-menu.component';
import { HighlightsComponent } from '../highlights/highlights.component';
import { SourceChartComponent } from '../source-chart/source-chart.component';
import { UnitAnalyticsActions } from '../store/unit-analytics.actions';
import { UNIT_NOT_FOUND } from '../store/unit-analytics.effects';
import { unitAnalyticsFeature } from '../store/unit-analytics.feature';
import { TopPerformersComponent } from '../top-performers/top-performers.component';
import { UnitAnalytics, UnitAnalyticsService } from '../unit-analytics.service';
import { UnitComparisonComponent } from '../unit-comparison/unit-comparison.component';
import { UnitListComponent } from '../unit-list/unit-list.component';
import { UnitTrendChartComponent } from '../unit-trend-chart/unit-trend-chart.component';

/** Analytics of one faculty or department for a period, compared with the period before it. */
@Component({
  selector: 'app-unit-analytics',
  imports: [
    ComparisonBarsComponent,
    DeltaTilesComponent,
    ExportMenuComponent,
    HighlightsComponent,
    MatButton,
    MatProgressBar,
    PageHeaderComponent,
    PaneComponent,
    PeriodChipsComponent,
    PeriodSelectComponent,
    RouterLink,
    SourceChartComponent,
    StatusPageComponent,
    TopPerformersComponent,
    TranslocoPipe,
    SplitComponent,
    UnitComparisonComponent,
    UnitListComponent,
    UnitTrendChartComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './unit-analytics.component.html',
  styleUrl: './unit-analytics.component.scss',
})
export class UnitAnalyticsComponent {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly service = inject(UnitAnalyticsService);

  readonly units = this.store.selectSignal(unitAnalyticsFeature.selectUnits);
  readonly unitId = this.store.selectSignal(unitAnalyticsFeature.selectUnitId);
  readonly period = this.store.selectSignal(unitAnalyticsFeature.selectPeriod);
  readonly analytics = this.store.selectSignal(unitAnalyticsFeature.selectAnalytics);
  readonly loading = this.store.selectSignal(unitAnalyticsFeature.selectLoading);
  readonly problem = this.store.selectSignal(unitAnalyticsFeature.selectProblem);
  readonly unitsStatus = this.store.selectSignal(unitAnalyticsFeature.selectUnitsStatus);
  readonly invalid = signal(false);
  readonly unknownId = signal(false);

  private readonly shownUnit = computed(
    () => this.units().find((unit) => unit.id === this.unitId()) ?? this.analytics()?.unit ?? null,
  );
  protected readonly context = computed(() => {
    const unit = this.shownUnit();
    return unit ? organizationName(unit, this.language.current()) : null;
  });
  private readonly facultySource = computed(
    () => {
      const unit = this.shownUnit();
      const parentId = unit?.parentId ?? null;
      if (
        !unit ||
        unit.type === 'FACULTY' ||
        parentId === null ||
        this.invalid() ||
        !this.units().some((candidate) => candidate.id === parentId)
      ) {
        return null;
      }
      return { unitId: parentId, period: this.period() };
    },
    {
      equal: (a, b) =>
        a?.unitId === b?.unitId &&
        a?.period.from === b?.period.from &&
        a?.period.to === b?.period.to,
    },
  );
  private readonly facultyReport = toSignal(
    toObservable(this.facultySource).pipe(
      switchMap((source) =>
        source
          ? this.service.read(source.unitId, source.period).pipe(
              map((report): UnitAnalytics | null => report),
              catchError(() => of(null)),
              startWith(null),
            )
          : of(null),
      ),
    ),
    { initialValue: null },
  );
  protected readonly counts = computed(() => {
    const data = this.analytics();
    const report = data?.unit.type === 'FACULTY' ? data : this.facultyReport();
    if (!report) {
      return data ? new Map([[data.unit.id, data.totals.approved.current]]) : null;
    }
    const counts = new Map(
      report.units
        .filter((row) => !row.facultyLevel)
        .map((row) => [row.unit.id, row.approved.current] as const),
    );
    counts.set(report.unit.id, report.totals.approved.current);
    return counts;
  });
  protected readonly notFound = computed(
    () => this.unknownId() || this.problem() === UNIT_NOT_FOUND,
  );
  protected readonly exportDisabled = computed(
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
  protected readonly hasYears = computed(
    () => this.analytics()?.byYear.some((entry) => entry.total > 0) ?? false,
  );
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
    this.requestUnitsUnlessLoaded();
    combineLatest([this.route.paramMap, this.route.queryParamMap])
      .pipe(takeUntilDestroyed())
      .subscribe(([params, query]) => {
        const unitId = Number(params.get('id'));
        const period = readPeriod(query);
        this.unknownId.set(!Number.isSafeInteger(unitId) || unitId <= 0);
        this.invalid.set(period === null);
        if (period && !this.unknownId()) {
          this.store.dispatch(UnitAnalyticsActions.unitSelected({ unitId, period }));
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
    this.requestUnitsUnlessLoaded();
    const unitId = this.unitId();
    if (unitId !== null) {
      this.store.dispatch(UnitAnalyticsActions.unitSelected({ unitId, period: this.period() }));
    }
  }

  private requestUnitsUnlessLoaded(): void {
    const status = this.unitsStatus();
    if (status === 'idle' || status === 'failed') {
      this.store.dispatch(UnitAnalyticsActions.unitsRequested());
    }
  }
}

function years(period: { from: number; to: number }): string {
  return period.from === period.to ? String(period.from) : `${period.from}–${period.to}`;
}
