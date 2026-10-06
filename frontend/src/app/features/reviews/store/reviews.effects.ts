import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { catchError, map, of, switchMap, withLatestFrom } from 'rxjs';

import { problemType } from '../../../core/api/problem';
import { ReviewsService } from '../reviews.service';
import { ReviewsActions } from './reviews.actions';
import { reviewsFeature } from './reviews.feature';

@Injectable()
export class ReviewsEffects {
  private readonly actions$ = inject(Actions);
  private readonly store = inject(Store);
  private readonly reviews = inject(ReviewsService);

  readonly load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ReviewsActions.opened, ReviewsActions.filtersChanged, ReviewsActions.pageChanged),
      withLatestFrom(
        this.store.select(reviewsFeature.selectFilters),
        this.store.select(reviewsFeature.selectPageIndex),
        this.store.select(reviewsFeature.selectPageSize),
      ),
      switchMap(([, filters, pageIndex, pageSize]) =>
        this.reviews.list(filters, pageIndex, pageSize).pipe(
          map((page) => ReviewsActions.reviewsLoaded({ page })),
          catchError((error: unknown) =>
            of(ReviewsActions.reviewsLoadFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );
}
