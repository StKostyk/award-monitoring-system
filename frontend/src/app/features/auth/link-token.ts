import { inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

/**
 * Reads the one-time token of an email link from the current route and removes it from the address bar, so a
 * reload or a shared URL does not carry the secret. Call from a field initialiser or constructor.
 */
export function consumeLinkToken(): string | null {
  const token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
  if (token) {
    void inject(Router).navigate([], { queryParams: {}, replaceUrl: true });
  }
  return token;
}
