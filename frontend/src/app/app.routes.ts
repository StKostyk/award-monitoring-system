import { Routes } from '@angular/router';

import {
  approverGuard,
  authGuard,
  delegationGuard,
  userDirectoryGuard,
} from './core/auth/auth.guard';
import { CallbackComponent } from './core/auth/callback.component';
import { ShellComponent } from './core/layout/shell.component';
import { ForgotPasswordComponent } from './features/auth/forgot-password/forgot-password.component';
import { ownAwardsGuard } from './features/awards/awards.guards';
import { RegisterComponent } from './features/auth/register/register.component';
import { RegistrationPendingComponent } from './features/auth/registration-pending/registration-pending.component';
import { ResetPasswordComponent } from './features/auth/reset-password/reset-password.component';
import { NotMeComponent } from './features/auth/security/not-me/not-me.component';
import { VerifyEmailComponent } from './features/auth/verify-email/verify-email.component';
import { ForbiddenComponent } from './features/forbidden/forbidden.component';
import { HomeComponent } from './features/home/home.component';
import { NotFoundComponent } from './features/not-found/not-found.component';
import { ConfirmEmailChangeComponent } from './features/profile/confirm-email-change/confirm-email-change.component';
import { ProfileComponent } from './features/profile/profile/profile.component';

const achievementList = () =>
  import('./features/achievements/achievement-list/achievement-list.component').then(
    (m) => m.AchievementListComponent,
  );

export const routes: Routes = [
  { path: 'callback', component: CallbackComponent },
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: 'register', component: RegisterComponent },
      { path: 'registration-pending', component: RegistrationPendingComponent },
      { path: 'verify-email', component: VerifyEmailComponent },
      { path: 'forgot-password', component: ForgotPasswordComponent },
      { path: 'reset-password', component: ResetPasswordComponent },
      { path: 'security/not-me', component: NotMeComponent },
      { path: 'confirm-email-change', component: ConfirmEmailChangeComponent },
      { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
      { path: 'forbidden', component: ForbiddenComponent, canActivate: [authGuard] },
      {
        path: 'admin',
        canActivate: [authGuard, userDirectoryGuard],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      {
        path: 'awards',
        canActivate: [authGuard, ownAwardsGuard],
        loadChildren: () => import('./features/awards/awards.routes').then((m) => m.AWARD_ROUTES),
      },
      {
        path: 'achievements',
        canActivate: [authGuard],
        loadChildren: () =>
          import('./features/achievements/achievements.routes').then((m) => m.ACHIEVEMENT_ROUTES),
      },
      {
        path: 'units/:id/achievements',
        canActivate: [authGuard],
        loadComponent: achievementList,
      },
      { path: 'public/achievements', loadComponent: achievementList, data: { scope: 'public' } },
      {
        path: 'public/units/:id/achievements',
        loadComponent: achievementList,
        data: { scope: 'public' },
      },
      { path: 'not-found', component: NotFoundComponent },
      {
        path: 'reviews',
        canActivate: [authGuard, approverGuard],
        loadChildren: () =>
          import('./features/reviews/reviews.routes').then((m) => m.REVIEW_ROUTES),
      },
      {
        path: 'delegations',
        canActivate: [authGuard, delegationGuard],
        loadChildren: () =>
          import('./features/delegations/delegations.routes').then((m) => m.DELEGATION_ROUTES),
      },
      { path: '', component: HomeComponent, pathMatch: 'full', canActivate: [authGuard] },
    ],
  },
  { path: '**', redirectTo: '' },
];
