import { convertToParamMap } from '@angular/router';

import { currentKyivYear } from '../../shared/date-format';
import { readPeriod } from './analytics-period';

describe('readPeriod', () => {
  const read = (params: Record<string, string>) => readPeriod(convertToParamMap(params), 2026);

  it('ac4_1_reads_the_years_of_the_address', () => {
    expect(read({})).toEqual({ from: null, to: null });
    expect(read({ from: '2020' })).toEqual({ from: 2020, to: null });
    expect(read({ to: '2024' })).toEqual({ from: null, to: 2024 });
    expect(read({ from: '1950', to: '2026' })).toEqual({ from: 1950, to: 2026 });
    expect(read({ from: '2024', to: '2024' })).toEqual({ from: 2024, to: 2024 });
  });

  it('ac4_1_refuses_a_year_after_the_current_kyiv_year', () => {
    expect(read({ from: '2027' })).toBeNull();
    expect(read({ to: '2027' })).toBeNull();
    expect(read({ from: '2020', to: '2100' })).toBeNull();
  });

  it('ac4_1_refuses_reversed_years_and_values_that_are_not_years', () => {
    expect(read({ from: '2024', to: '2020' })).toBeNull();
    expect(read({ from: '1949' })).toBeNull();
    expect(read({ from: 'abc' })).toBeNull();
    expect(read({ to: '2020.5' })).toBeNull();
    expect(read({ from: '' })).toBeNull();
  });

  it('ac4_1_takes_the_current_year_in_kyiv', () => {
    expect(currentKyivYear(new Date('2026-12-31T22:30:00Z'))).toBe(2027);
    expect(currentKyivYear(new Date('2026-10-19T10:00:00Z'))).toBe(2026);
  });
});
