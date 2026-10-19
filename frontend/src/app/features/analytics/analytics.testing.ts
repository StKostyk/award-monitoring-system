import { signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';

import en from '../../../../public/i18n/en.json';
import uk from '../../../../public/i18n/uk.json';
import { AppLanguage, LanguageService } from '../../core/i18n/language.service';
import { PersonalAnalytics } from './analytics.service';

/** Transloco with the real translations, starting in the given language. */
export function translations(language: AppLanguage = 'en') {
  return TranslocoTestingModule.forRoot({
    langs: { en, uk },
    translocoConfig: { availableLangs: ['en', 'uk'], defaultLang: language },
    preloadLangs: true,
  });
}

/** A language service whose current language a test can switch. */
export function languageStub(language: AppLanguage = 'en') {
  const current = signal<AppLanguage>(language);
  return { current, provider: { provide: LanguageService, useValue: { current } } };
}

/** A bearer token carrying the given claims. */
export function tokenWith(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

const algebra = {
  id: 41,
  name: 'Department of Algebra and Informatics',
  nameUk: 'Кафедра алгебри та інформатики',
  code: 'AI',
  type: 'DEPARTMENT' as const,
};

/** Analytics of an account with three approved awards in 2024–2026. */
export function sampleAnalytics(): PersonalAnalytics {
  return {
    period: { from: 2023, to: 2026 },
    totals: {
      approved: 3,
      verified: 2,
      highestLevel: 'NATIONAL',
      firstAwardDate: '2024-05-10',
      latestAwardDate: '2026-03-01',
    },
    inProgress: { drafts: 1, pending: 2 },
    byYear: [
      { year: 2023, total: 0, byLevel: {} },
      { year: 2024, total: 1, byLevel: { FACULTY: 1 } },
      { year: 2025, total: 0, byLevel: {} },
      { year: 2026, total: 2, byLevel: { DEPARTMENT: 1, NATIONAL: 1 } },
    ],
    byCategory: [
      { category: { id: 7, name: 'Grant', nameUk: 'Грант', level: 'NATIONAL' }, count: 2 },
      { category: { id: 3, name: 'Diploma', nameUk: 'Диплом', level: 'FACULTY' }, count: 1 },
    ],
    byLevel: [
      { level: 'DEPARTMENT', count: 1 },
      { level: 'FACULTY', count: 1 },
      { level: 'NATIONAL', count: 1 },
    ],
    timeline: [
      {
        awardId: 31,
        title: 'National grant',
        titleUk: 'Національний грант',
        awardDate: '2026-03-01',
        category: { id: 7, name: 'Grant', nameUk: 'Грант', level: 'NATIONAL' },
        awardingOrganization: 'Ministry of Education',
        verified: true,
      },
      {
        awardId: 22,
        title: null,
        titleUk: 'Подяка кафедри',
        awardDate: '2026-01-15',
        category: { id: 7, name: 'Grant', nameUk: 'Грант', level: 'DEPARTMENT' },
        awardingOrganization: 'ЧНУ',
        verified: false,
      },
      {
        awardId: 10,
        title: 'Faculty diploma',
        titleUk: null,
        awardDate: '2024-05-10',
        category: { id: 3, name: 'Diploma', nameUk: 'Диплом', level: 'FACULTY' },
        awardingOrganization: 'Faculty of Mathematics',
        verified: true,
      },
    ],
    timelineTruncated: false,
    rank: { status: 'RANKED', position: 2, groupSize: 12, sharedWith: 1, department: algebra },
  };
}

/** Analytics of an account without approved awards in the period. */
export function emptyAnalytics(drafts = 0, pending = 0): PersonalAnalytics {
  return {
    ...sampleAnalytics(),
    totals: {
      approved: 0,
      verified: 0,
      highestLevel: null,
      firstAwardDate: null,
      latestAwardDate: null,
    },
    inProgress: { drafts, pending },
    byYear: [],
    byCategory: [],
    byLevel: [],
    timeline: [],
    rank: {
      status: 'CONSENT_REQUIRED',
      position: null,
      groupSize: null,
      sharedWith: null,
      department: null,
    },
  };
}

export const ALGEBRA = algebra;
