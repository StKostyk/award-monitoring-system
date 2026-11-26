/** A message key with its parameters, for Transloco. */
export interface Phrase {
  key: string;
  params: Record<string, number>;
}

const PERIOD =
  /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/;
const PERIOD_UNITS = ['years', 'months', 'weeks', 'days', 'hours', 'minutes', 'seconds'];
const SECOND = 1000;
const MINUTE = 60;
const HOUR = 3600;

/**
 * The parts of an ISO 8601 period as phrases, largest unit first.
 *
 * @param period the period, e.g. `P7Y` or `PT5M`
 * @returns one phrase per non-zero part; empty when the text is not a period
 */
export function periodPhrases(period: string): Phrase[] {
  const match = PERIOD.exec(period);
  if (!match) {
    return [];
  }
  return PERIOD_UNITS.flatMap((unit, index) => {
    const count = Number(match[index + 1] ?? 0);
    return count > 0 ? [{ key: `compliance.units.${unit}`, params: { count } }] : [];
  });
}

/**
 * How long a run took, in whole seconds, as hours and minutes or minutes and seconds.
 *
 * @param startedAt when the run started
 * @param finishedAt when it ended, null while it runs or after an interruption
 * @returns the duration, or null without an end
 */
export function durationPhrase(startedAt: string, finishedAt: string | null): Phrase | null {
  if (!finishedAt) {
    return null;
  }
  const total = Math.max(0, Math.round((Date.parse(finishedAt) - Date.parse(startedAt)) / SECOND));
  if (total >= HOUR) {
    return {
      key: 'compliance.duration.hours',
      params: { hours: Math.floor(total / HOUR), minutes: Math.floor((total % HOUR) / MINUTE) },
    };
  }
  if (total >= MINUTE) {
    return {
      key: 'compliance.duration.minutes',
      params: { minutes: Math.floor(total / MINUTE), seconds: total % MINUTE },
    };
  }
  return { key: 'compliance.duration.seconds', params: { seconds: total } };
}

/**
 * Whether an e-mail is the placeholder an erased account keeps.
 *
 * @param email the account's address
 * @returns true for an address under the reserved `.invalid` domain
 */
export function isErasedEmail(email: string): boolean {
  return email.toLowerCase().endsWith('.invalid');
}
