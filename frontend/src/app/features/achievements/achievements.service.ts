import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ParamMap, Params } from '@angular/router';
import { Observable, forkJoin, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  OrganizationsService,
  OrganizationSummary,
} from '../../core/organizations/organizations.service';
import { organizationName } from '../../shared/organization-name';
import { CategoryRef, Page, RecognitionLevel, UnitRef } from '../awards/awards.service';

/** Who received a shared award: a person, named with the department at submission, or a unit. */
export type RecipientType = 'PERSON' | 'UNIT';

export interface AchievementRecipient {
  type: RecipientType;
  /** First and last name of the owner of a personal award; null for a unit award. */
  personName: string | null;
  unit: UnitRef;
}

/** The shared projection of an approved award. */
export interface Achievement {
  awardId: number;
  title: string | null;
  titleUk: string | null;
  description: string | null;
  descriptionUk: string | null;
  category: CategoryRef;
  awardingOrganization: string;
  awardDate: string;
  externalUrl: string | null;
  /** A reviewer approved it with the documents checked. */
  verified: boolean;
  recipient: AchievementRecipient;
}

export interface AchievementFilters {
  unit: number | null;
  year: number | null;
  level: RecognitionLevel | null;
  recipient: RecipientType | null;
}

/** The filters with their page, as kept in the address bar. */
export interface AchievementQuery {
  filters: AchievementFilters;
  page: number;
  size: number;
}

/** A faculty or department to filter by; departments follow their faculty. */
export interface UnitOption {
  unit: OrganizationSummary;
  depth: number;
}

export const NO_ACHIEVEMENT_FILTERS: AchievementFilters = {
  unit: null,
  year: null,
  level: null,
  recipient: null,
};

/** Recognition levels, broadest first. */
export const RECOGNITION_LEVELS: RecognitionLevel[] = [
  'INTERNATIONAL',
  'NATIONAL',
  'REGIONAL',
  'UNIVERSITY',
  'LOCAL',
  'FACULTY',
  'COLLEGE',
  'DEPARTMENT',
  'SPECIALITY',
];

export const RECIPIENT_TYPES: RecipientType[] = ['PERSON', 'UNIT'];
export const PAGE_SIZES = [20, 50, 100];

const NUMBER = /^\d{1,9}$/;

@Injectable({ providedIn: 'root' })
export class AchievementsService {
  private readonly http = inject(HttpClient);
  private readonly organizations = inject(OrganizationsService);

  /** One page of the awards shared with colleagues, newest first. */
  list(query: AchievementQuery): Observable<Page<Achievement>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const [key, value] of Object.entries(query.filters)) {
      if (value !== null) {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<Achievement>>(`${environment.apiUrl}/achievements`, { params });
  }

  /** Active faculties in the interface language, each followed by its departments. */
  units(language: string): Observable<UnitOption[]> {
    return forkJoin([
      this.organizations.ofType('FACULTY'),
      this.organizations.ofType('DEPARTMENT'),
    ]).pipe(
      map(([faculties, departments]) => {
        const byName = (a: OrganizationSummary, b: OrganizationSummary): number =>
          organizationName(a, language).localeCompare(organizationName(b, language), language);
        return [...faculties].sort(byName).flatMap((faculty) => [
          { unit: faculty, depth: 0 },
          ...departments
            .filter((department) => department.parent?.id === faculty.id)
            .sort(byName)
            .map((department) => ({ unit: department, depth: 1 })),
        ]);
      }),
    );
  }
}

/**
 * Reads the filters and the page from the address bar; a value the API would refuse is left out.
 *
 * @param params the query parameters
 * @returns the filters, page and size
 */
export function readQuery(params: ParamMap): AchievementQuery {
  const number = (name: string): number | null => {
    const value = params.get(name) ?? '';
    return NUMBER.test(value) ? Number(value) : null;
  };
  const level = params.get('level') as RecognitionLevel | null;
  const recipient = params.get('recipient') as RecipientType | null;
  const size = number('size');
  return {
    filters: {
      unit: number('unit'),
      year: number('year'),
      level: level && RECOGNITION_LEVELS.includes(level) ? level : null,
      recipient: recipient && RECIPIENT_TYPES.includes(recipient) ? recipient : null,
    },
    page: number('page') ?? 0,
    size: size !== null && PAGE_SIZES.includes(size) ? size : PAGE_SIZES[0],
  };
}

/**
 * The query parameters of filters and a page, leaving out empty filters and the first page.
 *
 * @param query the filters, page and size
 * @returns the parameters for the address bar
 */
export function writeQuery(query: AchievementQuery): Params {
  const params: Params = {};
  for (const [key, value] of Object.entries(query.filters)) {
    if (value !== null) {
      params[key] = value;
    }
  }
  if (query.page > 0) {
    params['page'] = query.page;
  }
  if (query.size !== PAGE_SIZES[0]) {
    params['size'] = query.size;
  }
  return params;
}
