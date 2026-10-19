import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { AnalyticsPeriod, DashboardWidget, PersonalAnalytics } from '../analytics.service';

export const AnalyticsActions = createActionGroup({
  source: 'Personal analytics',
  events: {
    Opened: emptyProps(),
    'Period Selected': props<{ period: AnalyticsPeriod }>(),
    'Analytics Loaded': props<{ analytics: PersonalAnalytics }>(),
    'Analytics Load Failed': props<{ problem: string }>(),
    'Preferences Loaded': props<{ hidden: DashboardWidget[] }>(),
    'Preferences Load Failed': emptyProps(),
    'Save Requested': props<{ hidden: DashboardWidget[] }>(),
    Saved: props<{ hidden: DashboardWidget[] }>(),
    'Save Failed': props<{ problem: string }>(),
  },
});
