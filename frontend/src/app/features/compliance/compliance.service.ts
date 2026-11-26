import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../../shared/page';
import { UserRef } from '../reviews/reviews.service';

export type RetentionAction = 'delete' | 'erase-account' | 'create-partitions' | 'drop-partitions';

export type RetentionRunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'INTERRUPTED';

export type ErasureStatus = 'PENDING' | 'CANCELLED' | 'COMPLETED';

export type ErasureOrigin = 'REQUEST' | 'RETENTION';

/** One retention policy, in the order the nightly run applies it. */
export interface RetentionPolicy {
  key: string;
  period: string;
  action: RetentionAction;
  order: number;
}

/** What one policy did in one run. */
export interface RetentionResult {
  policy: string;
  affected: number;
  held: number;
  failed: boolean;
  error: string | null;
}

/** One nightly retention run with the results of its policies. */
export interface RetentionRun {
  id: number;
  status: RetentionRunStatus;
  startedAt: string;
  finishedAt: string | null;
  results: RetentionResult[];
}

/** One erasure request of any origin; the person is the pseudonym once erased. */
export interface ErasureEntry {
  id: number;
  person: UserRef;
  origin: ErasureOrigin;
  status: ErasureStatus;
  requestedAt: string;
  scheduledFor: string;
  postponedAt: string | null;
  cancelledAt: string | null;
  completedAt: string | null;
}

/** One legal hold of any account; the lift fields stay null while it is active. */
export interface HoldEntry {
  id: number;
  person: UserRef;
  reason: string;
  placedAt: string;
  placedBy: UserRef;
  liftedAt: string | null;
  liftedBy: UserRef | null;
  liftReason: string | null;
}

/** One page of a compliance list. */
export interface PageRequest {
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class ComplianceService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/compliance`;

  policies(): Observable<RetentionPolicy[]> {
    return this.http.get<RetentionPolicy[]>(`${this.base}/retention-policies`);
  }

  runs(request: PageRequest): Observable<Page<RetentionRun>> {
    return this.http.get<Page<RetentionRun>>(`${this.base}/retention-runs`, {
      params: paging(request),
    });
  }

  erasureRequests(
    request: PageRequest,
    status: ErasureStatus | null,
  ): Observable<Page<ErasureEntry>> {
    const params = paging(request);
    return this.http.get<Page<ErasureEntry>>(`${this.base}/erasure-requests`, {
      params: status ? params.set('status', status) : params,
    });
  }

  legalHolds(request: PageRequest, active: boolean): Observable<Page<HoldEntry>> {
    const params = paging(request);
    return this.http.get<Page<HoldEntry>>(`${this.base}/legal-holds`, {
      params: active ? params.set('active', true) : params,
    });
  }
}

function paging(request: PageRequest): HttpParams {
  return new HttpParams().set('page', request.page).set('size', request.size);
}
