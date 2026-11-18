import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../../core/auth/auth.service';
import { canCreateAwards } from '../../../core/auth/permissions';
import { DashboardLayoutComponent } from '../../../shared/dashboard/dashboard-layout.component';
import { DashboardWidgetDirective } from '../../../shared/dashboard/dashboard-widget.directive';
import { IconComponent } from '../../../shared/icons/icon.component';
import { MIN_STREAK, currentKyivMonth, levelSteps, streak } from '../../../shared/widgets/facts';
import { KpiWidgetComponent } from '../../../shared/widgets/kpi-widget.component';
import { LevelStairsWidgetComponent } from '../../../shared/widgets/level-stairs-widget.component';
import { readPeriod } from '../analytics-period';
import { AnalyticsPeriod } from '../analytics.service';
import { CategoryChartComponent } from '../category-chart/category-chart.component';
import { CvExportComponent } from '../cv-export/cv-export.component';
import { DashboardEmptyComponent } from '../dashboard-empty/dashboard-empty.component';
import { InProgressComponent } from '../in-progress/in-progress.component';
import { PeriodSelectComponent } from '../period-select/period-select.component';
import { RankCardComponent } from '../rank-card/rank-card.component';
import { AnalyticsActions } from '../store/analytics.actions';
import { analyticsFeature } from '../store/analytics.feature';
import { SummaryTilesComponent } from '../summary-tiles/summary-tiles.component';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';
import { VisibilityCountsComponent } from '../visibility-counts/visibility-counts.component';
import { YearTrendChartComponent } from '../year-trend-chart/year-trend-chart.component';

/** The personal dashboard: period choice, the widgets in the account's layout, the CV export, guidance when nothing counts. */
@Component({
  selector: 'app-dashboard',
  imports: [
    CategoryChartComponent,
    CvExportComponent,
    DashboardEmptyComponent,
    DashboardLayoutComponent,
    DashboardWidgetDirective,
    InProgressComponent,
    KpiWidgetComponent,
    LevelStairsWidgetComponent,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    IconComponent,
    MatProgressBar,
    PeriodSelectComponent,
    RankCardComponent,
    RouterLink,
    SummaryTilesComponent,
    TimelineChartComponent,
    TranslocoPipe,
    VisibilityCountsComponent,
    YearTrendChartComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly canCreate = canCreateAwards(inject(AuthService).permissions());

  readonly period = this.store.selectSignal(analyticsFeature.selectPeriod);
  readonly analytics = this.store.selectSignal(analyticsFeature.selectAnalytics);
  readonly loading = this.store.selectSignal(analyticsFeature.selectLoading);
  readonly problem = this.store.selectSignal(analyticsFeature.selectProblem);
  readonly invalid = signal(false);

  protected readonly chosen = computed(
    () => this.period().from !== null || this.period().to !== null,
  );
  protected readonly empty = computed(() => this.analytics()?.totals.approved === 0);
  private readonly today = currentKyivMonth();
  protected readonly thisYear = computed(
    () => this.analytics()?.byYear.find((row) => row.year === this.today.year)?.total ?? 0,
  );
  protected readonly yearTotals = computed(() =>
    (this.analytics()?.byYear ?? []).map((row) => row.total),
  );
  /** The first year of an unbroken run of years with awards, when the run is long enough to mention. */
  protected readonly streakSince = computed(() => {
    const found = streak(this.analytics()?.byYear ?? [], this.today);
    return found.years >= MIN_STREAK ? found.since : null;
  });
  protected readonly steps = computed(() => levelSteps(this.analytics()?.byLevel ?? []));

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const period = readPeriod(params);
      this.invalid.set(period === null);
      if (period) {
        this.store.dispatch(AnalyticsActions.periodSelected({ period }));
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
    this.store.dispatch(AnalyticsActions.periodSelected({ period: this.period() }));
  }
}
