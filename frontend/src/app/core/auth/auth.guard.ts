import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import {
  canDelegate,
  canReadCompliance,
  canReadDirectory,
  canReadOwnAnalytics,
  canReadUnitAnalytics,
  canReadUniversityAnalytics,
  canReview,
} from './permissions';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  auth.login(state.url);
  return false;
};

/** The user directory needs `user:read:scope` or `user:read:all`; without it the route ends on the forbidden page. */
export const userDirectoryGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReadDirectory(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** Delegations are open to a caller holding an approval role of their own; anybody else is refused. */
export const delegationGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canDelegate(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** The reviewer queue is open to a caller with an approval role, their own or by delegation. */
export const approverGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReview(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** The compliance page is open to the GDPR officer only; anybody else ends on the forbidden page. */
export const complianceGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReadCompliance(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** The personal dashboard needs `analytics:read:own`; without it the route ends on the forbidden page. */
export const analyticsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReadOwnAnalytics(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** Unit analytics need `analytics:read:department`; without it the route ends on the forbidden page. */
export const unitAnalyticsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReadUnitAnalytics(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** University analytics need `analytics:read:university`; without it the route ends on the forbidden page. */
export const universityAnalyticsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReadUniversityAnalytics(auth.permissions()) || router.createUrlTree(['/forbidden']);
};
