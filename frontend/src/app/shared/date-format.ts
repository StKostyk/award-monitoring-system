/** Date and time in Kyiv, day first, in the interface language. */
export function kyivDateTime(iso: string, language: string): string {
  return new Intl.DateTimeFormat(language === 'en' ? 'en-GB' : 'uk-UA', {
    timeZone: 'Europe/Kyiv',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));
}

/**
 * A day in Kyiv, day first, in the interface language: a `YYYY-MM-DD` date as it is, an instant on the Kyiv
 * calendar.
 */
export function kyivDate(value: string, language: string): string {
  const instant = /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T12:00:00Z` : value;
  return new Intl.DateTimeFormat(language === 'en' ? 'en-GB' : 'uk-UA', {
    timeZone: 'Europe/Kyiv',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(instant));
}

/** Today in Kyiv as `YYYY-MM-DD`, the day the server compares award dates with. */
export function kyivToday(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Kyiv',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

/** The same day the given number of years earlier; 29 February becomes 28 February in a common year. */
export function yearsBefore(date: string, years: number): string {
  const [year, month, day] = date.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year - years, month, 0)).getUTCDate();
  return isoDate(Date.UTC(year - years, month - 1, Math.min(day, lastDay)));
}

/** The day the given number of days earlier. */
export function daysBefore(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number);
  return isoDate(Date.UTC(year, month - 1, day - days));
}

function isoDate(time: number): string {
  return new Date(time).toISOString().substring(0, 10);
}
