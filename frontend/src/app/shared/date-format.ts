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
