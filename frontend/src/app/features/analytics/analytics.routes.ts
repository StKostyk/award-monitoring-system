import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { provideAppCharts } from '../../shared/charts/chart-providers';
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
      provideAppCharts(),
    ],
    children: [
      { path: '', title: 'titles.dashboard', component: DashboardComponent },
      { path: 'summary', title: 'titles.summary', component: SummaryComponent },
    ],
  },
];
