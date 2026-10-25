import { OrganizationRef } from '../../../core/auth/user-profile';
import { figure } from '../units/unit-analytics.testing';
import { ComplianceSummary, UniversityAnalytics } from './university-analytics.service';

export const UNIVERSITY: OrganizationRef = {
  id: 1,
  name: 'Yuriy Fedkovych Chernivtsi National University',
  nameUk: 'Чернівецький національний університет імені Юрія Федьковича',
  code: 'CHNU',
  type: 'UNIVERSITY',
};

export const FMI: OrganizationRef = {
  id: 9,
  name: 'Faculty of Mathematics and Informatics',
  nameUk: 'Факультет математики та інформатики',
  code: 'FMI',
  type: 'FACULTY',
};

export const COLLEGE: OrganizationRef = {
  id: 30,
  name: 'Vyzhnytsia College of Applied Arts',
  nameUk: 'Вижницький фаховий коледж прикладного мистецтва',
  code: 'VCAA',
  type: 'COLLEGE',
};

/** Analytics of the university for 2022–2026 against 2017–2021: one faculty, one college and the university level. */
export function universityAnalytics(): UniversityAnalytics {
  return {
    unit: UNIVERSITY,
    period: { from: 2022, to: 2026 },
    previousPeriod: { from: 2017, to: 2021 },
    partialYear: true,
    generatedAt: '2026-10-25T10:00:00Z',
    totals: {
      approved: figure(140, 100),
      awardedPeople: figure(60, 50),
      personal: 120,
      unitAwards: 20,
      verified: 90,
      members: 400,
      perHundredMembers: 35,
    },
    byYear: [
      { year: 2022, total: 20, personal: 18, unitAwards: 2 },
      { year: 2023, total: 25, personal: 22, unitAwards: 3 },
      { year: 2024, total: 30, personal: 26, unitAwards: 4 },
      { year: 2025, total: 35, personal: 30, unitAwards: 5 },
      { year: 2026, total: 30, personal: 24, unitAwards: 6 },
    ],
    units: [
      {
        unit: FMI,
        facultyLevel: false,
        active: true,
        members: 100,
        approved: figure(80, 50),
        awardedPeople: 30,
        perHundredMembers: 80,
        verified: 50,
        byYear: [10, 15, 20, 20, 15],
      },
      {
        unit: COLLEGE,
        facultyLevel: false,
        active: true,
        members: 50,
        approved: figure(5, 10),
        awardedPeople: 4,
        perHundredMembers: 10,
        verified: 3,
        byYear: [1, 1, 1, 1, 1],
      },
      {
        unit: UNIVERSITY,
        facultyLevel: true,
        active: true,
        members: 250,
        approved: figure(55, 40),
        awardedPeople: 26,
        perHundredMembers: 22,
        verified: 37,
        byYear: [9, 9, 9, 14, 14],
      },
    ],
    byCategory: [
      {
        category: { id: 3, name: 'Grant', nameUk: 'Грант', level: 'NATIONAL' },
        count: 40,
        previous: 20,
      },
    ],
    byLevel: [{ level: 'NATIONAL', count: 40, previous: 20 }],
    bySource: { sources: [{ name: 'Ministry of Education', count: 30 }], otherCount: 10 },
    trends: {
      categories: {
        rising: [
          {
            category: { id: 3, name: 'Grant', nameUk: 'Грант', level: 'NATIONAL' },
            current: 40,
            previous: 20,
            delta: 20,
            deltaPercent: 100,
          },
        ],
        falling: [],
      },
      sources: {
        rising: [{ name: 'NAS of Ukraine', current: 6, previous: 0, delta: 6, deltaPercent: null }],
        falling: [
          { name: 'City council', current: 2, previous: 9, delta: -7, deltaPercent: -77.8 },
        ],
      },
    },
    projection: {
      status: 'PROJECTED',
      basis: { from: 2022, to: 2025 },
      slope: 5,
      year: 2026,
      estimate: 40,
    },
    highlights: [
      { type: 'BELOW_AVERAGE', unit: COLLEGE, current: 5, previous: 10 },
      { type: 'ABOVE_AVERAGE', unit: FMI, current: 80, previous: 50 },
    ],
  };
}

/** A compliance summary that needs attention: an overdue erasure request and two overdue reviews. */
export function complianceSummary(): ComplianceSummary {
  return {
    generatedAt: '2026-10-25T10:00:00Z',
    status: 'ATTENTION',
    attention: ['ERASURE_OVERDUE', 'REVIEWS_OVERDUE'],
    privacyNotice: { version: '1.2', accounts: 200, acknowledged: 150 },
    analyticsConsent: { accounts: 200, granted: 50 },
    erasure: { pending: 3, postponed: 1, overdue: 1 },
    legalHolds: { active: 2 },
    lastRetentionRun: {
      status: 'COMPLETED',
      startedAt: '2026-10-25T01:00:00Z',
      finishedAt: '2026-10-25T01:02:00Z',
    },
    reviews: {
      open: 7,
      overdue: 2,
      byLevel: [{ level: 'DEAN', open: 7, overdue: 2 }],
    },
  };
}
