import { createActionGroup, props } from '@ngrx/store';

import { AnalyticsPeriod, PersonalAnalytics } from '../analytics.service';

export const AnalyticsActions = createActionGroup({
  source: 'Personal analytics',
  events: {
    'Period Selected': props<{ period: AnalyticsPeriod }>(),
    'Analytics Loaded': props<{ analytics: PersonalAnalytics }>(),
    'Analytics Load Failed': props<{ problem: string }>(),
  },
});
