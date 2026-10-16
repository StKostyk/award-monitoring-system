import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../../shared/page';

export type Visibility = 'PRIVATE' | 'UNIVERSITY' | 'PUBLIC';

export interface PrivacySummary {
  awardsByVisibility: Record<Visibility, number>;
  documentsShown: number;
  emailOffTypes: string[];
  lastExportAt: string | null;
  erasure: ErasureRequest | null;
}

/** A pending request to erase the own account. */
export interface ErasureRequest {
  id: number;
  requestedAt: string;
  scheduledFor: string;
  postponed: boolean;
}

export interface ActivityEntry {
  action: string;
  occurredAt: string;
  entityType: string;
  entityId: number | null;
  byMe: boolean;
}

@Injectable({ providedIn: 'root' })
export class PrivacyCentreService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/users/me`;
  private readonly pending = signal<ErasureRequest | null>(null);

  /** The pending erasure request of the signed-in person, as last read or changed. */
  readonly erasure = this.pending.asReadonly();

  summary(): Observable<PrivacySummary> {
    return this.http
      .get<PrivacySummary>(`${this.base}/privacy`)
      .pipe(tap((summary) => this.pending.set(summary.erasure)));
  }

  /**
   * Asks for the erasure of the own account after the grace period.
   *
   * @param password the current password
   * @returns the pending request
   */
  requestErasure(password: string): Observable<ErasureRequest> {
    return this.http
      .post<ErasureRequest>(`${this.base}/erasure-request`, { password })
      .pipe(tap((request) => this.pending.set(request)));
  }

  cancelErasure(): Observable<void> {
    return this.http
      .post<void>(`${this.base}/erasure-request/cancel`, null)
      .pipe(tap(() => this.pending.set(null)));
  }

  /** Forgets the pending request, e.g. after signing out. */
  forgetErasure(): void {
    this.pending.set(null);
  }

  /**
   * One page of the personal activity log, newest first.
   *
   * @param page the page index
   * @param size the page size
   * @returns the page of entries
   */
  activity(page: number, size: number): Observable<Page<ActivityEntry>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ActivityEntry>>(`${this.base}/activity`, { params });
  }
}
