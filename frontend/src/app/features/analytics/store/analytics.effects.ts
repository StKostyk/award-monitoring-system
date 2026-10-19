import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';

import { problemType } from '../../../core/api/problem';
import { AnalyticsService } from '../analytics.service';
import { AnalyticsActions } from './analytics.actions';

@Injectable()
export class AnalyticsEffects {
  private readonly actions$ = inject(Actions);
  private readonly analytics = inject(AnalyticsService);

  readonly load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AnalyticsActions.periodSelected),
      switchMap(({ period }) =>
        this.analytics.read(period).pipe(
          map((analytics) => AnalyticsActions.analyticsLoaded({ analytics })),
          catchError((error: unknown) =>
            of(AnalyticsActions.analyticsLoadFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );

  readonly preferences$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AnalyticsActions.opened, AnalyticsActions.preferencesRetried),
      switchMap(() =>
        this.analytics.hiddenWidgets().pipe(
          map((hidden) => AnalyticsActions.preferencesLoaded({ hidden })),
          catchError(() => of(AnalyticsActions.preferencesLoadFailed())),
        ),
      ),
    ),
  );

  readonly save$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AnalyticsActions.saveRequested),
      switchMap(({ hidden }) =>
        this.analytics.saveHiddenWidgets(hidden).pipe(
          map((saved) => AnalyticsActions.saved({ hidden: saved })),
          catchError((error: unknown) =>
            of(AnalyticsActions.saveFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );
}
