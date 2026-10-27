import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { ComplianceComponent } from './compliance.component';
import { ComplianceEffects } from './store/compliance.effects';
import { complianceFeature } from './store/compliance.feature';

export const COMPLIANCE_ROUTES: Routes = [
  {
    path: '',
    providers: [provideState(complianceFeature), provideEffects(ComplianceEffects)],
    children: [{ path: '', title: 'titles.compliance', component: ComplianceComponent }],
  },
];
