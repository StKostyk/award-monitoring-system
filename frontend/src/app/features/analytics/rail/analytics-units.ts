import { AnalysableUnit } from '../units/unit-analytics.service';

/**
 * The units whose parent is not open to the caller, in the order of the API: the faculties for a rector or a
 * dean, the departments themselves for a caller who sees no faculty.
 *
 * @param units the units open to the caller
 * @return the top rows of the analytics rail
 */
export function topUnits(units: readonly AnalysableUnit[]): AnalysableUnit[] {
  const ids = new Set(units.map((unit) => unit.id));
  return units.filter((unit) => unit.parentId === null || !ids.has(unit.parentId));
}

/**
 * The unit `/analytics` opens for a caller without the university view: the first top faculty, else the first top
 * unit.
 *
 * @param units the units open to the caller
 * @return the unit to open, or null when none is open
 */
export function landingUnit(units: readonly AnalysableUnit[]): AnalysableUnit | null {
  const top = topUnits(units);
  return top.find((unit) => unit.type === 'FACULTY') ?? top[0] ?? null;
}
