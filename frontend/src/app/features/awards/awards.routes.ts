import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { approverGuard } from '../../core/auth/auth.guard';
import { AwardCorrectionComponent } from './award-correction/award-correction.component';
import { AwardDetailComponent } from './award-detail/award-detail.component';
import { AwardFormComponent } from './award-form/award-form.component';
import { AwardListComponent } from './award-list/award-list.component';
import { AwardSubmittedComponent } from './award-submitted/award-submitted.component';
import { awardEditGuard, awardEntryGuard, unsavedChangesGuard } from './awards.guards';
import { AwardsEffects } from './store/awards.effects';
import { awardsFeature } from './store/awards.feature';

export const AWARD_ROUTES: Routes = [
  {
    path: '',
    providers: [provideState(awardsFeature), provideEffects(AwardsEffects)],
    children: [
      { path: '', component: AwardListComponent },
      {
        path: 'new',
        component: AwardFormComponent,
        canActivate: [awardEntryGuard],
        canDeactivate: [unsavedChangesGuard],
      },
      {
        path: ':id/edit',
        component: AwardFormComponent,
        canActivate: [awardEditGuard],
        canDeactivate: [unsavedChangesGuard],
      },
      {
        path: ':id/correct',
        component: AwardCorrectionComponent,
        canActivate: [approverGuard],
        canDeactivate: [unsavedChangesGuard],
      },
      { path: ':id/submitted', component: AwardSubmittedComponent },
      { path: ':id', component: AwardDetailComponent },
    ],
  },
];
