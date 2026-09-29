import { HttpErrorResponse } from '@angular/common/http';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}

const TYPE_PREFIX = 'urn:awards:problem:';
/** No answer at all, or the proxy's answer while the application is down or restarting. */
const UNREACHABLE = [0, 502, 503, 504];

/** The short problem type of an API error, e.g. `email-taken`, `network` when the server is out of reach, or `unknown`. */
export function problemType(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ProblemDetail | null;
    const type = typeof body === 'object' ? (body?.type ?? '') : '';
    if (type.startsWith(TYPE_PREFIX)) {
      return type.substring(TYPE_PREFIX.length);
    }
    if (UNREACHABLE.includes(error.status)) {
      return 'network';
    }
  }
  return 'unknown';
}

/** HTTP status of an API error, or 0 when the request never reached the server. */
export function problemStatus(error: unknown): number {
  return error instanceof HttpErrorResponse ? error.status : 0;
}
