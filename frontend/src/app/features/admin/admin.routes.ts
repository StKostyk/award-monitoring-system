import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { AdminUsersEffects } from './store/admin-users.effects';
import { adminUsersFeature } from './store/admin-users.feature';
import { UserDetailComponent } from './user-detail/user-detail.component';
import { UserListComponent } from './user-list/user-list.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    providers: [provideState(adminUsersFeature), provideEffects(AdminUsersEffects)],
    children: [
      { path: 'users', component: UserListComponent },
      { path: 'users/:id', component: UserDetailComponent },
      { path: '', redirectTo: 'users', pathMatch: 'full' },
    ],
  },
];
