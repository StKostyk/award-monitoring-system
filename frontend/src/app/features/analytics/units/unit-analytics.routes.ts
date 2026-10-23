import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { provideEchartsCore } from 'ngx-echarts';

import { UnitAnalyticsEffects } from './store/unit-analytics.effects';
import { unitAnalyticsFeature } from './store/unit-analytics.feature';
import { UnitAnalyticsEntryComponent } from './unit-analytics-entry/unit-analytics-entry.component';
import { UnitAnalyticsComponent } from './unit-analytics/unit-analytics.component';

export const UNIT_ANALYTICS_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState(unitAnalyticsFeature),
      provideEffects(UnitAnalyticsEffects),
      provideEchartsCore({
        echarts: () => import('../../../shared/charts/echarts-setup').then((m) => m.echarts),
      }),
    ],
    children: [
      { path: '', pathMatch: 'full', component: UnitAnalyticsEntryComponent },
      { path: 'units/:id', component: UnitAnalyticsComponent },
    ],
  },
];
