import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { Subject, firstValueFrom, of, take, throwError, toArray } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../auth/auth.service';
import { UserProfile } from '../../auth/user-profile';
import { ColorSchemeService } from '../../brand/color-scheme.service';
import { DashboardPreferences, arrange } from '../dashboard-preferences';
import { PreferencesService } from '../preferences.service';
import { PreferencesActions } from './preferences.actions';
import { PreferencesEffects } from './preferences.effects';
import { PreferencesState, initialState, preferencesFeature } from './preferences.feature';

const STORED: DashboardPreferences = {
  colorScheme: 'DARK',
  layouts: { MY_AWARDS: { order: ['RANK'], hidden: ['KPI'] } },
};

describe('PreferencesEffects', () => {
  let actions$: Subject<Action>;
  let effects: PreferencesEffects;
  let store: MockStore;
  const profile = signal<UserProfile | null>(null);
  const api = { read: vi.fn(), save: vi.fn() };
  const scheme = { use: vi.fn() };

  beforeEach(() => {
    actions$ = new Subject<Action>();
    profile.set(null);
    api.read.mockReset().mockReturnValue(of(STORED));
    api.save.mockReset().mockImplementation((value: DashboardPreferences) => of(value));
    scheme.use.mockReset();
    TestBed.configureTestingModule({
      providers: [
        PreferencesEffects,
        provideMockActions(() => actions$),
        provideMockStore({ initialState: { preferences: initialState } }),
        { provide: PreferencesService, useValue: api },
        { provide: ColorSchemeService, useValue: scheme },
        { provide: AuthService, useValue: { profile } },
      ],
    });
    effects = TestBed.inject(PreferencesEffects);
    store = TestBed.inject(MockStore);
  });

  function withState(state: Partial<PreferencesState>): void {
    store.setState({ preferences: { ...initialState, ...state } });
  }

  it('ac1_2_loads_after_the_profile_and_resets_on_sign_out', async () => {
    const session = firstValueFrom(effects.session$.pipe(take(3), toArray()));
    TestBed.tick();
    profile.set({ id: 7 } as UserProfile);
    TestBed.tick();
    profile.set({ id: 7, firstName: 'Changed' } as UserProfile);
    TestBed.tick();
    profile.set(null);
    TestBed.tick();

    expect(await session).toEqual([
      PreferencesActions.signedOut(),
      PreferencesActions.signedIn(),
      PreferencesActions.signedOut(),
    ]);
  });

  it('ac1_2_reads_the_preferences_and_reports_a_failed_read', async () => {
    const loaded = firstValueFrom(effects.load$);
    actions$.next(PreferencesActions.signedIn());

    expect(await loaded).toEqual(PreferencesActions.loaded({ preferences: STORED }));

    api.read.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const failed = firstValueFrom(effects.load$);
    actions$.next(PreferencesActions.loadRetried());

    expect(await failed).toEqual(PreferencesActions.loadFailed());
  });

  it('ac1_2_applies_the_stored_scheme_once_loaded', () => {
    effects.applyStored$.subscribe();
    actions$.next(PreferencesActions.loaded({ preferences: STORED }));

    expect(scheme.use).toHaveBeenCalledWith('dark');
  });

  it('ac1_1_applies_a_picked_scheme_and_stores_it_with_every_layout', async () => {
    effects.applyPicked$.subscribe();
    withState({ status: 'loaded', colorScheme: 'LIGHT', layouts: STORED.layouts });
    const saved = firstValueFrom(effects.save$);
    actions$.next(PreferencesActions.schemePicked({ scheme: 'light' }));

    expect(scheme.use).toHaveBeenCalledWith('light');
    expect(await saved).toEqual(
      PreferencesActions.saved({ preferences: { colorScheme: 'LIGHT', layouts: STORED.layouts } }),
    );
  });

  it('ac1_2_keeps_a_scheme_in_the_browser_only_while_signed_out', () => {
    const results: Action[] = [];
    effects.save$.subscribe((action) => results.push(action));
    actions$.next(PreferencesActions.schemePicked({ scheme: 'dark' }));

    expect(api.save).not.toHaveBeenCalled();
    expect(results).toEqual([]);
  });

  it('ac1_3_reports_a_failed_save', async () => {
    withState({ status: 'loaded' });
    api.save.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            error: { type: 'urn:awards:problem:invalid-parameter' },
            status: 400,
          }),
      ),
    );
    const failed = firstValueFrom(effects.save$);
    actions$.next(PreferencesActions.layoutChanged({ dashboard: 'MY_AWARDS', layout: null }));

    expect(await failed).toEqual(PreferencesActions.saveFailed({ problem: 'invalid-parameter' }));
  });
});

describe('preferencesFeature', () => {
  const reducer = preferencesFeature.reducer;

  it('ac1_3_keeps_the_loaded_preferences_and_the_changed_layouts', () => {
    const loading = reducer(initialState, PreferencesActions.signedIn());

    expect(loading.status).toBe('loading');

    const loaded = reducer(loading, PreferencesActions.loaded({ preferences: STORED }));

    expect(loaded).toMatchObject({
      status: 'loaded',
      colorScheme: 'DARK',
      layouts: STORED.layouts,
    });

    const picked = reducer(loaded, PreferencesActions.schemePicked({ scheme: 'system' }));

    expect(picked.colorScheme).toBe('SYSTEM');

    const moved = reducer(
      picked,
      PreferencesActions.layoutChanged({
        dashboard: 'HALL_OF_FAME',
        layout: { order: ['TOP'], hidden: [] },
      }),
    );

    expect(Object.keys(moved.layouts)).toEqual(['MY_AWARDS', 'HALL_OF_FAME']);

    const reset = reducer(
      moved,
      PreferencesActions.layoutChanged({ dashboard: 'MY_AWARDS', layout: null }),
    );

    expect(reset.layouts).toEqual({ HALL_OF_FAME: { order: ['TOP'], hidden: [] } });

    const failed = reducer(reset, PreferencesActions.saveFailed({ problem: 'unknown' }));

    expect(failed.saveProblem).toBe('unknown');
    expect(
      reducer(failed, PreferencesActions.saved({ preferences: STORED })).saveProblem,
    ).toBeNull();
    expect(reducer(failed, PreferencesActions.loadFailed()).status).toBe('failed');
    expect(reducer(failed, PreferencesActions.signedOut())).toEqual(initialState);
  });
});

describe('arrange', () => {
  const widgets = ['KPI', 'TREND', 'RANK', 'TOP'];

  it('ac1_5_puts_the_widgets_a_stored_order_misses_after_it_in_default_order', () => {
    expect(arrange(widgets, { order: ['RANK', 'KPI'], hidden: [] })).toEqual([
      'RANK',
      'KPI',
      'TREND',
      'TOP',
    ]);
  });

  it('ac1_5_drops_unknown_and_repeated_names_and_falls_back_to_the_default', () => {
    expect(arrange(widgets, { order: ['GONE', 'TOP', 'TOP'], hidden: [] })).toEqual([
      'TOP',
      'KPI',
      'TREND',
      'RANK',
    ]);
    expect(arrange(widgets, undefined)).toEqual(widgets);
  });
});
