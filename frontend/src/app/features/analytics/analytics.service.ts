import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef } from '../../core/auth/user-profile';
import { CategoryRef, RecognitionLevel } from '../awards/awards.service';

export type RankStatus = 'RANKED' | 'CONSENT_REQUIRED' | 'NO_DEPARTMENT' | 'GROUP_TOO_SMALL';

export type DashboardWidget =
  'SUMMARY' | 'TIMELINE' | 'YEAR_TREND' | 'BY_CATEGORY' | 'BY_LEVEL' | 'RANK';

/** Widgets in display order. */
export const DASHBOARD_WIDGETS: DashboardWidget[] = [
  'SUMMARY',
  'TIMELINE',
  'YEAR_TREND',
  'BY_CATEGORY',
  'BY_LEVEL',
  'RANK',
];

export type LevelBand = 'UNIT' | 'UNIVERSITY' | 'REGIONAL' | 'NATIONAL' | 'INTERNATIONAL';

/** The five bands the year trend stacks by: the unit levels below the university share one band. */
export const LEVEL_BANDS: LevelBand[] = [
  'UNIT',
  'UNIVERSITY',
  'REGIONAL',
  'NATIONAL',
  'INTERNATIONAL',
];

/** A period of calendar years; null bounds take the server defaults. */
export interface AnalyticsPeriod {
  from: number | null;
  to: number | null;
}

export interface YearCount {
  year: number;
  total: number;
  byLevel: Partial<Record<RecognitionLevel, number>>;
}

export interface TimelineEntry {
  awardId: number;
  title: string | null;
  titleUk: string | null;
  awardDate: string;
  category: CategoryRef;
  awardingOrganization: string;
  verified: boolean;
}

export interface PersonalRank {
  status: RankStatus;
  position: number | null;
  groupSize: number | null;
  sharedWith: number | null;
  department: OrganizationRef | null;
}

export interface PersonalAnalytics {
  period: { from: number; to: number };
  totals: {
    approved: number;
    verified: number;
    highestLevel: RecognitionLevel | null;
    firstAwardDate: string | null;
    latestAwardDate: string | null;
  };
  inProgress: { drafts: number; pending: number };
  byYear: YearCount[];
  byCategory: { category: CategoryRef; count: number }[];
  byLevel: { level: RecognitionLevel; count: number }[];
  timeline: TimelineEntry[];
  timelineTruncated: boolean;
  rank: PersonalRank;
}

/** The band a recognition level belongs to. */
export function levelBand(level: RecognitionLevel): LevelBand {
  return (LEVEL_BANDS as string[]).includes(level) ? (level as LevelBand) : 'UNIT';
}

/** The caller's personal analytics and dashboard widget choice. */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly analyticsUrl = `${environment.apiUrl}/analytics/me`;
  private readonly preferencesUrl = `${environment.apiUrl}/users/me/dashboard-preferences`;

  read(period: AnalyticsPeriod): Observable<PersonalAnalytics> {
    return this.http.get<PersonalAnalytics>(this.analyticsUrl, { params: periodParams(period) });
  }

  hiddenWidgets(): Observable<DashboardWidget[]> {
    return this.http
      .get<{ hiddenWidgets: DashboardWidget[] }>(this.preferencesUrl)
      .pipe(map((body) => body.hiddenWidgets));
  }

  saveHiddenWidgets(hiddenWidgets: DashboardWidget[]): Observable<DashboardWidget[]> {
    return this.http
      .put<{ hiddenWidgets: DashboardWidget[] }>(this.preferencesUrl, { hiddenWidgets })
      .pipe(map((body) => body.hiddenWidgets));
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
