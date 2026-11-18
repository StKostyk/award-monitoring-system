import { OrganizationRef } from '../../core/auth/user-profile';
import { organizationName } from '../../shared/organization-name';
import { TopPerson } from '../../shared/widgets/widget-data';
import { UnitAnalytics, UnitComparisonRow } from '../analytics/units/unit-analytics.service';

/** Last year's count from which a halving this year counts as a decline, as in the server's highlights. */
export const DECLINE_MINIMUM = 4;

export type UnitAttention = 'NO_AWARDS' | 'DECLINE';

/** A unit that needs attention this year, with its counts. */
export interface AttentionUnit {
  unit: OrganizationRef;
  attention: UnitAttention;
  thisYear: number;
  lastYear: number;
  /** Approved awards of the earlier years of the period. */
  earlier: number;
  /** The last reminder within the server's interval, null when none. */
  lastReminderAt: string | null;
}

/**
 * The units of a faculty or of the university that need attention in the last year of the period: those without
 * an award first, then those whose count fell to half of the year before or less; units without members, closed
 * units and the faculty's own row are left out.
 *
 * @param rows the comparison rows, `byYear` from the first to the last year of the period
 * @return the units in the order to show
 */
export function attentionUnits(rows: UnitComparisonRow[]): AttentionUnit[] {
  const units = rows
    .filter((row) => !row.facultyLevel && row.active && row.members > 0 && row.byYear.length > 0)
    .map((row) => {
      const last = row.byYear.length - 1;
      const thisYear = row.byYear[last];
      const lastYear = last > 0 ? row.byYear[last - 1] : 0;
      return {
        unit: row.unit,
        attention: attention(thisYear, lastYear),
        thisYear,
        lastYear,
        earlier: row.byYear.slice(0, last).reduce((sum, count) => sum + count, 0),
        lastReminderAt: row.lastReminderAt ?? null,
      };
    })
    .filter((unit): unit is AttentionUnit => unit.attention !== null);
  return [
    ...units.filter((unit) => unit.attention === 'NO_AWARDS'),
    ...units.filter((unit) => unit.attention === 'DECLINE'),
  ];
}

/** The top people of a faculty; none when the group is too small to list. */
export function facultyTop(data: UnitAnalytics): TopPerson[] {
  return data.topPerformers.status === 'LISTED'
    ? data.topPerformers.people.map((person) => ({
        name: `${person.firstName} ${person.lastName}`,
        count: person.count,
      }))
    : [];
}

/** The units with awards in the period, most first. */
export function unitTop(rows: UnitComparisonRow[], language: string): TopPerson[] {
  return rows
    .filter((row) => !row.facultyLevel && row.approved.current > 0)
    .sort((a, b) => b.approved.current - a.approved.current)
    .map((row) => ({ name: organizationName(row.unit, language), count: row.approved.current }));
}

function attention(thisYear: number, lastYear: number): UnitAttention | null {
  if (thisYear === 0) {
    return 'NO_AWARDS';
  }
  return lastYear >= DECLINE_MINIMUM && thisYear * 2 <= lastYear ? 'DECLINE' : null;
}
