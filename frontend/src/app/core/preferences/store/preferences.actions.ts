import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { ColorScheme } from '../../brand/color-scheme.service';
import { DashboardId, DashboardLayout, DashboardPreferences } from '../dashboard-preferences';

export const PreferencesActions = createActionGroup({
  source: 'Preferences',
  events: {
    'Signed In': emptyProps(),
    'Signed Out': emptyProps(),
    'Load Retried': emptyProps(),
    Loaded: props<{ preferences: DashboardPreferences }>(),
    'Load Failed': emptyProps(),
    'Scheme Picked': props<{ scheme: ColorScheme }>(),
    'Layout Changed': props<{ dashboard: DashboardId; layout: DashboardLayout | null }>(),
    Saved: props<{ preferences: DashboardPreferences }>(),
    'Save Failed': props<{ problem: string }>(),
  },
});
