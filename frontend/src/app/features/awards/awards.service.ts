import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, shareReplay, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef, OrganizationType } from '../../core/auth/user-profile';
import { OrganizationsService } from '../../core/organizations/organizations.service';
import { daysBefore } from '../../shared/date-format';
import { organizationName } from '../../shared/organization-name';

/** An organisation name in both languages. */
export interface OrganizationName {
  id: number;
  name: string;
  nameUk: string | null;
}

/** Organisation types an award can belong to. */
const ORGANIZATION_TYPES: OrganizationType[] = ['DEPARTMENT', 'FACULTY', 'COLLEGE'];

export type AwardStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';
export type RequestStatus =
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'ESCALATED'
  | 'APPROVED'
  | 'REJECTED'
  | 'RETURNED'
  | 'EXPIRED'
  | 'WITHDRAWN';
export type RecognitionLevel =
  | 'SPECIALITY'
  | 'DEPARTMENT'
  | 'COLLEGE'
  | 'FACULTY'
  | 'LOCAL'
  | 'UNIVERSITY'
  | 'REGIONAL'
  | 'NATIONAL'
  | 'INTERNATIONAL';

export const AWARD_STATUSES: AwardStatus[] = [
  'DRAFT',
  'PENDING',
  'APPROVED',
  'REJECTED',
  'ARCHIVED',
];

export interface CategoryRef {
  id: number;
  name: string;
  nameUk: string | null;
  level: RecognitionLevel;
}

export interface CategoryNode extends CategoryRef {
  description: string | null;
  children: CategoryNode[];
}

export type SuggestionReason = 'KEYWORD' | 'ORGANISATION' | 'HISTORY';

/** A category suggested for the award being entered, with the rules that produced it. */
export interface CategorySuggestion extends CategoryRef {
  score: number;
  reasons: SuggestionReason[];
}

/** Title and organisation shorter than this are not used for suggestions. */
export const SUGGESTION_MIN_LENGTH = 3;

export type ApprovalLevel = 'FACULTY_SECRETARY' | 'DEAN' | 'RECTOR_SECRETARY' | 'RECTOR';

/** Approval levels from the lowest to the highest. */
export const APPROVAL_LEVELS: ApprovalLevel[] = [
  'FACULTY_SECRETARY',
  'DEAN',
  'RECTOR_SECRETARY',
  'RECTOR',
];

export interface AwardRequestSummary {
  status: RequestStatus;
  currentLevel: ApprovalLevel;
  submittedAt: string;
  deadline: string | null;
  /** Kyiv date `YYYY-MM-DD` the last level is expected to finish; null while no level is reviewing. */
  estimatedCompletion: string | null;
  overdue: boolean;
  /** The reviewer's comment while a returned award waits for resubmission; only on a single award. */
  returnComment?: string | null;
}

/** Request statuses the owner may still withdraw, as long as no reviewer has claimed them. */
export const WITHDRAWABLE_REQUEST_STATUSES: RequestStatus[] = ['SUBMITTED', 'ESCALATED'];

/** Request statuses after which nothing changes any more. */
export const FINAL_REQUEST_STATUSES: RequestStatus[] = ['APPROVED', 'REJECTED', 'EXPIRED'];

export type StepState = 'DONE' | 'SKIPPED' | 'CURRENT' | 'UPCOMING';
export type ReviewDecisionType = 'APPROVED' | 'REJECTED' | 'ESCALATED' | 'RETURNED';
export type DelayReason = 'NO_REVIEWER' | 'REVIEW_OVERDUE';

/** One level of the approval path. */
export interface PathStep {
  level: ApprovalLevel;
  state: StepState;
  dueDate: string | null;
  completedAt: string | null;
}

/** A reviewer decision with its comment. */
export interface ReviewDecision {
  id: number;
  decision: ReviewDecisionType;
  level: ApprovalLevel;
  reviewerId: number;
  reviewerName: string;
  comments: string | null;
  decidedAt: string;
  /** Who lent the role the reviewer decided under; null for an own role. */
  delegatorId: number | null;
  delegatorName: string | null;
}

/** The review timeline of an award; request fields are null for an award without a request. */
export interface AwardStatusView {
  awardId: number;
  status: AwardStatus;
  requestStatus: RequestStatus | null;
  currentLevel: ApprovalLevel | null;
  submittedAt: string | null;
  deadline: string | null;
  estimatedCompletion: string | null;
  overdue: boolean;
  completedAt: string | null;
  rejectionReason: string | null;
  delay: { reason: DelayReason; since: string | null } | null;
  path: PathStep[];
  decisions: ReviewDecision[];
}

