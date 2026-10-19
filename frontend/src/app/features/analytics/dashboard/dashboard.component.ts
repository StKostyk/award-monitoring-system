import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../../core/auth/auth.service';
import { canCreateAwards } from '../../../core/auth/permissions';
import { kyivToday } from '../../../shared/date-format';
import { AnalyticsPeriod, DASHBOARD_WIDGETS, DashboardWidget } from '../analytics.service';
import { CategoryChartComponent } from '../category-chart/category-chart.component';
import { CustomiseDialogComponent } from '../customise-dialog/customise-dialog.component';
import { DashboardEmptyComponent } from '../dashboard-empty/dashboard-empty.component';
import { LevelChartComponent } from '../level-chart/level-chart.component';
import { RankCardComponent } from '../rank-card/rank-card.component';
import { AnalyticsActions } from '../store/analytics.actions';
import { analyticsFeature } from '../store/analytics.feature';
import { SummaryTilesComponent } from '../summary-tiles/summary-tiles.component';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';
import { YearTrendChartComponent } from '../year-trend-chart/year-trend-chart.component';

export const FIRST_YEAR = 1950;
export const LAST_YEAR = 2100;

/** A year query parameter within 1950..2100, or null. */
export function yearParameter(params: ParamMap, name: string): number | null {
  const value = Number(params.get(name));
  return params.has(name) && Number.isInteger(value) && value >= FIRST_YEAR && value <= LAST_YEAR
    ? value
    : null;
}

/** The personal dashboard: period choice, the widgets the account keeps, guidance when nothing counts. */
@Component({
  selector: 'app-dashboard',
  imports: [
    CategoryChartComponent,
    DashboardEmptyComponent,
    LevelChartComponent,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatFormField,
    MatIcon,
    MatLabel,
    MatOption,
    MatProgressBar,
    MatSelect,
    RankCardComponent,
    RouterLink,
    SummaryTilesComponent,
    TimelineChartComponent,
    TranslocoPipe,
    YearTrendChartComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly canCreate = canCreateAwards(inject(AuthService).permissions());
  protected readonly years = yearsDescending(Number(kyivToday().slice(0, 4)));

  readonly period = this.store.selectSignal(analyticsFeature.selectPeriod);
  readonly analytics = this.store.selectSignal(analyticsFeature.selectAnalytics);
  readonly loading = this.store.selectSignal(analyticsFeature.selectLoading);
  readonly problem = this.store.selectSignal(analyticsFeature.selectProblem);
  readonly hidden = this.store.selectSignal(analyticsFeature.selectHidden);
  readonly saveProblem = this.store.selectSignal(analyticsFeature.selectSaveProblem);

  protected readonly visible = computed(() => {
    const hidden = this.hidden();
    return new Set(DASHBOARD_WIDGETS.filter((widget) => !hidden.includes(widget)));
  });
  protected readonly chosen = computed(
    () => this.period().from !== null || this.period().to !== null,
  );
  protected readonly empty = computed(() => this.analytics()?.totals.approved === 0);

  constructor() {
    this.store.dispatch(AnalyticsActions.opened());
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) =>
      this.store.dispatch(
        AnalyticsActions.periodSelected({
          period: { from: yearParameter(params, 'from'), to: yearParameter(params, 'to') },
        }),
      ),
    );
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

  protected customise(): void {
    this.dialog
      .open<CustomiseDialogComponent, DashboardWidget[], DashboardWidget[]>(
        CustomiseDialogComponent,
        { data: this.hidden(), width: '420px' },
      )
      .afterClosed()
      .subscribe((hidden) => {
        if (hidden) {
          this.store.dispatch(AnalyticsActions.saveRequested({ hidden }));
        }
      });
  }
}

function yearsDescending(last: number): number[] {
  return Array.from({ length: last - FIRST_YEAR + 1 }, (_, index) => last - index);
}
