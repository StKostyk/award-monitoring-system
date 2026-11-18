import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { ConnectivityService } from '../connectivity/connectivity.service';
import { AuthService } from './auth.service';
import {
  canDelegate,
  canManagePeople,
  canOpenCommandCentre,
  canOpenHallOfFame,
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
  if (!inject(ConnectivityService).online()) {
    return auth.offlinePage(state.url);
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

/** The spreadsheet import is open to a faculty secretary or dean, own or delegated; anybody else is refused. */
export const peopleGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canManagePeople(auth.permissions()) || router.createUrlTree(['/forbidden']);
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

/** The Hall of Fame needs a rector or dean role held, not borrowed; otherwise the route ends on the forbidden page. */
export const hallOfFameGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canOpenHallOfFame(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** The Command centre needs a secretary or dean role held, not borrowed; otherwise the route ends on the forbidden page. */
export const commandCentreGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canOpenCommandCentre(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/**
 * The home of a rector or dean is the Hall of Fame, of a faculty or rector's secretary the Command centre, of
 * anybody with personal analytics the personal dashboard; an account without any, such as one waiting for its
 * membership, stays on the home page.
 */
export const roleHomeGuard: CanActivateFn = () => {
  const permissions = inject(AuthService).permissions();
  const router = inject(Router);
  if (canOpenHallOfFame(permissions)) {
    return router.createUrlTree(['/hall-of-fame']);
  }
  if (canOpenCommandCentre(permissions)) {
    return router.createUrlTree(['/command-centre']);
  }
  return !canReadOwnAnalytics(permissions) || router.createUrlTree(['/dashboard']);
};

/** University analytics need `analytics:read:university`; without it the route ends on the forbidden page. */
export const universityAnalyticsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReadUniversityAnalytics(auth.permissions()) || router.createUrlTree(['/forbidden']);
};
