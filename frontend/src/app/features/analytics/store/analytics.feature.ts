import { createFeature, createReducer, on } from '@ngrx/store';

import { AnalyticsPeriod, DashboardWidget, PersonalAnalytics } from '../analytics.service';
import { AnalyticsActions } from './analytics.actions';

export interface AnalyticsState {
  period: AnalyticsPeriod;
  analytics: PersonalAnalytics | null;
  loading: boolean;
  problem: string | null;
  hidden: DashboardWidget[];
  saving: boolean;
  saveProblem: string | null;
}

export const initialState: AnalyticsState = {
  period: { from: null, to: null },
  analytics: null,
  loading: false,
  problem: null,
  hidden: [],
  saving: false,
  saveProblem: null,
};

export const analyticsFeature = createFeature({
  name: 'personalAnalytics',
  reducer: createReducer(
    initialState,
    on(AnalyticsActions.opened, (state) => ({ ...state, saveProblem: null })),
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
    on(AnalyticsActions.preferencesLoaded, (state, { hidden }) => ({ ...state, hidden })),
    on(AnalyticsActions.saveRequested, (state) => ({ ...state, saving: true, saveProblem: null })),
    on(AnalyticsActions.saved, (state, { hidden }) => ({ ...state, hidden, saving: false })),
    on(AnalyticsActions.saveFailed, (state, { problem }) => ({
      ...state,
      saving: false,
      saveProblem: problem,
    })),
  ),
});
