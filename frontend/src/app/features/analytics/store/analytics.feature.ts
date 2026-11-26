import { createFeature, createReducer, on } from '@ngrx/store';

import { AnalyticsPeriod, PersonalAnalytics } from '../analytics.service';
import { AnalyticsActions } from './analytics.actions';

export interface AnalyticsState {
  period: AnalyticsPeriod;
  analytics: PersonalAnalytics | null;
  loading: boolean;
  problem: string | null;
}

export const initialState: AnalyticsState = {
  period: { from: null, to: null },
  analytics: null,
  loading: false,
  problem: null,
};

export const analyticsFeature = createFeature({
  name: 'personalAnalytics',
  reducer: createReducer(
    initialState,
    on(AnalyticsActions.periodSelected, (state, { period }) => ({
      ...state,
      period,
      loading: true,
      problem: null,
    })),
    on(AnalyticsActions.analyticsLoaded, (state, { analytics }) => ({
      ...state,
      analytics,
      loading: false,
      problem: null,
    })),
    on(AnalyticsActions.analyticsLoadFailed, (state, { problem }) => ({
      ...state,
      analytics: null,
      loading: false,
      problem,
    })),
  ),
});
