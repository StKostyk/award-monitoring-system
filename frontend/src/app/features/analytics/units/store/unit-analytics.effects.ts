import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';

import { problemStatus, problemType } from '../../../../core/api/problem';
import { UnitAnalyticsService } from '../unit-analytics.service';
import { UnitAnalyticsActions } from './unit-analytics.actions';

/** The problem a page shows when the API answers 404 for a unit. */
export const UNIT_NOT_FOUND = 'unit-not-found';

const NOT_FOUND = 404;

@Injectable()
export class UnitAnalyticsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(UnitAnalyticsService);

  readonly units$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UnitAnalyticsActions.unitsRequested),
      switchMap(() =>
        this.api.units().pipe(
          map((units) => UnitAnalyticsActions.unitsLoaded({ units })),
          catchError((error: unknown) =>
            of(UnitAnalyticsActions.unitsLoadFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );

  readonly load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UnitAnalyticsActions.unitSelected),
      switchMap(({ unitId, period }) =>
        this.api.read(unitId, period).pipe(
          map((analytics) => UnitAnalyticsActions.analyticsLoaded({ analytics })),
          catchError((error: unknown) =>
            of(
              UnitAnalyticsActions.analyticsLoadFailed({
                problem: problemStatus(error) === NOT_FOUND ? UNIT_NOT_FOUND : problemType(error),
              }),
            ),
          ),
        ),
      ),
    ),
  );
}
