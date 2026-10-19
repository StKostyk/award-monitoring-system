import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AnalyticsService } from '../analytics.service';
import { sampleAnalytics } from '../analytics.testing';
import { AnalyticsActions } from './analytics.actions';
import { AnalyticsEffects } from './analytics.effects';
import { analyticsFeature, initialState } from './analytics.feature';

function problem(type: string, status: number): HttpErrorResponse {
  return new HttpErrorResponse({ error: { type: `urn:awards:problem:${type}` }, status });
}

describe('AnalyticsEffects', () => {
  let actions$: Subject<Action>;
  let effects: AnalyticsEffects;
  let api: {
    read: ReturnType<typeof vi.fn>;
    hiddenWidgets: ReturnType<typeof vi.fn>;
    saveHiddenWidgets: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    actions$ = new Subject<Action>();
    api = {
      read: vi.fn().mockReturnValue(of(sampleAnalytics())),
      hiddenWidgets: vi.fn().mockReturnValue(of(['RANK'])),
      saveHiddenWidgets: vi.fn((hidden: string[]) => of(hidden)),
    };
    TestBed.configureTestingModule({
      providers: [
        AnalyticsEffects,
        provideMockActions(() => actions$),
        { provide: AnalyticsService, useValue: api },
      ],
    });
    effects = TestBed.inject(AnalyticsEffects);
  });

  it('ac2_5_loads_the_analytics_of_the_selected_period', async () => {
    const loaded = firstValueFrom(effects.load$);
    actions$.next(AnalyticsActions.periodSelected({ period: { from: 2024, to: null } }));

    expect(await loaded).toEqual(
      AnalyticsActions.analyticsLoaded({ analytics: sampleAnalytics() }),
    );
    expect(api.read).toHaveBeenCalledWith({ from: 2024, to: null });
  });

  it('ac2_5_reports_a_failed_load', async () => {
    api.read.mockReturnValue(throwError(() => problem('invalid-parameter', 400)));
    const failed = firstValueFrom(effects.load$);
    actions$.next(AnalyticsActions.periodSelected({ period: { from: 2024, to: 2020 } }));

    expect(await failed).toEqual(
      AnalyticsActions.analyticsLoadFailed({ problem: 'invalid-parameter' }),
    );
  });

  it('ac2_9_reads_the_widget_choice_when_the_dashboard_opens', async () => {
    const loaded = firstValueFrom(effects.preferences$);
    actions$.next(AnalyticsActions.opened());

    expect(await loaded).toEqual(AnalyticsActions.preferencesLoaded({ hidden: ['RANK'] }));

    api.hiddenWidgets.mockReturnValue(throwError(() => problem('internal', 500)));
    const failed = firstValueFrom(effects.preferences$);
    actions$.next(AnalyticsActions.opened());

    expect(await failed).toEqual(AnalyticsActions.preferencesLoadFailed());
  });

  it('ac2_9_saves_the_widget_choice', async () => {
    const saved = firstValueFrom(effects.save$);
    actions$.next(AnalyticsActions.saveRequested({ hidden: ['TIMELINE'] }));

    expect(await saved).toEqual(AnalyticsActions.saved({ hidden: ['TIMELINE'] }));

    api.saveHiddenWidgets.mockReturnValue(throwError(() => problem('invalid-parameter', 400)));
    const failed = firstValueFrom(effects.save$);
    actions$.next(AnalyticsActions.saveRequested({ hidden: ['TIMELINE'] }));

    expect(await failed).toEqual(AnalyticsActions.saveFailed({ problem: 'invalid-parameter' }));
  });
});

describe('analyticsFeature', () => {
  const reducer = analyticsFeature.reducer;

  it('ac2_5_keeps_the_period_and_the_load_state', () => {
    const loading = reducer(
      initialState,
      AnalyticsActions.periodSelected({ period: { from: 2020, to: 2024 } }),
    );

    expect(loading).toMatchObject({
      period: { from: 2020, to: 2024 },
      loading: true,
      problem: null,
    });

    const loaded = reducer(
      loading,
      AnalyticsActions.analyticsLoaded({ analytics: sampleAnalytics() }),
    );

    expect(loaded.loading).toBe(false);
    expect(loaded.analytics?.totals.approved).toBe(3);

    const failed = reducer(loaded, AnalyticsActions.analyticsLoadFailed({ problem: 'unknown' }));

    expect(failed).toMatchObject({ analytics: null, loading: false, problem: 'unknown' });
  });

  it('ac2_9_tracks_the_saved_choice_and_a_failed_save', () => {
    const shown = reducer(initialState, AnalyticsActions.preferencesLoaded({ hidden: ['RANK'] }));

    expect(shown.hidden).toEqual(['RANK']);

    const saving = reducer(shown, AnalyticsActions.saveRequested({ hidden: [] }));

    expect(saving.saving).toBe(true);

    const failed = reducer(saving, AnalyticsActions.saveFailed({ problem: 'unknown' }));

    expect(failed).toMatchObject({ saving: false, saveProblem: 'unknown', hidden: ['RANK'] });

    const reopened = reducer(failed, AnalyticsActions.opened());

    expect(reopened.saveProblem).toBeNull();

    const saved = reducer(saving, AnalyticsActions.saved({ hidden: [] }));

    expect(saved).toMatchObject({ saving: false, hidden: [] });
  });
});
