import { createFeature, createReducer, on } from '@ngrx/store';

import { NO_REVIEW_FILTERS, ReviewFilters, ReviewItem } from '../reviews.service';
import { ReviewsActions } from './reviews.actions';

/** Requests per page when the queue opens. */
export const DEFAULT_PAGE_SIZE = 20;

export interface ReviewsState {
  items: ReviewItem[];
  total: number;
  filters: ReviewFilters;
  pageIndex: number;
  pageSize: number;
  loading: boolean;
  problem: string | null;
}

export const initialState: ReviewsState = {
  items: [],
  total: 0,
  filters: NO_REVIEW_FILTERS,
  pageIndex: 0,
  pageSize: DEFAULT_PAGE_SIZE,
  loading: false,
  problem: null,
};

export const reviewsFeature = createFeature({
  name: 'reviews',
  reducer: createReducer(
    initialState,
    on(ReviewsActions.opened, (state) => ({ ...state, loading: true, problem: null })),
    on(ReviewsActions.filtersChanged, (state, { filters }) => ({
      ...state,
      filters,
      pageIndex: 0,
      loading: true,
      problem: null,
    })),
    on(ReviewsActions.pageChanged, (state, { pageIndex, pageSize }) => ({
      ...state,
      pageIndex,
      pageSize,
      loading: true,
      problem: null,
    })),
    on(ReviewsActions.reviewsLoaded, (state, { page }) => ({
      ...state,
      items: page.content,
      total: page.totalElements,
      loading: false,
    })),
    on(ReviewsActions.reviewsLoadFailed, (state, { problem }) => ({
      ...state,
      items: [],
      total: 0,
      loading: false,
      problem,
    })),
  ),
});
