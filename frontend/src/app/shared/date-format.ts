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
