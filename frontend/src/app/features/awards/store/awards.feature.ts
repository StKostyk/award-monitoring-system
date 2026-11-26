import { createFeature, createReducer, on } from '@ngrx/store';

import { narrowScreen } from '../../../shared/touch';
import { Award, AwardFilters, NO_FILTERS } from '../awards.service';
import { AwardsActions } from './awards.actions';

/** Awards per page when the list opens. */
export const DEFAULT_PAGE_SIZE = 20;

/** Awards per page when the list opens on a phone: half a page renders in half the time. */
export const NARROW_PAGE_SIZE = 10;

/**
 * Awards per page when the list opens.
 *
 * @param narrow whether the screen is phone-sized
 * @return the page size
 */
export function openingPageSize(narrow = narrowScreen()): number {
  return narrow ? NARROW_PAGE_SIZE : DEFAULT_PAGE_SIZE;
}

export interface AwardsState {
  awards: Award[];
  total: number;
  filters: AwardFilters;
  pageIndex: number;
  pageSize: number;
  loading: boolean;
  problem: string | null;
}

export const initialState: AwardsState = {
  awards: [],
  total: 0,
  filters: NO_FILTERS,
  pageIndex: 0,
  pageSize: openingPageSize(),
  loading: false,
  problem: null,
};

export const awardsFeature = createFeature({
  name: 'awards',
  reducer: createReducer(
    initialState,
    on(AwardsActions.opened, (state) => ({ ...state, loading: true, problem: null })),
    on(AwardsActions.filtersChanged, (state, { filters }) => ({
      ...state,
      filters,
      pageIndex: 0,
      loading: true,
      problem: null,
    })),
    on(AwardsActions.pageChanged, (state, { pageIndex, pageSize }) => ({
      ...state,
      pageIndex,
      pageSize,
      loading: true,
      problem: null,
    })),
    on(AwardsActions.awardsLoaded, (state, { page }) => ({
      ...state,
      awards: page.content,
      total: page.totalElements,
      loading: false,
    })),
    on(AwardsActions.awardsLoadFailed, (state, { problem }) => ({
      ...state,
      awards: [],
      total: 0,
      loading: false,
      problem,
    })),
  ),
});
