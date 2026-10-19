import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { provideEchartsCore } from 'ngx-echarts';

import { DashboardComponent } from './dashboard/dashboard.component';
import { AnalyticsEffects } from './store/analytics.effects';
import { analyticsFeature } from './store/analytics.feature';
import { SummaryComponent } from './summary/summary.component';

export const ANALYTICS_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState(analyticsFeature),
      provideEffects(AnalyticsEffects),
      provideEchartsCore({
        echarts: () => import('../../shared/charts/echarts-setup').then((m) => m.echarts),
      }),
    ],
    children: [
      { path: '', component: DashboardComponent },
      { path: 'summary', component: SummaryComponent },
    ],
  },
];
