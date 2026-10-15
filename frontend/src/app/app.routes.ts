import { Routes } from '@angular/router';

import {
  approverGuard,
  authGuard,
  delegationGuard,
  userDirectoryGuard,
} from './core/auth/auth.guard';
import { CallbackComponent } from './core/auth/callback.component';
import { ShellComponent } from './core/layout/shell.component';
import { ownAwardsGuard } from './features/awards/awards.guards';
import { ForbiddenComponent } from './features/forbidden/forbidden.component';
import { HomeComponent } from './features/home/home.component';
import { NotFoundComponent } from './features/not-found/not-found.component';
import { noticeAcknowledgementGuard } from './features/privacy/notice-acknowledgement.guard';
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
    canActivateChild: [noticeAcknowledgementGuard],
    children: [
      {
        path: 'register',
        loadComponent: () =>
          import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
      },
      {
        path: 'registration-pending',
        loadComponent: () =>
          import('./features/auth/registration-pending/registration-pending.component').then(
            (m) => m.RegistrationPendingComponent,
          ),
      },
      {
        path: 'verify-email',
        loadComponent: () =>
          import('./features/auth/verify-email/verify-email.component').then(
            (m) => m.VerifyEmailComponent,
          ),
      },
      {
        path: 'forgot-password',
        loadComponent: () =>
          import('./features/auth/forgot-password/forgot-password.component').then(
            (m) => m.ForgotPasswordComponent,
          ),
      },
      {
        path: 'reset-password',
        loadComponent: () =>
          import('./features/auth/reset-password/reset-password.component').then(
            (m) => m.ResetPasswordComponent,
          ),
      },
      {
        path: 'security/not-me',
        loadComponent: () =>
          import('./features/auth/security/not-me/not-me.component').then((m) => m.NotMeComponent),
      },
      {
        path: 'confirm-email-change',
        loadComponent: () =>
          import('./features/profile/confirm-email-change/confirm-email-change.component').then(
            (m) => m.ConfirmEmailChangeComponent,
          ),
      },
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
        path: 'privacy-notice',
        loadComponent: () =>
          import('./features/privacy/privacy-notice/privacy-notice.component').then(
            (m) => m.PrivacyNoticeComponent,
          ),
      },
      {
        path: 'notifications',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/notifications/notification-list/notification-list.component').then(
            (m) => m.NotificationListComponent,
          ),
      },
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
