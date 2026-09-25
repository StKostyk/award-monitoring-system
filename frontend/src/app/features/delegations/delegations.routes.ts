import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { DelegationListComponent } from './delegation-list/delegation-list.component';
import { DelegationsEffects } from './store/delegations.effects';
import { delegationsFeature } from './store/delegations.feature';

export const DELEGATION_ROUTES: Routes = [
  {
    path: '',
    providers: [provideState(delegationsFeature), provideEffects(DelegationsEffects)],
    children: [{ path: '', component: DelegationListComponent }],
  },
];
