import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef } from '../../core/auth/user-profile';
import { LanguageService } from '../../core/i18n/language.service';
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
  overdueNoticedAt: string | null;
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

/** One award of a batch decision. */
export interface BatchItem {
  awardId: number;
  requestVersion: number;
}

/** The same decision for several awards; each item is decided on its own. */
export interface BatchDecisionRequest {
  decision: DecisionType;
  comment?: string;
  verified?: boolean;
  items: BatchItem[];
}

/** What became of one item: `code` is the problem type slug of a failed item. */
export interface BatchItemResult {
  awardId: number;
  outcome: 'DONE' | 'FAILED';
  code?: string;
  detail?: string;
  status?: AwardStatus;
  level?: ApprovalLevel;
}

/** A ready-made reviewer comment in the caller's language. */
export interface ReviewTemplate {
  id: number;
  decision: DecisionType;
  title: string;
  body: string;
}

/** Whose requests the queue shows: the caller's, nobody's or everybody's (null). */
export type ReviewAssignment = 'me' | 'unassigned';

export interface ReviewFilters {
  assigned: ReviewAssignment | null;
  level: ApprovalLevel | null;
  organizationId: number | null;
  /** True for requests whose missed deadline the next level was told about; null shows all. */
  noticed: true | null;
}

/** The working days each faculty level has for a request of the faculty. */
export interface ReviewPeriod {
  organizationId: number;
  workingDays: number | null;
  effectiveWorkingDays: number;
  defaultWorkingDays: number;
  updatable: boolean;
}

export const NO_REVIEW_FILTERS: ReviewFilters = {
  assigned: null,
  level: null,
  organizationId: null,
  noticed: null,
};

/** The reviewer queue and who works on a request. */
@Injectable({ providedIn: 'root' })
export class ReviewsService {
  private readonly http = inject(HttpClient);
  private readonly language = inject(LanguageService);
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

  /** Applies one decision to up to 50 awards; answers one result per item in the order sent. */
  decideBatch(request: BatchDecisionRequest): Observable<BatchItemResult[]> {
    return this.http.post<BatchItemResult[]>(`${environment.apiUrl}/reviews/decisions`, request);
  }

  /** The comment templates of a decision, in the language the interface shows. */
  templates(decision: DecisionType): Observable<ReviewTemplate[]> {
    return this.http.get<ReviewTemplate[]>(`${environment.apiUrl}/reviews/templates`, {
      params: { decision },
      headers: { 'Accept-Language': this.language.current() },
    });
  }

  candidates(awardId: number): Observable<ReviewerCandidate[]> {
    return this.http.get<ReviewerCandidate[]>(`${this.base}/${awardId}/reviewers`);
  }

  /** The review period of a faculty. */
  reviewPeriod(facultyId: number): Observable<ReviewPeriod> {
    return this.http.get<ReviewPeriod>(this.period(facultyId));
  }

  /** Sets the review period of a faculty; null restores the default. */
  setReviewPeriod(facultyId: number, workingDays: number | null): Observable<ReviewPeriod> {
    return this.http.put<ReviewPeriod>(this.period(facultyId), { workingDays });
  }

  private period(facultyId: number): string {
    return `${environment.apiUrl}/organizations/${facultyId}/review-period`;
  }

  private reviewer(awardId: number): string {
    return `${this.base}/${awardId}/reviewer`;
  }
}
