import { HttpErrorResponse } from '@angular/common/http';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}

const TYPE_PREFIX = 'urn:awards:problem:';

/** The short problem type of an API error, e.g. `email-taken`, or `unknown`. */
export function problemType(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ProblemDetail | null;
    const type = body?.type ?? '';
    if (type.startsWith(TYPE_PREFIX)) {
      return type.substring(TYPE_PREFIX.length);
    }
    if (error.status === 0) {
      return 'network';
    }
  }
  return 'unknown';
}

/** HTTP status of an API error, or 0 when the request never reached the server. */
export function problemStatus(error: unknown): number {
  return error instanceof HttpErrorResponse ? error.status : 0;
}
