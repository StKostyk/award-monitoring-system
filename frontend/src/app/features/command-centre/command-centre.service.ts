import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef } from '../../core/auth/user-profile';
import { UserPage, UserSummary } from '../admin/users.service';
import { UserRef } from '../reviews/reviews.service';

/** Most colleagues the reminder dialog offers. */
export const MEMBERS_PAGE_SIZE = 100;

/** The caller's review work of the current Kyiv day. */
export interface ReviewDay {
  decided: number;
  since: string;
}

/** A reminder the server accepted for sending. */
export interface UnitReminder {
  unit: OrganizationRef;
  recipient: UserRef;
  sentAt: string;
}

/** The data of the Command centre that no other feature reads: the day's decisions and the reminder. */
@Injectable({ providedIn: 'root' })
export class CommandCentreService {
  private readonly http = inject(HttpClient);

  /** The decisions the caller made today. */
  reviewDay(): Observable<ReviewDay> {
    return this.http.get<ReviewDay>(`${environment.apiUrl}/reviews/today`);
  }

  /** The active members of a department, by name. */
  members(unitId: number): Observable<UserSummary[]> {
    const params = new HttpParams()
      .set('organization', unitId)
      .set('status', 'ACTIVE')
      .set('page', 0)
      .set('size', MEMBERS_PAGE_SIZE);
    return this.http
      .get<UserPage>(`${environment.apiUrl}/users`, { params })
      .pipe(map((page) => page.content));
  }

  /** Reminds an active member of a department without an award this year. */
  remind(unitId: number, recipientId: number): Observable<UnitReminder> {
    return this.http.post<UnitReminder>(
      `${environment.apiUrl}/analytics/units/${unitId}/reminders`,
      { recipientId },
    );
  }
}
