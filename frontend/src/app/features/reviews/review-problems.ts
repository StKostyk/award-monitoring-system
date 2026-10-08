import { HttpErrorResponse } from '@angular/common/http';

import { UserRef } from './reviews.service';

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
