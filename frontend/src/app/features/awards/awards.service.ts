import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef } from '../../core/auth/user-profile';

export type AwardStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';
export type RequestStatus =
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'ESCALATED'
  | 'APPROVED'
  | 'REJECTED'
  | 'RETURNED'
  | 'EXPIRED';
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

export const AWARD_STATUSES: AwardStatus[] = ['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'ARCHIVED'];

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

export interface AwardRequestSummary {
  status: RequestStatus;
  currentLevel: string;
  submittedAt: string;
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
}

export interface AwardFilters {
  status: AwardStatus | null;
  category: number | null;
  dateFrom: string | null;
  dateTo: string | null;
}

export interface AwardPage {
  content: Award[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** One refused field of a problem answer. */
export interface FieldProblem {
  field: string;
  code: string;
  message: string;
}

export const NO_FILTERS: AwardFilters = { status: null, category: null, dateFrom: null, dateTo: null };

@Injectable({ providedIn: 'root' })
export class AwardsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/awards`;
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

  /** The category tree, fetched once while the app is open; a failed fetch is tried again next time. */
  categories(): Observable<CategoryNode[]> {
    this.catalogue$ ??= this.http.get<CategoryNode[]>(`${environment.apiUrl}/award-categories`).pipe(
      catchError((error: unknown) => {
        this.catalogue$ = undefined;
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.catalogue$;
  }
}

/** Title in the interface language, falling back to the other one. */
export function awardTitle(award: Pick<Award, 'title' | 'titleUk'>, language: string): string {
  return (language === 'en' ? (award.title ?? award.titleUk) : (award.titleUk ?? award.title)) ?? '';
}

/** Category name in the interface language. */
export function categoryName(category: CategoryRef, language: string): string {
  return language === 'en' ? category.name : (category.nameUk ?? category.name);
}

/** Today in Kyiv as `YYYY-MM-DD`, the day the server compares award dates with. */
export function kyivToday(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Kyiv',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

/** The same day the given number of years earlier; 29 February becomes 28 February in a common year. */
export function yearsBefore(date: string, years: number): string {
  const [year, month, day] = date.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year - years, month, 0)).getUTCDate();
  return isoDate(Date.UTC(year - years, month - 1, Math.min(day, lastDay)));
}

/** The day the given number of days earlier. */
export function daysBefore(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number);
  return isoDate(Date.UTC(year, month - 1, day - days));
}

/** Whether a `YYYY-MM-DD` date lies within the last 30 days: today and the 29 days before it. */
export function isRecent(date: string | null | undefined, today: string): boolean {
  return !!date && date <= today && date > daysBefore(today, RECENT_DAYS);
}

function isoDate(time: number): string {
  return new Date(time).toISOString().substring(0, 10);
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

/** The field errors of a 422 answer, empty when there are none. */
export function fieldProblems(error: unknown): FieldProblem[] {
  const body = (error as { error?: { errors?: unknown } } | null)?.error;
  return Array.isArray(body?.errors) ? (body.errors as FieldProblem[]) : [];
}

/** The matching awards of a 409 `award-possible-duplicate` answer, empty when there are none. */
export function duplicateMatches(error: unknown): DuplicateMatch[] {
  const body = (error as { error?: { matches?: unknown } } | null)?.error;
  return Array.isArray(body?.matches) ? (body.matches as DuplicateMatch[]) : [];
}
