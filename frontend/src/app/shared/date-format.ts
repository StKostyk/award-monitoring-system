const KYIV = 'Europe/Kyiv';
const DAY: Intl.DateTimeFormatOptions = {
  timeZone: KYIV,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
};
const DAY_TIME: Intl.DateTimeFormatOptions = { ...DAY, hour: '2-digit', minute: '2-digit' };
const formats = new Map<string, Intl.DateTimeFormat>();

/** Date and time in Kyiv, day first, in the interface language. */
export function kyivDateTime(iso: string, language: string): string {
  return format(localeOf(language), DAY_TIME).format(new Date(iso));
}

/**
 * A day in Kyiv, day first, in the interface language: a `YYYY-MM-DD` date as it is, an instant on the Kyiv
 * calendar. A plain date is rearranged without a formatter: the first formatter of a page costs more than a
 * whole list of rows.
 */
export function kyivDate(value: string, language: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-');
    return [day, month, year].join(language === 'en' ? '/' : '.');
  }
  return format(localeOf(language), DAY).format(new Date(value));
}

/** Today in Kyiv as `YYYY-MM-DD`, the day the server compares award dates with. */
export function kyivToday(now = new Date()): string {
  return format('en-CA', DAY).format(now);
}

/**
 * Creates the first formatter while the start-up requests are under way: the first one of a page loads the
 * calendar data and blocks the page for a moment when it is created during rendering.
 */
export function prepareDateFormats(): void {
  kyivToday();
}

/** The calendar year of a moment in Kyiv, by default the present. */
export function currentKyivYear(now = new Date()): number {
  return Number(kyivToday(now).slice(0, 4));
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

function localeOf(language: string): string {
  return language === 'en' ? 'en-GB' : 'uk-UA';
}

/** Formatters are costly to create and are reused per locale and pattern. */
function format(locale: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = `${locale}|${options === DAY ? 'day' : 'time'}`;
  let formatter = formats.get(key);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, options);
    formats.set(key, formatter);
  }
  return formatter;
}
