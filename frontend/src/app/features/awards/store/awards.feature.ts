import { createFeature, createReducer, on } from '@ngrx/store';

import { Award, AwardFilters, NO_FILTERS } from '../awards.service';
import { AwardsActions } from './awards.actions';

export interface AwardsState {
  awards: Award[];
  total: number;
  filters: AwardFilters;
  loading: boolean;
  problem: string | null;
}

export const initialState: AwardsState = {
  awards: [],
  total: 0,
  filters: NO_FILTERS,
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
