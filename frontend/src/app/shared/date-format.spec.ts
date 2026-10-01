import { kyivDateTime } from './date-format';

describe('date format', () => {
  it('ac2_1_formats_times_in_kyiv_in_both_languages', () => {
    expect(kyivDateTime('2026-09-30T21:30:00Z', 'uk')).toBe('01.10.2026, 00:30');
    expect(kyivDateTime('2026-12-01T10:00:00Z', 'en')).toBe('01/12/2026, 12:00');
  });
});
