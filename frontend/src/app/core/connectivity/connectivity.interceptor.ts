import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ConnectivityService } from './connectivity.service';

/** Paths of the authorization server, which shares the origin of the application. */
const AUTH_PATHS = /^\/(oauth2|connect|\.well-known)\//;

/**
 * Reports the outcome of every backend request: a successful answer marks the server reachable, a request without
 * any answer (status 0) marks it unreachable. Files of the application shell are left out, since the service worker
 * may answer them from its cache. Backend requests bypass the service worker, which caches none of them and would
 * turn a missing answer into a 504 of its own.
 */
export const connectivityInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isBackend(request.url)) {
    return next(request);
  }
  const connectivity = inject(ConnectivityService);
  const sent = workerControlled()
    ? request.clone({ setHeaders: { 'ngsw-bypass': 'true' } })
    : request;
  return next(sent).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          connectivity.reachable();
        }
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 0) {
          connectivity.unreachable();
        }
      },
    }),
  );
};

function workerControlled(): boolean {
  return typeof navigator !== 'undefined' && !!navigator.serviceWorker?.controller;
}

function isBackend(url: string): boolean {
  if (url.startsWith(environment.apiUrl)) {
    return true;
  }
  const path = url.startsWith(environment.issuer) ? url.substring(environment.issuer.length) : url;
  return AUTH_PATHS.test(path);
}
