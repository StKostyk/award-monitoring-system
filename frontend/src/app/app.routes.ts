import { Routes } from '@angular/router';

import {
  analyticsGuard,
  approverGuard,
  authGuard,
  complianceGuard,
  delegationGuard,
  unitAnalyticsGuard,
  userDirectoryGuard,
} from './core/auth/auth.guard';
import { CallbackComponent } from './core/auth/callback.component';
import { ShellComponent } from './core/layout/shell.component';
import { ownAwardsGuard } from './features/awards/awards.guards';
import { ForbiddenComponent } from './features/forbidden/forbidden.component';
import { NotFoundComponent } from './features/not-found/not-found.component';
import { OfflineComponent } from './features/offline/offline.component';
import { noticeAcknowledgementGuard } from './features/privacy/notice-acknowledgement.guard';

const achievementList = () =>
  import('./features/achievements/achievement-list/achievement-list.component').then(
    (m) => m.AchievementListComponent,
  );

export const routes: Routes = [
  { path: 'callback', title: 'titles.signingIn', component: CallbackComponent },
  {
    path: '',
    component: ShellComponent,
    canActivateChild: [noticeAcknowledgementGuard],
    children: [
      {
        path: 'register',
        title: 'titles.register',
        loadComponent: () =>
          import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
      },
      {
        path: 'registration-pending',
        title: 'titles.registrationPending',
        loadComponent: () =>
          import('./features/auth/registration-pending/registration-pending.component').then(
            (m) => m.RegistrationPendingComponent,
          ),
      },
      {
        path: 'verify-email',
        title: 'titles.verifyEmail',
        loadComponent: () =>
          import('./features/auth/verify-email/verify-email.component').then(
            (m) => m.VerifyEmailComponent,
          ),
      },
      {
        path: 'forgot-password',
        title: 'titles.forgotPassword',
        loadComponent: () =>
          import('./features/auth/forgot-password/forgot-password.component').then(
            (m) => m.ForgotPasswordComponent,
          ),
      },
      {
        path: 'reset-password',
        title: 'titles.resetPassword',
        loadComponent: () =>
          import('./features/auth/reset-password/reset-password.component').then(
            (m) => m.ResetPasswordComponent,
          ),
      },
      {
        path: 'security/not-me',
        title: 'titles.notMe',
        loadComponent: () =>
          import('./features/auth/security/not-me/not-me.component').then((m) => m.NotMeComponent),
      },
      {
        path: 'confirm-email-change',
        title: 'titles.confirmEmailChange',
        loadComponent: () =>
          import('./features/profile/confirm-email-change/confirm-email-change.component').then(
            (m) => m.ConfirmEmailChangeComponent,
          ),
      },
      {
        path: 'profile',
        title: 'titles.profile',
        loadComponent: () =>
          import('./features/profile/profile/profile.component').then((m) => m.ProfileComponent),
        canActivate: [authGuard],
      },
      {
        path: 'forbidden',
        title: 'titles.forbidden',
        component: ForbiddenComponent,
        canActivate: [authGuard],
      },
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
        path: 'dashboard',
        canActivate: [authGuard, analyticsGuard],
        loadChildren: () =>
          import('./features/analytics/analytics.routes').then((m) => m.ANALYTICS_ROUTES),
      },
      {
        path: 'analytics',
        canActivate: [authGuard, unitAnalyticsGuard],
        loadChildren: () =>
          import('./features/analytics/units/unit-analytics.routes').then(
            (m) => m.UNIT_ANALYTICS_ROUTES,
          ),
      },
      {
        path: 'achievements',
        canActivate: [authGuard],
        loadChildren: () =>
          import('./features/achievements/achievements.routes').then((m) => m.ACHIEVEMENT_ROUTES),
      },
      {
        path: 'units/:id/achievements',
        title: 'titles.unitAchievements',
        canActivate: [authGuard],
        loadComponent: achievementList,
      },
      {
        path: 'public/achievements',
        title: 'titles.achievements',
        loadComponent: achievementList,
        data: { scope: 'public' },
      },
      {
        path: 'public/units/:id/achievements',
        title: 'titles.unitAchievements',
        loadComponent: achievementList,
        data: { scope: 'public' },
      },
      { path: 'not-found', title: 'titles.notFound', component: NotFoundComponent },
      { path: 'offline', title: 'titles.offline', component: OfflineComponent },
      {
        path: 'privacy-notice',
        title: 'titles.privacyNotice',
        loadComponent: () =>
          import('./features/privacy/privacy-notice/privacy-notice.component').then(
            (m) => m.PrivacyNoticeComponent,
          ),
      },
      {
        path: 'privacy',
        title: 'titles.privacy',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/privacy/privacy-centre/privacy-centre.component').then(
            (m) => m.PrivacyCentreComponent,
          ),
      },
      {
        path: 'notifications',
        title: 'titles.notifications',
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
      {
        path: 'compliance',
        canActivate: [authGuard, complianceGuard],
        loadChildren: () =>
          import('./features/compliance/compliance.routes').then((m) => m.COMPLIANCE_ROUTES),
      },
      {
        path: '',
        title: 'titles.home',
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
        pathMatch: 'full',
        canActivate: [authGuard],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
