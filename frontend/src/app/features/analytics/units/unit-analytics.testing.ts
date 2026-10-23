import { OrganizationRef } from '../../../core/auth/user-profile';
import { AnalysableUnit, Figure, UnitAnalytics } from './unit-analytics.service';

export const FACULTY: AnalysableUnit = {
  id: 9,
  name: 'Faculty of Mathematics and Informatics',
  nameUk: 'Факультет математики та інформатики',
  code: 'FMI',
  type: 'FACULTY',
  parentId: null,
};

export const ALGEBRA: AnalysableUnit = {
  id: 64,
  name: 'Department of Algebra and Informatics',
  nameUk: 'Кафедра алгебри та інформатики',
  code: 'DAI',
  type: 'DEPARTMENT',
  parentId: 9,
};

export const ANALYSIS: AnalysableUnit = {
  id: 66,
  name: 'Department of Mathematical Analysis',
  nameUk: 'Кафедра математичного аналізу',
  code: 'DMA',
  type: 'DEPARTMENT',
  parentId: 9,
};

/** The reference of a unit, as rows of the analytics carry it. */
export function ref(unit: AnalysableUnit): OrganizationRef {
  return { id: unit.id, name: unit.name, nameUk: unit.nameUk, code: unit.code, type: unit.type };
}

/** A figure with its change computed as the API does. */
export function figure(current: number, previous: number): Figure {
  return {
    current,
    previous,
    delta: current - previous,
    deltaPercent: previous === 0 ? null : ((current - previous) * 100) / previous,
  };
}

/** Analytics of the faculty for 2024–2026 against 2021–2023, with two departments and the faculty level. */
export function facultyAnalytics(): UnitAnalytics {
  return {
    unit: FACULTY,
    period: { from: 2024, to: 2026 },
    previousPeriod: { from: 2021, to: 2023 },
    partialYear: true,
    generatedAt: '2026-10-23T10:00:00Z',
    totals: {
      approved: figure(15, 10),
      awardedPeople: figure(6, 0),
      personal: 12,
      unitAwards: 3,
      verified: 9,
      members: 40,
      perHundredMembers: 37.5,
    },
    byYear: [
      { year: 2024, total: 4, personal: 3, unitAwards: 1 },
      { year: 2025, total: 5, personal: 4, unitAwards: 1 },
      { year: 2026, total: 6, personal: 5, unitAwards: 1 },
    ],
    units: [
      {
        unit: ref(FACULTY),
        facultyLevel: true,
        active: true,
        members: 4,
        approved: figure(1, 1),
        awardedPeople: 1,
        perHundredMembers: 25,
        verified: 1,
        byYear: [0, 1, 0],
      },
      {
        unit: ref(ALGEBRA),
        facultyLevel: false,
        active: true,
        members: 20,
        approved: figure(11, 5),
        awardedPeople: 4,
        perHundredMembers: 55,
        verified: 7,
        byYear: [3, 3, 5],
      },
      {
        unit: ref(ANALYSIS),
        facultyLevel: false,
        active: false,
        members: 16,
        approved: figure(3, 8),
        awardedPeople: 1,
        perHundredMembers: 18.75,
        verified: 1,
        byYear: [1, 1, 1],
      },
    ],
    byCategory: [
      {
        category: { id: 3, name: 'Diploma', nameUk: 'Диплом', level: 'FACULTY' },
        count: 4,
        previous: 6,
      },
      {
        category: { id: 7, name: 'Grant', nameUk: 'Грант', level: 'NATIONAL' },
        count: 11,
        previous: 4,
      },
    ],
    byLevel: [
      { level: 'FACULTY', count: 4, previous: 6 },
      { level: 'NATIONAL', count: 11, previous: 4 },
    ],
    bySource: {
      sources: [
        { name: 'Ministry of Education', count: 11 },
        { name: 'ЧНУ', count: 4 },
      ],
      otherCount: 0,
    },
    topPerformers: {
      status: 'LISTED',
      people: [
        {
          userId: 5,
          firstName: 'Olena',
          lastName: 'Koval',
          department: ref(ALGEBRA),
          count: 6,
          highestLevel: 'NATIONAL',
        },
      ],
    },
    highlights: [
      { type: 'GROWTH', unit: ref(ALGEBRA), current: 11, previous: 5 },
      { type: 'DECLINE', unit: ref(ANALYSIS), current: 3, previous: 8 },
    ],
  };
}

/** Analytics of a department: no comparison rows. */
export function departmentAnalytics(): UnitAnalytics {
  return {
    ...facultyAnalytics(),
    unit: ALGEBRA,
    partialYear: false,
    units: [],
    highlights: [],
  };
}
