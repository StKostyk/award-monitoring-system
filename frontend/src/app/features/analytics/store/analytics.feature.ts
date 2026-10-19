import { createFeature, createReducer, on } from '@ngrx/store';

import { AnalyticsPeriod, DashboardWidget, PersonalAnalytics } from '../analytics.service';
import { AnalyticsActions } from './analytics.actions';

export interface AnalyticsState {
  period: AnalyticsPeriod;
  analytics: PersonalAnalytics | null;
  loading: boolean;
  problem: string | null;
  hidden: DashboardWidget[];
  preferences: 'loading' | 'loaded' | 'failed';
  saving: boolean;
  saveProblem: string | null;
}

export const initialState: AnalyticsState = {
  period: { from: null, to: null },
  analytics: null,
  loading: false,
  problem: null,
  hidden: [],
  preferences: 'loading',
  saving: false,
  saveProblem: null,
};

export const analyticsFeature = createFeature({
  name: 'personalAnalytics',
  reducer: createReducer(
    initialState,
    on(AnalyticsActions.opened, (state) => ({
      ...state,
      preferences: 'loading' as const,
      saveProblem: null,
    })),
    on(AnalyticsActions.preferencesRetried, (state) => ({
      ...state,
      preferences: 'loading' as const,
    })),
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
    on(AnalyticsActions.preferencesLoaded, (state, { hidden }) => ({
      ...state,
      hidden,
      preferences: 'loaded' as const,
    })),
    on(AnalyticsActions.preferencesLoadFailed, (state) => ({
      ...state,
      preferences: 'failed' as const,
    })),
    on(AnalyticsActions.saveRequested, (state) => ({ ...state, saving: true, saveProblem: null })),
    on(AnalyticsActions.saved, (state, { hidden }) => ({ ...state, hidden, saving: false })),
    on(AnalyticsActions.saveFailed, (state, { problem }) => ({
      ...state,
      saving: false,
      saveProblem: problem,
    })),
  ),
});