/** An own award that looks like the one being entered. */
export interface DuplicateMatch {
  id: number;
  title: string | null;
  titleUk: string | null;
  awardDate: string;
  status: AwardStatus;
}

export interface AwardWarning {
  code: 'RECENT_DATE' | 'POSSIBLE_DUPLICATE';
  field: string;
  matches: DuplicateMatch[];
}

/** Awards older than this many years are refused. */
export const MAX_AGE_YEARS = 50;
/** A date within this many days is pointed out as possibly the submission date. */
export const RECENT_DAYS = 30;

/** A faculty or department that receives awards. */
export interface UnitRef extends OrganizationName {
  type: OrganizationType;
}

/** Who received an award: its owner, or a faculty or department the owner entered it for. */
export type AwardRecipient =
  { type: 'PERSON'; organization: null } | { type: 'UNIT'; organization: UnitRef };

export interface Award {
  id: number;
  title: string | null;
  titleUk: string | null;
  description: string | null;
  descriptionUk: string | null;
  category: CategoryRef | null;
  awardingOrganization: string | null;
  awardDate: string | null;
  externalUrl: string | null;
  status: AwardStatus;
  impactScore: number | null;
  owner: { id: number; name: string; email: string };
  recipient: AwardRecipient;
  organization: OrganizationRef;
  request: AwardRequestSummary | null;
  warnings: AwardWarning[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** The form fields of an award; every field may be empty in a draft except a title in one language. */
export interface AwardForm {
  title: string | null;
  titleUk: string | null;
  description: string | null;
  descriptionUk: string | null;
  categoryId: number | null;
  awardingOrganization: string | null;
  awardDate: string | null;
  externalUrl: string | null;
  /** The faculty or department that received the award; null for the caller. */
  recipientOrganizationId: number | null;
}

export interface AwardFilters {
  status: AwardStatus | null;
  category: number | null;
  dateFrom: string | null;
  dateTo: string | null;
}

export type AwardPage = Page<Award>;

export const NO_FILTERS: AwardFilters = {
  status: null,
  category: null,
  dateFrom: null,
  dateTo: null,
};

/** A page of any list answered by the API. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type VersionAction = 'BASELINE' | 'CREATED' | 'UPDATED' | 'SUBMITTED' | 'DECIDED';

/** The business fields of an award as saved in one version. */
export interface AwardSnapshot {
  title: string | null;
  titleUk: string | null;
  description: string | null;
  descriptionUk: string | null;
  awardingOrganization: string | null;
  awardDate: string | null;
  categoryId: number | null;
  status: AwardStatus;
  impactScore: number | null;
  verificationBadge: boolean;
  externalUrl: string | null;
  organizationId: number | null;
}

export type SnapshotField = keyof AwardSnapshot;

/** Snapshot fields in the order they are shown. */
export const SNAPSHOT_FIELDS: SnapshotField[] = [
  'title',
  'titleUk',
  'description',
  'descriptionUk',
  'categoryId',
  'awardingOrganization',
  'awardDate',
  'externalUrl',
  'organizationId',
  'status',
  'impactScore',
  'verificationBadge',
];

export interface FieldChange {
  field: SnapshotField;
  from: unknown;
  to: unknown;
}

export interface AwardVersion {
  number: number;
  action: VersionAction;
  actor: { id: number; name: string; email: string } | null;
  createdAt: string;
  snapshot: AwardSnapshot;
  changes: FieldChange[];
}

/** One row of the audit log about an award. */
export interface AuditTrailEntry {
  id: number;
  createdAt: string;
  actorId: number | null;
  actorName: string | null;
  actorEmail: string | null;
  action: string;
  entityType: string;
  entityId: number | null;
  changedFields: string[];
  oldValues: Record<string, unknown>;
  newValues: Record<string, unknown>;
  ipAddress: string | null;
  correlationId: string | null;
}

/** Header of an audit export that left out older rows. */
export const TRUNCATED_HEADER = 'X-Audit-Truncated';

@Injectable({ providedIn: 'root' })
export class AwardsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/awards`;
  private readonly organizationList = inject(OrganizationsService);
  private catalogue$?: Observable<CategoryNode[]>;

  list(filters: AwardFilters, page = 0, size = 20): Observable<AwardPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) {
      if (value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<AwardPage>(this.base, { params });
  }

  get(id: number): Observable<Award> {
    return this.http.get<Award>(`${this.base}/${id}`);
  }

  create(form: AwardForm): Observable<Award> {
    return this.http.post<Award>(this.base, form);
  }

  update(id: number, form: AwardForm, version: number): Observable<Award> {
    return this.http.put<Award>(`${this.base}/${id}`, { ...form, version });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  submit(id: number, version: number, acknowledgeDuplicate = false): Observable<Award> {
    return this.http.post<Award>(`${this.base}/${id}/submit`, { version, acknowledgeDuplicate });
  }

  /** Takes a pending award no reviewer has claimed back as a draft. */
  withdraw(id: number, version: number): Observable<Award> {
    return this.http.post<Award>(`${this.base}/${id}/withdraw`, { version });
  }

  /** The review timeline of an award. */
  status(id: number): Observable<AwardStatusView> {
    return this.http.get<AwardStatusView>(`${this.base}/${id}/status`);
  }

  /** Saved versions of an award, newest first. */
  versions(id: number, page = 0, size = 20): Observable<Page<AwardVersion>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<AwardVersion>>(`${this.base}/${id}/versions`, { params });
  }

  /** Audit rows about an award, newest first; needs `audit:read`. */
  auditTrail(id: number, page = 0, size = 20): Observable<Page<AuditTrailEntry>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<AuditTrailEntry>>(`${this.base}/${id}/audit-trail`, { params });
  }

  /** The audit rows about an award as a CSV file. */
  exportAuditTrail(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/${id}/audit-trail/export`, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  /** Active departments, faculties and colleges by id, fetched once while the app is open. */
  organizations(): Observable<Map<number, OrganizationName>> {
    return forkJoin(ORGANIZATION_TYPES.map((type) => this.organizationList.ofType(type))).pipe(
      map((lists) => new Map(lists.flat().map((organization) => [organization.id, organization]))),
    );
  }

  /** Faculties and departments the caller may enter awards for; empty without a secretary or dean role. */
  recipientUnits(): Observable<UnitRef[]> {
    return this.http.get<UnitRef[]>(`${this.base}/recipient-units`);
  }

  /** Up to three categories for the title and awarding organisation typed so far. */
  suggestions(title: string, organization: string): Observable<CategorySuggestion[]> {
    const params = new HttpParams().set('title', title).set('organization', organization);
    return this.http.get<CategorySuggestion[]>(
      `${environment.apiUrl}/award-categories/suggestions`,
      { params },
    );
  }

  /** The category tree, fetched once while the app is open; a failed fetch is tried again next time. */
  categories(): Observable<CategoryNode[]> {
    this.catalogue$ ??= this.http
      .get<CategoryNode[]>(`${environment.apiUrl}/award-categories`)
      .pipe(
        catchError((error: unknown) => {
          this.catalogue$ = undefined;
          return throwError(() => error);
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.catalogue$;
  }
}

/** Whether the signed-in user owns the award. */
export function isOwnAward(award: Pick<Award, 'owner'>, userId: string | null): boolean {
  return userId !== null && String(award.owner.id) === userId;
}

/** Title in the interface language, falling back to the other one. */
export function awardTitle(award: Pick<Award, 'title' | 'titleUk'>, language: string): string {
  return (
    (language === 'en' ? (award.title ?? award.titleUk) : (award.titleUk ?? award.title)) ?? ''
  );
}

/** Category name in the interface language. */
export function categoryName(category: CategoryRef, language: string): string {
  return organizationName(category, language);
}

/** Whether a `YYYY-MM-DD` date lies within the last 30 days: today and the 29 days before it. */
export function isRecent(date: string | null | undefined, today: string): boolean {
  return !!date && date <= today && date > daysBefore(today, RECENT_DAYS);
}

/** Every category of the tree with its depth, parents before their children. */
export function flattenCategories(
  nodes: CategoryNode[],
  depth = 0,
): { category: CategoryNode; depth: number }[] {
  return nodes.flatMap((node) => [
    { category: node, depth },
    ...flattenCategories(node.children, depth + 1),
  ]);
}

/** The matching awards of a 409 `award-possible-duplicate` answer, empty when there are none. */
export function duplicateMatches(error: unknown): DuplicateMatch[] {
  const body = (error as { error?: { matches?: unknown } } | null)?.error;
  return Array.isArray(body?.matches) ? (body.matches as DuplicateMatch[]) : [];
}
