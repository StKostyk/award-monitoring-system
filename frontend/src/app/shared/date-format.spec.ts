import { daysBefore, kyivDateTime, kyivToday, yearsBefore } from './date-format';

describe('date format', () => {
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
