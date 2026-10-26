import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { combineLatest } from 'rxjs';

import { LanguageService } from '../../../../core/i18n/language.service';
import { RECOGNITION_LEVELS, categoryName } from '../../../awards/awards.service';
import { readPeriod } from '../../analytics-period';
import { AnalyticsPeriod } from '../../analytics.service';
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
import { UnitComparisonComponent } from '../unit-comparison/unit-comparison.component';
import { UnitPickerComponent } from '../unit-picker/unit-picker.component';
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
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatProgressBar,
    PeriodSelectComponent,
    RouterLink,
    SourceChartComponent,
    TopPerformersComponent,
    TranslocoPipe,
    UnitComparisonComponent,
    UnitPickerComponent,
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

  readonly units = this.store.selectSignal(unitAnalyticsFeature.selectUnits);
  readonly unitId = this.store.selectSignal(unitAnalyticsFeature.selectUnitId);
  readonly period = this.store.selectSignal(unitAnalyticsFeature.selectPeriod);
  readonly analytics = this.store.selectSignal(unitAnalyticsFeature.selectAnalytics);
  readonly loading = this.store.selectSignal(unitAnalyticsFeature.selectLoading);
  readonly problem = this.store.selectSignal(unitAnalyticsFeature.selectProblem);
  readonly unitsStatus = this.store.selectSignal(unitAnalyticsFeature.selectUnitsStatus);
  readonly invalid = signal(false);
  readonly unknownId = signal(false);

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

  protected chooseUnit(unitId: number): void {
    void this.router.navigate(['/analytics/units', unitId], {
      queryParamsHandling: 'preserve',
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
