import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../../shared/page';

export type NotificationType =
  | 'AWARD_APPROVED'
  | 'AWARD_REJECTED'
  | 'AWARD_RETURNED'
  | 'AWARD_ESCALATED'
  | 'AWARD_CORRECTED'
  | 'REVIEW_OVERDUE'
  | 'REVIEW_TAKEN_OVER'
  | 'ROLE_ASSIGNED'
  | 'ROLE_REVOKED'
  | 'DELEGATION_CREATED'
  | 'DELEGATION_REVOKED';

export const NOTIFICATION_TYPES: readonly NotificationType[] = [
  'AWARD_APPROVED',
  'AWARD_REJECTED',
  'AWARD_RETURNED',
  'AWARD_ESCALATED',
  'AWARD_CORRECTED',
  'REVIEW_OVERDUE',
  'REVIEW_TAKEN_OVER',
  'ROLE_ASSIGNED',
  'ROLE_REVOKED',
  'DELEGATION_CREATED',
  'DELEGATION_REVOKED',
];

/** One in-app notification; the payload fields depend on the type. */
export interface AppNotification {
  id: number;
  type: NotificationType;
  payload: Record<string, unknown>;
  referenceType: string | null;
  referenceId: number | null;
  read: boolean;
  sentAt: string;
}

/** The caller's in-app notifications. */
@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/notifications`;

  list(page: number, size: number, unread = false): Observable<Page<AppNotification>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (unread) {
      params = params.set('unread', true);
    }
    return this.http.get<Page<AppNotification>>(this.base, { params });
  }

  unreadCount(): Observable<number> {
    return this.http
      .get<{ unread: number }>(`${this.base}/unread-count`)
      .pipe(map((body) => body.unread));
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/read`, null);
  }

  markAllRead(): Observable<number> {
    return this.http
      .post<{ marked: number }>(`${this.base}/read-all`, null)
      .pipe(map((body) => body.marked));
  }
}
