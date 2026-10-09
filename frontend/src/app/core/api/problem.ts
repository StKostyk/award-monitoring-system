import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';

/** Why a resource could not be read: a retry may help (`failed`) or cannot (`gone`, `denied`). */
export type ReadProblem = 'failed' | 'gone' | 'denied';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}

/** One refused field of a problem answer. */
export interface FieldProblem {
  field: string;
  code: string;
  message: string;
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

/**
 * A problem type when it has a message of its own, otherwise the fallback.
 *
 * @param type the problem type, from `problemType`
 * @param known the types with their own message
 * @param fallback the type to show for every other one
 * @returns the type, or the fallback when it is not known
 */
export function knownProblem(type: string, known: readonly string[], fallback = 'unknown'): string {
  return known.includes(type) ? type : fallback;
}

/** HTTP status of an API error, or 0 when the request never reached the server. */
export function problemStatus(error: unknown): number {
  return error instanceof HttpErrorResponse ? error.status : 0;
}

/**
 * The problem a failed read reports: 403 `denied`, 404 `gone`, anything else `failed`.
 *
 * @param error the failure
 * @returns the problem to show
 */
export function readProblem(error: unknown): ReadProblem {
  const status = problemStatus(error);
  if (status === HttpStatusCode.Forbidden) {
    return 'denied';
  }
  return status === HttpStatusCode.NotFound ? 'gone' : 'failed';
}

/** The field errors of a 422 answer, empty when there are none. */
export function fieldProblems(error: unknown): FieldProblem[] {
  const body = (error as { error?: { errors?: unknown } } | null)?.error;
  return Array.isArray(body?.errors) ? (body.errors as FieldProblem[]) : [];
}
