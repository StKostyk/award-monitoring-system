import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { UnitAnalyticsService } from '../unit-analytics.service';
import { FACULTY, departmentAnalytics, facultyAnalytics } from '../unit-analytics.testing';
import { UnitAnalyticsActions } from './unit-analytics.actions';
import { UNIT_NOT_FOUND, UnitAnalyticsEffects } from './unit-analytics.effects';
import { initialState, unitAnalyticsFeature } from './unit-analytics.feature';

describe('UnitAnalyticsEffects', () => {
  let actions$: Subject<Action>;
  let effects: UnitAnalyticsEffects;
  let api: { units: ReturnType<typeof vi.fn>; read: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    actions$ = new Subject<Action>();
    api = {
      units: vi.fn().mockReturnValue(of([FACULTY])),
      read: vi.fn().mockReturnValue(of(facultyAnalytics())),
    };
    TestBed.configureTestingModule({
      providers: [
        UnitAnalyticsEffects,
        provideMockActions(() => actions$),
        { provide: UnitAnalyticsService, useValue: api },
      ],
    });
    effects = TestBed.inject(UnitAnalyticsEffects);
  });

  it('ac2_1_loads_the_units', async () => {
    const result = firstValueFrom(effects.units$);
    actions$.next(UnitAnalyticsActions.unitsRequested());

    expect(await result).toEqual(UnitAnalyticsActions.unitsLoaded({ units: [FACULTY] }));
  });

  it('ac2_7_reports_a_failed_unit_list', async () => {
    api.units.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const result = firstValueFrom(effects.units$);
    actions$.next(UnitAnalyticsActions.unitsRequested());

    expect(await result).toEqual(UnitAnalyticsActions.unitsLoadFailed({ problem: 'network' }));
  });

  it('ac2_2_loads_the_unit_for_the_period', async () => {
    const result = firstValueFrom(effects.load$);
    actions$.next(
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: { from: 2024, to: 2026 } }),
    );

    expect(await result).toEqual(
      UnitAnalyticsActions.analyticsLoaded({ analytics: facultyAnalytics() }),
    );
    expect(api.read).toHaveBeenCalledWith(9, { from: 2024, to: 2026 });
  });

  it('ac2_1_tells_a_unit_the_api_does_not_know_from_other_failures', async () => {
    api.read.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
    const missing = firstValueFrom(effects.load$);
    actions$.next(
      UnitAnalyticsActions.unitSelected({ unitId: 999, period: { from: null, to: null } }),
    );

    expect(await missing).toEqual(
      UnitAnalyticsActions.analyticsLoadFailed({ problem: UNIT_NOT_FOUND }),
    );

    api.read.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    const failed = firstValueFrom(effects.load$);
    actions$.next(
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: { from: null, to: null } }),
    );

    expect(await failed).toEqual(UnitAnalyticsActions.analyticsLoadFailed({ problem: 'unknown' }));
  });

  it('ac2_2_drops_the_figures_of_the_previous_unit_but_keeps_them_for_a_new_period', () => {
    const reducer = unitAnalyticsFeature.reducer;
    const loaded = reducer(
      { ...initialState, unitId: 9 },
      UnitAnalyticsActions.analyticsLoaded({ analytics: facultyAnalytics() }),
    );

    const samePeriodChange = reducer(
      loaded,
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: { from: 2025, to: null } }),
    );
    const otherUnit = reducer(
      loaded,
      UnitAnalyticsActions.unitSelected({ unitId: 64, period: { from: null, to: null } }),
    );

    expect(samePeriodChange.analytics).not.toBeNull();
    expect(samePeriodChange.loading).toBe(true);
    expect(otherUnit.analytics).toBeNull();
    expect(
      reducer(otherUnit, UnitAnalyticsActions.analyticsLoaded({ analytics: departmentAnalytics() }))
        .analytics?.unit.id,
    ).toBe(64);
  });
});
