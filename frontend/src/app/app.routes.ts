import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { CallbackComponent } from './core/auth/callback.component';
import { ShellComponent } from './core/layout/shell.component';
import { ForgotPasswordComponent } from './features/auth/forgot-password/forgot-password.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { RegistrationPendingComponent } from './features/auth/registration-pending/registration-pending.component';
import { ResetPasswordComponent } from './features/auth/reset-password/reset-password.component';
import { NotMeComponent } from './features/auth/security/not-me/not-me.component';
import { VerifyEmailComponent } from './features/auth/verify-email/verify-email.component';
import { HomeComponent } from './features/home/home.component';

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
      { path: '', component: HomeComponent, pathMatch: 'full', canActivate: [authGuard] },
    ],
  },
  { path: '**', redirectTo: '' },
];
