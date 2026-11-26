import {
  HttpErrorResponse,
  HttpEvent,
  HttpInterceptorFn,
  HttpRequest,
  HttpStatusCode,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

/**
 * A 401 from the API first gets one token refresh and a repeat of the request, so an expired or re-signed access
 * token does not end the session; if the refresh is refused or the repeat is refused too, the session was ended
 * elsewhere and the user signs in again. A refresh that fails for another reason (network, server restart) only
 * fails the request.
 */
export const unauthorizedInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const send = (
    outgoing: HttpRequest<unknown>,
    repeated: boolean,
  ): Observable<HttpEvent<unknown>> =>
    next(outgoing).pipe(
      catchError((err: unknown) => {
        if (!refusedByApi(err, outgoing)) {
          return throwError(() => err);
        }
        if (repeated) {
          auth.signedOutElsewhere();
          return throwError(() => err);
        }
        const refusedToken = outgoing.headers.get('Authorization')?.replace(/^Bearer /, '') ?? null;
        return from(auth.refreshOnce(refusedToken)).pipe(
          catchError(() => throwError(() => err)),
          switchMap((token) => {
            if (token) {
              return send(
                outgoing.clone({ setHeaders: { Authorization: `Bearer ${token}` } }),
                true,
              );
            }
            auth.signedOutElsewhere();
            return throwError(() => err);
          }),
        );
      }),
    );
  return send(request, false);
};

function refusedByApi(err: unknown, request: HttpRequest<unknown>): boolean {
  return (
    err instanceof HttpErrorResponse &&
    err.status === HttpStatusCode.Unauthorized &&
    request.url.startsWith(environment.apiUrl)
  );
}
