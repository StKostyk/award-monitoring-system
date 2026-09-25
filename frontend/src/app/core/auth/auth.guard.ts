import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { canDelegate, canReadDirectory } from './permissions';

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
