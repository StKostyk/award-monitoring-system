import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../../shared/page';

export type Visibility = 'PRIVATE' | 'UNIVERSITY' | 'PUBLIC';

export interface PrivacySummary {
  awardsByVisibility: Record<Visibility, number>;
  documentsShown: number;
  emailOffTypes: string[];
  lastExportAt: string | null;
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

  summary(): Observable<PrivacySummary> {
    return this.http.get<PrivacySummary>(`${this.base}/privacy`);
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
