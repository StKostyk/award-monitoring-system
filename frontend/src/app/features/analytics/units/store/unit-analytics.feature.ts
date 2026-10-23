import { createFeature, createReducer, on } from '@ngrx/store';

import { AnalyticsPeriod } from '../../analytics.service';
import { AnalysableUnit, UnitAnalytics } from '../unit-analytics.service';
import { UnitAnalyticsActions } from './unit-analytics.actions';

export interface UnitAnalyticsState {
  units: AnalysableUnit[];
  unitsStatus: 'idle' | 'loading' | 'loaded' | 'failed';
  unitId: number | null;
  period: AnalyticsPeriod;
  analytics: UnitAnalytics | null;
  loading: boolean;
  problem: string | null;
}

export const initialState: UnitAnalyticsState = {
  units: [],
  unitsStatus: 'idle',
  unitId: null,
  period: { from: null, to: null },
  analytics: null,
  loading: false,
  problem: null,
};

export const unitAnalyticsFeature = createFeature({
  name: 'unitAnalytics',
  reducer: createReducer(
    initialState,
    on(UnitAnalyticsActions.unitsRequested, (state) => ({
      ...state,
      unitsStatus: 'loading' as const,
    })),
    on(UnitAnalyticsActions.unitsLoaded, (state, { units }) => ({
      ...state,
      units,
      unitsStatus: 'loaded' as const,
    })),
    on(UnitAnalyticsActions.unitsLoadFailed, (state) => ({
      ...state,
      units: [],
      unitsStatus: 'failed' as const,
    })),
    on(UnitAnalyticsActions.unitSelected, (state, { unitId, period }) => ({
      ...state,
      unitId,
      period,
      analytics: state.unitId === unitId ? state.analytics : null,
      loading: true,
      problem: null,
    })),
    on(UnitAnalyticsActions.analyticsLoaded, (state, { analytics }) => ({
      ...state,
      analytics,
      loading: false,
      problem: null,
    })),
    on(UnitAnalyticsActions.analyticsLoadFailed, (state, { problem }) => ({
      ...state,
      analytics: null,
      loading: false,
      problem,
    })),
  ),
});
