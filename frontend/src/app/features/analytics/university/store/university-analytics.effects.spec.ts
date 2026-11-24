import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { UniversityAnalyticsService } from '../university-analytics.service';
import { universityAnalytics } from '../university-analytics.testing';
import { UniversityAnalyticsActions } from './university-analytics.actions';
import { UniversityAnalyticsEffects } from './university-analytics.effects';

describe('UniversityAnalyticsEffects', () => {
  let actions$: Subject<Action>;
  let effects: UniversityAnalyticsEffects;
  let api: { read: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    actions$ = new Subject<Action>();
    api = {
      read: vi.fn().mockReturnValue(of(universityAnalytics())),
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
  });

  it('ac2_8_reports_a_failed_load', async () => {
    api.read.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const result = firstValueFrom(effects.load$);
    actions$.next(UniversityAnalyticsActions.periodSelected({ period: { from: null, to: null } }));

    expect(await result).toEqual(
      UniversityAnalyticsActions.analyticsLoadFailed({ problem: 'network' }),
    );
  });
});
