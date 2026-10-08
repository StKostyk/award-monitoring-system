import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { provideMockStore } from '@ngrx/store/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { NO_REVIEW_FILTERS, ReviewFilters, ReviewItem, ReviewsService } from '../reviews.service';
import { ReviewsActions } from './reviews.actions';
import { ReviewsEffects } from './reviews.effects';
import { initialState, reviewsFeature } from './reviews.feature';

const page = { content: [], totalElements: 3, totalPages: 1, size: 20, number: 0 };

describe('reviews feature', () => {
  it('ac1_10_keeps_the_filters_and_returns_to_the_first_page_when_they_change', () => {
    let state = reviewsFeature.reducer(
      initialState,
      ReviewsActions.pageChanged({ pageIndex: 2, pageSize: 50 }),
    );
    expect(state).toMatchObject({ pageIndex: 2, pageSize: 50, loading: true });

    const filters: ReviewFilters = { ...NO_REVIEW_FILTERS, assigned: 'me' };
    state = reviewsFeature.reducer(state, ReviewsActions.filtersChanged({ filters }));
    expect(state).toMatchObject({ filters, pageIndex: 0, pageSize: 50 });

    state = reviewsFeature.reducer(state, ReviewsActions.reviewsLoaded({ page }));
    expect(state).toMatchObject({ total: 3, loading: false });

    state = reviewsFeature.reducer(state, ReviewsActions.reviewsLoadFailed({ problem: 'network' }));
    expect(state).toMatchObject({ items: [], total: 0, problem: 'network' });
  });

  it('ac4_7_decided_items_leave_the_page_and_the_total', () => {
    const items = [5, 6, 7].map((awardId) => ({ awardId }) as ReviewItem);
    const loaded = { ...initialState, items, total: 12 };

    const state = reviewsFeature.reducer(
      loaded,
      ReviewsActions.itemsDecided({ awardIds: [5, 7, 99] }),
    );

    expect(state.items.map((item) => item.awardId)).toEqual([6]);
    expect(state.total).toBe(10);
  });
});

describe('ReviewsEffects', () => {
  let actions$: Observable<Action>;
  const service = { list: vi.fn() };

  function effects(filters: ReviewFilters): ReviewsEffects {
    TestBed.configureTestingModule({
      providers: [
        ReviewsEffects,
        provideMockActions(() => actions$),
        provideMockStore({ initialState: { reviews: { ...initialState, filters } } }),
        { provide: ReviewsService, useValue: service },
      ],
    });
    return TestBed.inject(ReviewsEffects);
  }

  beforeEach(() => service.list.mockReset());

  it('ac1_10_loads_the_queue_with_the_current_filters', () => {
    const filters: ReviewFilters = { ...NO_REVIEW_FILTERS, level: 'DEAN' };
    service.list.mockReturnValue(of(page));
    actions$ = of(ReviewsActions.opened());
    const emitted: Action[] = [];

    effects(filters).load$.subscribe((action) => emitted.push(action));

    expect(service.list).toHaveBeenCalledWith(filters, 0, 20);
    expect(emitted).toEqual([ReviewsActions.reviewsLoaded({ page })]);
  });

  it('ac1_10_reports_a_failed_load', () => {
    service.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    actions$ = of(ReviewsActions.opened());
    const emitted: Action[] = [];

    effects(NO_REVIEW_FILTERS).load$.subscribe((action) => emitted.push(action));

    expect(emitted).toEqual([ReviewsActions.reviewsLoadFailed({ problem: 'network' })]);
  });
});
