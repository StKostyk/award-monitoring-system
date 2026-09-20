import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { CallbackComponent } from './core/auth/callback.component';
import { ShellComponent } from './core/layout/shell.component';
import { HomeComponent } from './features/home/home.component';

export const routes: Routes = [
  { path: 'callback', component: CallbackComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [{ path: '', component: HomeComponent, pathMatch: 'full' }],
  },
  { path: '**', redirectTo: '' },
];
