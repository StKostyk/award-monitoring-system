import { OrganizationRef } from '../../core/auth/user-profile';
import { UnitComparisonRow } from '../analytics/units/unit-analytics.service';
import { facultyAnalytics, figure } from '../analytics/units/unit-analytics.testing';
import { attentionUnits, facultyTop, unitTop } from './command-centre-view';

function department(id: number, name: string): OrganizationRef {
  return { id, name, nameUk: `Кафедра ${name}`, code: null, type: 'DEPARTMENT' };
}

function row(
  unit: OrganizationRef,
  byYear: number[],
  changes: Partial<UnitComparisonRow> = {},
): UnitComparisonRow {
  const current = byYear.reduce((sum, count) => sum + count, 0);
  return {
    unit,
    facultyLevel: false,
    active: true,
    members: 10,
    approved: figure(current, 0),
    awardedPeople: 1,
    perHundredMembers: null,
    verified: 0,
    byYear,
    ...changes,
  };
}

describe('command centre view', () => {
  it('ac5_2_lists_departments_without_awards_first_then_declines_with_earlier_years', () => {
    const declining = row(department(41, 'Algebra'), [1, 2, 3, 8, 3]);
    const silent = row(department(42, 'Geometry'), [2, 4, 3, 5, 0], {
      lastReminderAt: '2026-11-17T09:00:00Z',
    });
    const steady = row(department(43, 'Topology'), [1, 1, 1, 2, 2]);

    const units = attentionUnits([declining, silent, steady]);

    expect(units.map((unit) => [unit.unit.id, unit.attention])).toEqual([
      [42, 'NO_AWARDS'],
      [41, 'DECLINE'],
    ]);
    expect(units[0]).toEqual(
      expect.objectContaining({
        thisYear: 0,
        lastYear: 5,
        earlier: 14,
        lastReminderAt: '2026-11-17T09:00:00Z',
      }),
    );
    expect(units[1]).toEqual(
      expect.objectContaining({ thisYear: 3, lastYear: 8, earlier: 14, lastReminderAt: null }),
    );
  });

  it('ac5_2_leaves_out_the_faculty_row_closed_and_empty_units_and_small_falls', () => {
    const units = attentionUnits([
      row(department(1, 'Faculty'), [3, 0], { facultyLevel: true }),
      row(department(2, 'Closed'), [3, 0], { active: false }),
      row(department(3, 'Nobody'), [3, 0], { members: 0 }),
      row(department(4, 'Small'), [3, 1]),
      row(department(5, 'Empty years'), []),
    ]);

    expect(units).toEqual([]);
  });

  it('ac5_2_ac5_6_lists_the_top_people_of_a_faculty_and_the_top_units_of_the_university', () => {
    const data = facultyAnalytics();

    expect(facultyTop(data)).toEqual([{ name: 'Olena Koval', count: 6 }]);
    expect(
      facultyTop({ ...data, topPerformers: { status: 'GROUP_TOO_SMALL', people: [] } }),
    ).toEqual([]);
    expect(unitTop(data.units, 'en').map((person) => person.count)).toEqual([11, 3]);
  });
});
