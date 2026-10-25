import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { AnalyticsPeriod } from '../../analytics.service';
import { ComplianceSummary, UniversityAnalytics } from '../university-analytics.service';

export const UniversityAnalyticsActions = createActionGroup({
  source: 'University analytics',
  events: {
    'Period Selected': props<{ period: AnalyticsPeriod }>(),
    'Analytics Loaded': props<{ analytics: UniversityAnalytics }>(),
    'Analytics Load Failed': props<{ problem: string }>(),
    'Compliance Requested': emptyProps(),
    'Compliance Loaded': props<{ compliance: ComplianceSummary }>(),
    'Compliance Load Failed': props<{ problem: string }>(),
  },
});
