import { createFeature, createReducer, on } from '@ngrx/store';

import { DashboardLayouts, StoredColorScheme, toStoredColorScheme } from '../dashboard-preferences';
import { PreferencesActions } from './preferences.actions';

export interface PreferencesState {
  status: 'signed-out' | 'loading' | 'loaded' | 'failed';
  colorScheme: StoredColorScheme;
  layouts: DashboardLayouts;
  saveProblem: string | null;
}

export const initialState: PreferencesState = {
  status: 'signed-out',
  colorScheme: 'SYSTEM',
  layouts: {},
  saveProblem: null,
};

export const preferencesFeature = createFeature({
  name: 'preferences',
  reducer: createReducer(
    initialState,
    on(PreferencesActions.signedIn, PreferencesActions.loadRetried, (state) => ({
      ...state,
      status: 'loading' as const,
      saveProblem: null,
    })),
    on(PreferencesActions.signedOut, () => initialState),
    on(PreferencesActions.loaded, (state, { preferences }) => ({
      ...state,
      status: 'loaded' as const,
      colorScheme: preferences.colorScheme,
      layouts: preferences.layouts,
    })),
    on(PreferencesActions.loadFailed, (state) => ({ ...state, status: 'failed' as const })),
    on(PreferencesActions.schemePicked, (state, { scheme }) => ({
      ...state,
      colorScheme: toStoredColorScheme(scheme),
    })),
    on(PreferencesActions.layoutChanged, (state, { dashboard, layout }) => {
      const layouts = { ...state.layouts };
      if (layout) {
        layouts[dashboard] = layout;
      } else {
        delete layouts[dashboard];
      }
      return { ...state, layouts };
    }),
    on(PreferencesActions.saved, (state) => ({ ...state, saveProblem: null })),
    on(PreferencesActions.saveFailed, (state, { problem }) => ({ ...state, saveProblem: problem })),
  ),
});
