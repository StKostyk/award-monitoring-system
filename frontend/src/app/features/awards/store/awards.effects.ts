import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { catchError, map, of, switchMap, withLatestFrom } from 'rxjs';

import { problemType } from '../../../core/api/problem';
import { AwardsService } from '../awards.service';
import { AwardsActions } from './awards.actions';
import { awardsFeature } from './awards.feature';

@Injectable()
export class AwardsEffects {
  private readonly actions$ = inject(Actions);
  private readonly store = inject(Store);
  private readonly awards = inject(AwardsService);

  readonly load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AwardsActions.opened, AwardsActions.filtersChanged, AwardsActions.pageChanged),
      withLatestFrom(
        this.store.select(awardsFeature.selectFilters),
        this.store.select(awardsFeature.selectPageIndex),
        this.store.select(awardsFeature.selectPageSize),
      ),
      switchMap(([, filters, pageIndex, pageSize]) =>
        this.awards.list(filters, pageIndex, pageSize).pipe(
          map((page) => AwardsActions.awardsLoaded({ page })),
          catchError((error: unknown) =>
            of(AwardsActions.awardsLoadFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );
}
