import { vi } from 'vitest';

import { daysBefore, kyivDate, kyivDateTime, kyivToday, yearsBefore } from './date-format';

describe('date format', () => {
  it('shows_a_plain_date_as_the_locale_formatter_does', () => {
    const dates = ['2026-01-01', '2026-03-29', '2026-10-25', '2024-02-29', '1976-12-31'];
    for (const [language, locale] of [
      ['uk', 'uk-UA'],
      ['en', 'en-GB'],
    ]) {
      const formatter = new Intl.DateTimeFormat(locale, {
        timeZone: 'Europe/Kyiv',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      });
      for (const date of dates) {
        expect(kyivDate(date, language)).toBe(formatter.format(new Date(`${date}T12:00:00Z`)));
      }
    }
  });

  it('shows_a_plain_date_without_creating_a_formatter', () => {
    const created = vi.spyOn(Intl, 'DateTimeFormat');

    try {
      expect(kyivDate('2026-10-09', 'uk')).toBe('09.10.2026');
      expect(kyivDate('2026-10-09', 'en')).toBe('09/10/2026');
      expect(created).not.toHaveBeenCalled();
    } finally {
      created.mockRestore();
    }
  });

  it('ac2_1_formats_times_in_kyiv_in_both_languages', () => {
    expect(kyivDateTime('2026-09-30T21:30:00Z', 'uk')).toBe('01.10.2026, 00:30');
    expect(kyivDateTime('2026-12-01T10:00:00Z', 'en')).toBe('01/12/2026, 12:00');
  });

  it('ac2_1_takes_today_from_the_kyiv_calendar', () => {
    expect(kyivToday(new Date('2026-09-27T21:30:00Z'))).toBe('2026-09-28');
    expect(kyivToday(new Date('2026-09-27T20:30:00Z'))).toBe('2026-09-27');
  });

  it('ac2_2_the_oldest_date_is_fifty_years_back_and_a_leap_day_becomes_the_28th', () => {
    expect(yearsBefore('2026-09-28', 50)).toBe('1976-09-28');
    expect(yearsBefore('2028-02-29', 50)).toBe('1978-02-28');
    expect(yearsBefore('2028-02-29', 4)).toBe('2024-02-29');
  });

  it('ac2_3_counts_days_back_across_month_ends', () => {
    expect(daysBefore('2026-03-01', 1)).toBe('2026-02-28');
  });
});
