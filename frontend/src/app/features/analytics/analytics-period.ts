import { ParamMap } from '@angular/router';

import { currentKyivYear } from '../../shared/date-format';
import { AnalyticsPeriod } from './analytics.service';

export const FIRST_YEAR = 1950;

/** The period of the address bar, or null for a year outside 1950..lastYear or reversed years. */
export function readPeriod(params: ParamMap, lastYear = currentKyivYear()): AnalyticsPeriod | null {
  const from = year(params, 'from', lastYear);
  const to = year(params, 'to', lastYear);
  if (from === undefined || to === undefined || (from !== null && to !== null && from > to)) {
    return null;
  }
  return { from, to };
}

function year(params: ParamMap, name: string, lastYear: number): number | null | undefined {
  if (!params.has(name)) {
    return null;
  }
  const raw = params.get(name) ?? '';
  const value = Number(raw);
  return raw.trim() !== '' && Number.isInteger(value) && value >= FIRST_YEAR && value <= lastYear
    ? value
    : undefined;
}
