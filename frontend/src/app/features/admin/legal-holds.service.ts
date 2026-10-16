import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { UserRef } from '../reviews/reviews.service';

/** Shortest reason the server accepts for placing or lifting a hold. */
export const HOLD_REASON_MIN_LENGTH = 10;

/** Longest reason the server accepts for placing or lifting a hold. */
export const HOLD_REASON_MAX_LENGTH = 500;

/** One legal hold on an account; the lift fields stay null while it is active. */
export interface LegalHold {
  id: number;
  reason: string;
  placedAt: string;
  placedBy: UserRef;
  liftedAt: string | null;
  liftedBy: UserRef | null;
  liftReason: string | null;
}

/** The holds of one account, newest first, and the due date of a pending erasure. */
export interface LegalHoldOverview {
  holds: LegalHold[];
  erasureScheduledFor: string | null;
}

@Injectable({ providedIn: 'root' })
export class LegalHoldsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/users`;

  list(userId: number): Observable<LegalHoldOverview> {
    return this.http.get<LegalHoldOverview>(`${this.base}/${userId}/legal-holds`);
  }

  place(userId: number, reason: string): Observable<LegalHold> {
    return this.http.post<LegalHold>(`${this.base}/${userId}/legal-holds`, { reason });
  }

  lift(userId: number, holdId: number, reason: string): Observable<LegalHold> {
    return this.http.post<LegalHold>(`${this.base}/${userId}/legal-holds/${holdId}/lift`, {
      reason,
    });
  }
}
