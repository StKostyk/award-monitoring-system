import { Routes } from '@angular/router';

import { AchievementDetailComponent } from './achievement-detail/achievement-detail.component';
import { AchievementListComponent } from './achievement-list/achievement-list.component';

export const ACHIEVEMENT_ROUTES: Routes = [
  { path: '', title: 'titles.achievements', component: AchievementListComponent },
  { path: ':id', title: 'titles.achievement', component: AchievementDetailComponent },
];
