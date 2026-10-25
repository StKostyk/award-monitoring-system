import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { UniversityAnalyticsService } from '../university-analytics.service';
import { complianceSummary, universityAnalytics } from '../university-analytics.testing';
import { UniversityAnalyticsActions } from './university-analytics.actions';
import { UniversityAnalyticsEffects } from './university-analytics.effects';
import { initialState, universityAnalyticsFeature } from './university-analytics.feature';

describe('UniversityAnalyticsEffects', () => {
  let actions$: Subject<Action>;
  let effects: UniversityAnalyticsEffects;
  let api: { read: ReturnType<typeof vi.fn>; compliance: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    actions$ = new Subject<Action>();
    api = {
      read: vi.fn().mockReturnValue(of(universityAnalytics())),
      compliance: vi.fn().mockReturnValue(of(complianceSummary())),
    };
    TestBed.configureTestingModule({
      providers: [
        UniversityAnalyticsEffects,
        provideMockActions(() => actions$),
        { provide: UniversityAnalyticsService, useValue: api },
      ],
    });
    effects = TestBed.inject(UniversityAnalyticsEffects);
  });

  it('ac2_2_loads_the_university_for_the_period', async () => {
    const result = firstValueFrom(effects.load$);
    actions$.next(UniversityAnalyticsActions.periodSelected({ period: { from: 2022, to: 2026 } }));

    expect(await result).toEqual(
      UniversityAnalyticsActions.analyticsLoaded({ analytics: universityAnalytics() }),
    );
    expect(api.read).toHaveBeenCalledWith({ from: 2022, to: 2026 });
    expect(api.compliance).not.toHaveBeenCalled();
  });

  it('ac2_8_reports_a_failed_load', async () => {
    api.read.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const result = firstValueFrom(effects.load$);
    actions$.next(UniversityAnalyticsActions.periodSelected({ period: { from: null, to: null } }));

    expect(await result).toEqual(
      UniversityAnalyticsActions.analyticsLoadFailed({ problem: 'network' }),
    );
  });

  it('ac2_7_loads_the_compliance_summary_on_its_own_and_reports_its_failure', async () => {
    const loaded = firstValueFrom(effects.compliance$);
    actions$.next(UniversityAnalyticsActions.complianceRequested());

    expect(await loaded).toEqual(
      UniversityAnalyticsActions.complianceLoaded({ compliance: complianceSummary() }),
    );
    expect(api.read).not.toHaveBeenCalled();

    api.compliance.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const failed = firstValueFrom(effects.compliance$);
    actions$.next(UniversityAnalyticsActions.complianceRequested());

    expect(await failed).toEqual(
      UniversityAnalyticsActions.complianceLoadFailed({ problem: 'unknown' }),
    );
  });

  it('ac2_7_a_period_change_keeps_the_compliance_summary_and_a_refresh_keeps_the_analytics', () => {
    const reducer = universityAnalyticsFeature.reducer;
    const loaded = reducer(
      reducer(
        initialState,
        UniversityAnalyticsActions.analyticsLoaded({ analytics: universityAnalytics() }),
      ),
      UniversityAnalyticsActions.complianceLoaded({ compliance: complianceSummary() }),
    );

    const periodChange = reducer(
      loaded,
      UniversityAnalyticsActions.periodSelected({ period: { from: 2025, to: null } }),
    );
    const refresh = reducer(loaded, UniversityAnalyticsActions.complianceRequested());
    const complianceFailed = reducer(
      refresh,
      UniversityAnalyticsActions.complianceLoadFailed({ problem: 'unknown' }),
    );

    expect(periodChange.compliance).not.toBeNull();
    expect(periodChange.complianceLoading).toBe(false);
    expect(periodChange.loading).toBe(true);
    expect(refresh.analytics).not.toBeNull();
    expect(refresh.loading).toBe(false);
    expect(refresh.complianceLoading).toBe(true);
    expect(complianceFailed.analytics).not.toBeNull();
    expect(complianceFailed.complianceProblem).toBe('unknown');
  });
});
