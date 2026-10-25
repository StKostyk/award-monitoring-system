import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { OrganizationRef } from '../../../core/auth/user-profile';
import { AppLanguage } from '../../../core/i18n/language.service';
import { CategoryRef, RecognitionLevel } from '../../awards/awards.service';
import { AnalyticsPeriod } from '../analytics.service';

export type UnitType = 'FACULTY' | 'DEPARTMENT';
export type TopPerformersStatus = 'LISTED' | 'GROUP_TOO_SMALL';
export type HighlightType = 'NO_AWARDS' | 'DECLINE' | 'GROWTH' | 'BELOW_AVERAGE' | 'ABOVE_AVERAGE';
export type ReportFormat = 'pdf' | 'xlsx' | 'csv';

/** The formats of the unit report in the order the export menu offers them. */
export const REPORT_FORMATS: readonly ReportFormat[] = ['pdf', 'xlsx', 'csv'];

/** A faculty or department the caller may analyse. */
export interface AnalysableUnit {
  id: number;
  name: string;
  nameUk: string | null;
  code: string | null;
  type: UnitType;
  parentId: number | null;
}

/** A count of the period beside the one of the previous period; `deltaPercent` is null when the previous is 0. */
export interface Figure {
  current: number;
  previous: number;
  delta: number;
  deltaPercent: number | null;
}

export interface UnitTotals {
  approved: Figure;
  awardedPeople: Figure;
  personal: number;
  unitAwards: number;
  verified: number;
  members: number;
  perHundredMembers: number | null;
}

export interface UnitComparisonRow {
  unit: OrganizationRef;
  facultyLevel: boolean;
  active: boolean;
  members: number;
  approved: Figure;
  awardedPeople: number;
  perHundredMembers: number | null;
  verified: number;
  byYear: number[];
}

export interface TopPerformer {
  userId: number;
  firstName: string;
  lastName: string;
  department: OrganizationRef | null;
  count: number;
  highestLevel: RecognitionLevel;
}

export interface UnitHighlight {
  type: HighlightType;
  unit: OrganizationRef;
  current: number;
  previous: number;
}

export interface UnitAnalytics {
  unit: AnalysableUnit;
  period: { from: number; to: number };
  previousPeriod: { from: number; to: number };
  partialYear: boolean;
  generatedAt: string;
  totals: UnitTotals;
  byYear: { year: number; total: number; personal: number; unitAwards: number }[];
  units: UnitComparisonRow[];
  byCategory: { category: CategoryRef; count: number; previous: number }[];
  byLevel: { level: RecognitionLevel; count: number; previous: number }[];
  bySource: { sources: { name: string; count: number }[]; otherCount: number };
  topPerformers: { status: TopPerformersStatus; people: TopPerformer[] };
  highlights: UnitHighlight[];
}

/** Analytics of the faculties and departments in the caller's scope. */
@Injectable({ providedIn: 'root' })
export class UnitAnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/analytics/units`;

  units(): Observable<AnalysableUnit[]> {
    return this.http.get<AnalysableUnit[]>(this.url);
  }

  read(unitId: number, period: AnalyticsPeriod): Observable<UnitAnalytics> {
    return this.http.get<UnitAnalytics>(`${this.url}/${unitId}`, { params: periodParams(period) });
  }

  /** The report of a unit for the period as a file in the given format and language. */
  exportReport(
    unitId: number,
    period: AnalyticsPeriod,
    format: ReportFormat,
    lang: AppLanguage,
  ): Observable<HttpResponse<Blob>> {
    const params = periodParams(period).set('format', format).set('lang', lang);
    return this.http.get(`${this.url}/${unitId}/export`, {
      params,
      observe: 'response',
      responseType: 'blob',
    });
  }
}

/** The query of a period: `from` and `to` when set. */
export function periodParams(period: AnalyticsPeriod): HttpParams {
  let params = new HttpParams();
  if (period.from !== null) {
    params = params.set('from', period.from);
  }
  if (period.to !== null) {
    params = params.set('to', period.to);
  }
  return params;
}
