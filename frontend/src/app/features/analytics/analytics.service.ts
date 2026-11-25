import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef } from '../../core/auth/user-profile';
import { AppLanguage } from '../../core/i18n/language.service';
import { CategoryRef, RecognitionLevel } from '../awards/awards.service';

export type RankStatus = 'RANKED' | 'CONSENT_REQUIRED' | 'NO_DEPARTMENT' | 'GROUP_TOO_SMALL';

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
  level: RecognitionLevel;
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
  /** Approved personal awards by whom they are shown to, whatever the period. */
  visibility: AwardVisibilityCounts;
}

/** Approved personal awards by visibility and how many carry a document. */
export interface AwardVisibilityCounts {
  publicAwards: number;
  universityAwards: number;
  privateAwards: number;
  withDocument: number;
}

/** The file formats of the CV export. */
export type CvFormat = 'pdf' | 'text';

export const CV_FORMATS: CvFormat[] = ['pdf', 'text'];

/** The band a recognition level belongs to. */
export function levelBand(level: RecognitionLevel): LevelBand {
  return (LEVEL_BANDS as string[]).includes(level) ? (level as LevelBand) : 'UNIT';
}

/** The caller's personal analytics. */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly analyticsUrl = `${environment.apiUrl}/analytics/me`;

  read(period: AnalyticsPeriod): Observable<PersonalAnalytics> {
    return this.http.get<PersonalAnalytics>(this.analyticsUrl, { params: periodParams(period) });
  }

  /** The caller's approved awards as a CV list in the given format and language. */
  exportCv(format: CvFormat, lang: AppLanguage): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiUrl}/users/me/awards/cv`, {
      params: new HttpParams().set('format', format).set('lang', lang),
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
