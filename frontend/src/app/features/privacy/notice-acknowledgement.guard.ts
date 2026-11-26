import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { ConsentsService } from './consents.service';

/** Pages that stay open without an acknowledgement of the current notice. */
const EXEMPT = ['/privacy-notice', '/privacy', '/forbidden', '/not-found', '/offline'];

/**
 * Sends a signed-in person who has not acknowledged the current privacy notice to the acknowledgement page.
 * The overview failing to load lets the navigation pass: the API does not depend on the acknowledgement.
 */
export const noticeAcknowledgementGuard: CanActivateChildFn = async (_route, state) => {
  const auth = inject(AuthService);
  const consents = inject(ConsentsService);
  const router = inject(Router);
  if (!auth.isAuthenticated() || exempt(state.url)) {
    return true;
  }
  try {
    const overview = await consents.overview();
    return (
      !overview.notice.acknowledgementRequired ||
      router.createUrlTree(['/privacy-notice'], {
        queryParams: { acknowledge: true, returnUrl: state.url },
      })
    );
  } catch {
    return true;
  }
};

function exempt(url: string): boolean {
  const path = url.split(/[?#]/)[0];
  return EXEMPT.some((page) => path === page || path.startsWith(`${page}/`));
}
