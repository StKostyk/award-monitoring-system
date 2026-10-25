import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { OrganizationRef } from '../../../core/auth/user-profile';
import { AppLanguage } from '../../../core/i18n/language.service';
import { CategoryRef, RecognitionLevel } from '../../awards/awards.service';
import { AnalyticsPeriod } from '../analytics.service';
import {
  UnitComparisonRow,
  UnitHighlight,
  UnitTotals,
  periodParams,
} from '../units/unit-analytics.service';

export type ProjectionStatus = 'PROJECTED' | 'TOO_FEW_YEARS';
export type ComplianceStatus = 'OK' | 'ATTENTION';
export type AttentionReason =
  | 'RETENTION_NOT_RUN'
  | 'RETENTION_FAILED'
  | 'RETENTION_STALE'
  | 'ERASURE_OVERDUE'
  | 'REVIEWS_OVERDUE';
export type RetentionRunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'INTERRUPTED';

/** A category whose count changed noticeably against the previous period; `deltaPercent` is null when it is new. */
export interface CategoryTrend {
  category: CategoryRef;
  current: number;
  previous: number;
  delta: number;
  deltaPercent: number | null;
}

/** An awarding organisation whose count changed noticeably against the previous period. */
export interface SourceTrend {
  name: string;
  current: number;
  previous: number;
  delta: number;
  deltaPercent: number | null;
}

export interface AnalyticsTrends {
  categories: { rising: CategoryTrend[]; falling: CategoryTrend[] };
  sources: { rising: SourceTrend[]; falling: SourceTrend[] };
}

/** The least-squares estimate of the year after the last complete one, when there are enough complete years. */
export interface TrendProjection {
  status: ProjectionStatus;
  basis: { from: number; to: number } | null;
  slope: number | null;
  year: number | null;
  estimate: number | null;
}

export interface UniversityAnalytics {
  unit: OrganizationRef;
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
  trends: AnalyticsTrends;
  projection: TrendProjection;
  highlights: UnitHighlight[];
}

export interface ComplianceSummary {
  generatedAt: string;
  status: ComplianceStatus;
  attention: AttentionReason[];
  privacyNotice: { version: string; accounts: number; acknowledged: number };
  analyticsConsent: { accounts: number; granted: number };
  erasure: { pending: number; postponed: number; overdue: number };
  legalHolds: { active: number };
  lastRetentionRun: {
    status: RetentionRunStatus;
    startedAt: string;
    finishedAt: string | null;
  } | null;
  reviews: {
    open: number;
    overdue: number;
    byLevel: { level: string; open: number; overdue: number }[];
  };
}

/** Analytics of the whole university and its compliance summary. */
@Injectable({ providedIn: 'root' })
export class UniversityAnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/analytics/university`;

  read(period: AnalyticsPeriod): Observable<UniversityAnalytics> {
    return this.http.get<UniversityAnalytics>(this.url, { params: periodParams(period) });
  }

  compliance(): Observable<ComplianceSummary> {
    return this.http.get<ComplianceSummary>(`${this.url}/compliance`);
  }

  /** The board report of the university for the period as a PDF in the given language. */
  exportReport(period: AnalyticsPeriod, lang: AppLanguage): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.url}/report`, {
      params: periodParams(period).set('lang', lang),
      observe: 'response',
      responseType: 'blob',
    });
  }
}
