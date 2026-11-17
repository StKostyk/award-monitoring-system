import { Injectable, inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import {
  catchError,
  concatMap,
  distinctUntilChanged,
  filter,
  map,
  of,
  switchMap,
  tap,
  withLatestFrom,
} from 'rxjs';

import { problemType } from '../../api/problem';
import { AuthService } from '../../auth/auth.service';
import { ColorSchemeService } from '../../brand/color-scheme.service';
import { toColorScheme } from '../dashboard-preferences';
import { PreferencesService } from '../preferences.service';
import { PreferencesActions } from './preferences.actions';
import { preferencesFeature } from './preferences.feature';

@Injectable()
export class PreferencesEffects {
  private readonly actions$ = inject(Actions);
  private readonly store = inject(Store);
  private readonly preferences = inject(PreferencesService);
  private readonly colorScheme = inject(ColorSchemeService);
  private readonly auth = inject(AuthService);

  readonly session$ = createEffect(() =>
    toObservable(this.auth.profile).pipe(
      map((profile) => profile?.id ?? null),
      distinctUntilChanged(),
      map((id) => (id === null ? PreferencesActions.signedOut() : PreferencesActions.signedIn())),
    ),
  );

  readonly load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(PreferencesActions.signedIn, PreferencesActions.loadRetried),
      switchMap(() =>
        this.preferences.read().pipe(
          map((preferences) => PreferencesActions.loaded({ preferences })),
          catchError(() => of(PreferencesActions.loadFailed())),
        ),
      ),
    ),
  );

  readonly applyStored$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(PreferencesActions.loaded),
        tap(({ preferences }) => this.colorScheme.use(toColorScheme(preferences.colorScheme))),
      ),
    { dispatch: false },
  );

  readonly applyPicked$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(PreferencesActions.schemePicked),
        tap(({ scheme }) => this.colorScheme.use(scheme)),
      ),
    { dispatch: false },
  );

  readonly save$ = createEffect(() =>
    this.actions$.pipe(
      ofType(PreferencesActions.schemePicked, PreferencesActions.layoutChanged),
      withLatestFrom(this.store.select(preferencesFeature.selectPreferencesState)),
      filter(([, state]) => state.status === 'loaded'),
      concatMap(([, state]) =>
        this.preferences.save({ colorScheme: state.colorScheme, layouts: state.layouts }).pipe(
          map((preferences) => PreferencesActions.saved({ preferences })),
          catchError((error: unknown) =>
            of(PreferencesActions.saveFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );
}
