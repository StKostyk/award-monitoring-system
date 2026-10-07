import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef } from '../../core/auth/user-profile';
import {
  ApprovalLevel,
  AwardRecipient,
  AwardStatus,
  CategoryRef,
  Page,
  RequestStatus,
} from '../awards/awards.service';

/** A person as the review endpoints name them. */
export interface UserRef {
  id: number;
  name: string;
  email: string;
}

/** One open request in the reviewer queue. */
export interface ReviewItem {
  awardId: number;
  requestId: number;
  requestVersion: number;
  title: string | null;
  titleUk: string | null;
  recipient: AwardRecipient;
  owner: UserRef;
  organization: OrganizationRef | null;
  category: CategoryRef | null;
  level: ApprovalLevel;
  status: RequestStatus;
  reviewer: UserRef | null;
  submittedAt: string;
  deadline: string | null;
  overdue: boolean;
  documentCount: number;
  delegatedFrom: UserRef | null;
}

/** A colleague the request can be handed over to. */
export interface ReviewerCandidate extends UserRef {
  delegated: boolean;
}

export type DecisionType = 'APPROVE' | 'REJECT' | 'RETURN' | 'ESCALATE';

/** What a reviewer decides at the current level of a request. */
export interface DecisionRequest {
  decision: DecisionType;
  requestVersion: number;
  comment?: string;
  verified?: boolean;
}

/** The award and its request after a decision; `level` is where the request now waits or was decided. */
export interface DecisionOutcome {
  awardId: number;
  status: AwardStatus;
  requestStatus: RequestStatus;
  level: ApprovalLevel;
  requestVersion: number;
}

/** Whose requests the queue shows: the caller's, nobody's or everybody's (null). */
export type ReviewAssignment = 'me' | 'unassigned';

export interface ReviewFilters {
  assigned: ReviewAssignment | null;
  level: ApprovalLevel | null;
  organizationId: number | null;
}

export const NO_REVIEW_FILTERS: ReviewFilters = {
  assigned: null,
  level: null,
  organizationId: null,
};

/** The reviewer queue and who works on a request. */
@Injectable({ providedIn: 'root' })
export class ReviewsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/awards`;

  list(filters: ReviewFilters, page = 0, size = 20): Observable<Page<ReviewItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [name, value] of Object.entries(filters)) {
      if (value !== null) {
        params = params.set(name, String(value));
      }
    }
    return this.http.get<Page<ReviewItem>>(`${environment.apiUrl}/reviews`, { params });
  }

  /** The open request of an award; 404 when the caller may not review it. */
  item(awardId: number): Observable<ReviewItem> {
    return this.http.get<ReviewItem>(this.reviewer(awardId));
  }

  /** Claims the request, or takes it over from its reviewer. */
  claim(awardId: number, requestVersion: number, takeOver = false): Observable<ReviewItem> {
    return this.http.put<ReviewItem>(this.reviewer(awardId), { requestVersion, takeOver });
  }

  handOver(awardId: number, requestVersion: number, reviewerId: number): Observable<ReviewItem> {
    return this.http.put<ReviewItem>(this.reviewer(awardId), { requestVersion, reviewerId });
  }

  release(awardId: number, requestVersion: number): Observable<void> {
    return this.http.delete<void>(this.reviewer(awardId), { params: { requestVersion } });
  }

  /** Approves, rejects, returns or escalates the request, claiming it first when nobody holds it. */
  decide(awardId: number, request: DecisionRequest): Observable<DecisionOutcome> {
    return this.http.post<DecisionOutcome>(`${this.base}/${awardId}/decisions`, request);
  }

  candidates(awardId: number): Observable<ReviewerCandidate[]> {
    return this.http.get<ReviewerCandidate[]>(`${this.base}/${awardId}/reviewers`);
  }

  private reviewer(awardId: number): string {
    return `${this.base}/${awardId}/reviewer`;
  }
}
