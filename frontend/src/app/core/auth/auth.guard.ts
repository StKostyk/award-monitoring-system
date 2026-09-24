import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { canReadDirectory } from './permissions';

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
