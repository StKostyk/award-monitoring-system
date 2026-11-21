import { Delegation } from './delegations.service';

/** How far a delegation is from its start or end, in whole days. */
export interface DaysLeft {
  key: 'endsIn' | 'startsIn';
  count: number;
}

const DAY_MILLIS = 86_400_000;

function dayNumber(date: string): number {
  return Math.round(Date.parse(`${date.slice(0, 10)}T00:00:00Z`) / DAY_MILLIS);
}

/**
 * The days an active delegation still runs, today and its last day included, or the days until an upcoming one
 * starts; an ended or revoked delegation has none, nor has one whose state lags behind the calendar.
 *
 * @param delegation the delegation
 * @param today the Kyiv date, as yyyy-MM-dd
 * @returns the phrase key and the count, or null
 */
export function daysLeft(delegation: Delegation, today: string): DaysLeft | null {
  let left: DaysLeft | null = null;
  if (delegation.state === 'active') {
    left = { key: 'endsIn', count: dayNumber(delegation.validTo) - dayNumber(today) + 1 };
  } else if (delegation.state === 'upcoming') {
    left = { key: 'startsIn', count: dayNumber(delegation.validFrom) - dayNumber(today) };
  }
  return left && left.count > 0 ? left : null;
}
