import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { universityAnalyticsGuard } from '../../../core/auth/auth.guard';
import { provideAppCharts } from '../../../shared/charts/chart-providers';
import { UniversityAnalyticsEffects } from '../university/store/university-analytics.effects';
import { universityAnalyticsFeature } from '../university/store/university-analytics.feature';
import { UniversityAnalyticsComponent } from '../university/university-analytics/university-analytics.component';
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
      provideAppCharts(),
    ],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'titles.analytics',
        component: UnitAnalyticsEntryComponent,
      },
      {
        path: 'university',
        title: 'titles.university',
        canActivate: [universityAnalyticsGuard],
        providers: [
          provideState(universityAnalyticsFeature),
          provideEffects(UniversityAnalyticsEffects),
        ],
        component: UniversityAnalyticsComponent,
      },
      { path: 'units/:id', title: 'titles.unitAnalytics', component: UnitAnalyticsComponent },
    ],
  },
];
