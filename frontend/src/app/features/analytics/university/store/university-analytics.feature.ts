import { createFeature, createReducer, on } from '@ngrx/store';

import { AnalyticsPeriod } from '../../analytics.service';
import { UniversityAnalytics } from '../university-analytics.service';
import { UniversityAnalyticsActions } from './university-analytics.actions';

export interface UniversityAnalyticsState {
  period: AnalyticsPeriod;
  analytics: UniversityAnalytics | null;
  loading: boolean;
  problem: string | null;
}

export const initialState: UniversityAnalyticsState = {
  period: { from: null, to: null },
  analytics: null,
  loading: false,
  problem: null,
};

export const universityAnalyticsFeature = createFeature({
  name: 'universityAnalytics',
  reducer: createReducer(
    initialState,
    on(UniversityAnalyticsActions.periodSelected, (state, { period }) => ({
      ...state,
      period,
      loading: true,
      problem: null,
    })),
    on(UniversityAnalyticsActions.analyticsLoaded, (state, { analytics }) => ({
      ...state,
      analytics,
      loading: false,
      problem: null,
    })),
    on(UniversityAnalyticsActions.analyticsLoadFailed, (state, { problem }) => ({
      ...state,
      analytics: null,
      loading: false,
      problem,
    })),
  ),
});
