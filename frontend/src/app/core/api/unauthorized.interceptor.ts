import { HttpErrorResponse, HttpInterceptorFn, HttpStatusCode } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

/** A 401 from the API while signed in means the session was ended elsewhere; sign in again instead of failing quietly. */
export const unauthorizedInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  return next(request).pipe(
    catchError((err: unknown) => {
      if (
        err instanceof HttpErrorResponse &&
        err.status === HttpStatusCode.Unauthorized &&
        request.url.startsWith(environment.apiUrl)
      ) {
        auth.signedOutElsewhere();
      }
      return throwError(() => err);
    }),
  );
};
