import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { provideAppCharts } from '../../shared/charts/chart-providers';
import { UnitAnalyticsEffects } from '../analytics/units/store/unit-analytics.effects';
import { unitAnalyticsFeature } from '../analytics/units/store/unit-analytics.feature';
import { UniversityAnalyticsEffects } from '../analytics/university/store/university-analytics.effects';
import { universityAnalyticsFeature } from '../analytics/university/store/university-analytics.feature';
import { HallOfFameComponent } from './hall-of-fame.component';

export const HALL_OF_FAME_ROUTES: Routes = [
  {
    path: '',
    title: 'titles.hallOfFame',
    providers: [
      provideState(universityAnalyticsFeature),
      provideState(unitAnalyticsFeature),
      provideEffects(UniversityAnalyticsEffects, UnitAnalyticsEffects),
      provideAppCharts('widgets'),
    ],
    component: HallOfFameComponent,
  },
];
