import { inject } from '@angular/core';
import { CanActivateFn, CanDeactivateFn, Router } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { canCreateAwards, canReadOwnAwards } from '../../core/auth/permissions';

/** A page that may hold values nobody saved yet. */
export interface LeavesUnsavedChanges {
  /** Asks before leaving when something is unsaved; resolves true when leaving is fine. */
  confirmLeave(): boolean | Observable<boolean>;
}

/** Own awards need `award:read:own`. */
export const ownAwardsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canReadOwnAwards(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** Entering and editing awards needs `award:create`. */
export const awardEntryGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return canCreateAwards(auth.permissions()) || router.createUrlTree(['/forbidden']);
};

/** Leaving a form with unsaved values asks first. */
export const unsavedChangesGuard: CanDeactivateFn<LeavesUnsavedChanges> = (component) =>
  component.confirmLeave();
