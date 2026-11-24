import { createActionGroup, props } from '@ngrx/store';

import { AnalyticsPeriod } from '../../analytics.service';
import { UniversityAnalytics } from '../university-analytics.service';

export const UniversityAnalyticsActions = createActionGroup({
  source: 'University analytics',
  events: {
    'Period Selected': props<{ period: AnalyticsPeriod }>(),
    'Analytics Loaded': props<{ analytics: UniversityAnalytics }>(),
    'Analytics Load Failed': props<{ problem: string }>(),
  },
});
