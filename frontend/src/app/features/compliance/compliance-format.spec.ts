import { durationPhrase, isErasedEmail, periodPhrases } from './compliance-format';

describe('compliance format', () => {
  it('ac2_5_names_each_part_of_an_iso_period', () => {
    expect(periodPhrases('P7Y')).toEqual([{ key: 'compliance.units.years', params: { count: 7 } }]);
    expect(periodPhrases('PT5M')).toEqual([
      { key: 'compliance.units.minutes', params: { count: 5 } },
    ]);
    expect(periodPhrases('P1Y6M')).toEqual([
      { key: 'compliance.units.years', params: { count: 1 } },
      { key: 'compliance.units.months', params: { count: 6 } },
    ]);
    expect(periodPhrases('seven years')).toEqual([]);
  });

  it('ac2_5_gives_the_duration_of_a_finished_run_only', () => {
    const start = '2026-10-18T01:00:00Z';

    expect(durationPhrase(start, null)).toBeNull();
    expect(durationPhrase(start, '2026-10-18T01:00:42Z')).toEqual({
      key: 'compliance.duration.seconds',
      params: { seconds: 42 },
    });
    expect(durationPhrase(start, '2026-10-18T01:03:05Z')).toEqual({
      key: 'compliance.duration.minutes',
      params: { minutes: 3, seconds: 5 },
    });
    expect(durationPhrase(start, '2026-10-18T03:10:00Z')).toEqual({
      key: 'compliance.duration.hours',
      params: { hours: 2, minutes: 10 },
    });
  });

  it('ac2_3_recognises_the_address_of_an_erased_account', () => {
    expect(isErasedEmail('erased-41@erased.invalid')).toBe(true);
    expect(isErasedEmail('employee.fmi@chnu.edu.ua')).toBe(false);
  });
});
