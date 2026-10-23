import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { AnalyticsPeriod } from '../../analytics.service';
import { AnalysableUnit, UnitAnalytics } from '../unit-analytics.service';

export const UnitAnalyticsActions = createActionGroup({
  source: 'Unit analytics',
  events: {
    'Units Requested': emptyProps(),
    'Units Loaded': props<{ units: AnalysableUnit[] }>(),
    'Units Load Failed': props<{ problem: string }>(),
    'Unit Selected': props<{ unitId: number; period: AnalyticsPeriod }>(),
    'Analytics Loaded': props<{ analytics: UnitAnalytics }>(),
    'Analytics Load Failed': props<{ problem: string }>(),
  },
});
