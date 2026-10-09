import { HttpErrorResponse } from '@angular/common/http';

import { knownProblem } from '../../core/api/problem';
import { UserRef } from './reviews.service';

/** Problem types with a message of their own under `reviews.problems`. */
export const REVIEW_PROBLEMS: readonly string[] = [
  'reviewer-not-eligible',
  'no-higher-level',
  'validation-failed',
  'access-denied',
  'network',
];

/**
 * The reviewer a `request-claimed` answer names.
 *
 * @param error the failure
 * @returns the holder, or null when the answer names none
 */
export function reviewerOf(error: unknown): UserRef | null {
  const body = error instanceof HttpErrorResponse ? (error.error as { reviewer?: UserRef }) : null;
  return body?.reviewer ?? null;
}

/**
 * The translation key of a review problem, `reviews.problems.unknown` when it has no message of its own.
 *
 * @param type the problem type
 * @returns the translation key
 */
export function reviewProblemKey(type: string): string {
  return `reviews.problems.${knownProblem(type, REVIEW_PROBLEMS)}`;
}
