import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';

import { problemType } from '../../../../core/api/problem';
import { UniversityAnalyticsService } from '../university-analytics.service';
import { UniversityAnalyticsActions } from './university-analytics.actions';

@Injectable()
export class UniversityAnalyticsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(UniversityAnalyticsService);

  readonly load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UniversityAnalyticsActions.periodSelected),
      switchMap(({ period }) =>
        this.api.read(period).pipe(
          map((analytics) => UniversityAnalyticsActions.analyticsLoaded({ analytics })),
          catchError((error: unknown) =>
            of(UniversityAnalyticsActions.analyticsLoadFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );
}
