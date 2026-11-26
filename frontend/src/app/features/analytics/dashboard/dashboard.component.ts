import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../../core/auth/auth.service';
import { canCreateAwards } from '../../../core/auth/permissions';
import { MY_AWARDS_SIDE } from '../../../core/preferences/dashboard-preferences';
import { DashboardLayoutComponent } from '../../../shared/dashboard/dashboard-layout.component';
import { DashboardWidgetDirective } from '../../../shared/dashboard/dashboard-widget.directive';
import { IconComponent } from '../../../shared/icons/icon.component';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { FactWidgetComponent } from '../../../shared/widgets/fact-widget.component';
import {
  MAX_FACTS,
  MIN_STREAK,
  bestYearFact,
  currentKyivMonth,
  firstAwardFact,
  levelShareFact,
  levelSteps,
  streak,
  streakFact,
} from '../../../shared/widgets/facts';
import { Fact } from '../../../shared/widgets/widget-data';
import { KpiWidgetComponent } from '../../../shared/widgets/kpi-widget.component';
import { LevelStairsWidgetComponent } from '../../../shared/widgets/level-stairs-widget.component';
import { OnboardingChecklistComponent } from '../../onboarding/onboarding-checklist/onboarding-checklist.component';
import { readPeriod } from '../analytics-period';
import { AnalyticsPeriod } from '../analytics.service';
import { CategoryChartComponent } from '../category-chart/category-chart.component';
import { CvExportComponent } from '../cv-export/cv-export.component';
import { DashboardEmptyComponent } from '../dashboard-empty/dashboard-empty.component';
import { InProgressComponent } from '../in-progress/in-progress.component';
import { LatestAwardsComponent } from '../latest-awards/latest-awards.component';
import { PeriodSelectComponent } from '../period-select/period-select.component';
import { ProfileCardComponent } from '../profile-card/profile-card.component';
import { RankCardComponent } from '../rank-card/rank-card.component';
import { AnalyticsActions } from '../store/analytics.actions';
import { analyticsFeature } from '../store/analytics.feature';
import { SummaryTilesComponent } from '../summary-tiles/summary-tiles.component';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';
import { VisibilityCountsComponent } from '../visibility-counts/visibility-counts.component';
import { YearTrendChartComponent } from '../year-trend-chart/year-trend-chart.component';

/** Approved awards the personal facts need: one award tells nothing a fact would add. */
export const MIN_FACT_AWARDS = 2;

/**
 * The personal dashboard: period choice, the profile and the widgets in the account's layout on a split page, the CV
 * export, guidance when nothing counts.
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    CategoryChartComponent,
    CvExportComponent,
    DashboardEmptyComponent,
    DashboardLayoutComponent,
    DashboardWidgetDirective,
    FactWidgetComponent,
    IconComponent,
    InProgressComponent,
    KpiWidgetComponent,
    LatestAwardsComponent,
    LevelStairsWidgetComponent,
    MatButton,
    MatIconButton,
    MatProgressBar,
    MatTooltip,
    OnboardingChecklistComponent,
    PageHeaderComponent,
    PaneComponent,
    PeriodSelectComponent,
    ProfileCardComponent,
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
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation(), {
    initialValue: null,
  });

  protected readonly canCreate = canCreateAwards(inject(AuthService).permissions());
  protected readonly side = MY_AWARDS_SIDE;
  protected readonly minFactAwards = MIN_FACT_AWARDS;

  readonly period = this.store.selectSignal(analyticsFeature.selectPeriod);
  readonly analytics = this.store.selectSignal(analyticsFeature.selectAnalytics);
  readonly loading = this.store.selectSignal(analyticsFeature.selectLoading);
  readonly problem = this.store.selectSignal(analyticsFeature.selectProblem);
  readonly invalid = signal(false);

  protected readonly chosen = computed(
    () => this.period().from !== null || this.period().to !== null,
  );
  /** The context line: approved awards of the period and requests under review, once the analytics are in. */
  protected readonly context = computed(() => {
    this.translation();
    const data = this.analytics();
    if (!data || this.invalid()) {
      return null;
    }
    return [
      this.transloco.translate('analytics.context.approved', { count: data.totals.approved }),
      this.transloco.translate('analytics.context.pending', { count: data.inProgress.pending }),
    ].join(' · ');
  });
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
  /** The 9.4.6 facts that apply to one person; growth needs monthly counts the personal analytics do not carry. */
  protected readonly personalFacts = computed(() => {
    const data = this.analytics();
    if (!data) {
      return [];
    }
    const source = {
      period: data.period,
      byYear: data.byYear,
      byMonth: [],
      byLevel: data.byLevel,
      today: this.today,
    };
    return [bestYearFact, firstAwardFact, streakFact, levelShareFact]
      .map((fact) => fact(source))
      .filter((fact): fact is Fact => fact !== null)
      .slice(0, MAX_FACTS);
  });

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
