import { TestBed } from '@angular/core/testing';
import { DateAdapter } from '@angular/material/core';

import {
  DATE_LOCALES,
  ISO_DATE_FORMATS,
  IsoDateAdapter,
  provideIsoDateAdapter,
} from './iso-date-adapter';

describe('IsoDateAdapter', () => {
  let adapter: IsoDateAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideIsoDateAdapter()] });
    adapter = TestBed.inject(DateAdapter) as IsoDateAdapter;
  });

  it('shows dates day first with dots in Ukrainian and slashes in English', () => {
    expect(adapter.format('2026-10-04', ISO_DATE_FORMATS.display.dateInput)).toBe('04.10.2026');
    adapter.setLocale(DATE_LOCALES.en);
    expect(adapter.format('2026-10-04', ISO_DATE_FORMATS.display.dateInput)).toBe('04/10/2026');
  });

  it('reads typed day-first dates and ISO dates into ISO values', () => {
    expect(adapter.parse('4.10.2026')).toBe('2026-10-04');
    expect(adapter.parse('04/10/2026')).toBe('2026-10-04');
    expect(adapter.parse('2026-10-04')).toBe('2026-10-04');
    expect(adapter.parse('  ')).toBeNull();
  });

  it('refuses dates that do not exist', () => {
    expect(adapter.isValid(adapter.parse('31.02.2026') as string)).toBe(false);
    expect(adapter.isValid(adapter.parse('2026-13-01') as string)).toBe(false);
    expect(adapter.isValid(adapter.parse('yesterday') as string)).toBe(false);
    expect(adapter.format(adapter.invalid(), ISO_DATE_FORMATS.display.dateInput)).toBe('');
  });

  it('does calendar arithmetic on ISO values and starts weeks on Monday', () => {
    expect(adapter.addCalendarDays('2026-02-28', 1)).toBe('2026-03-01');
    expect(adapter.addCalendarMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(adapter.addCalendarYears('2024-02-29', 1)).toBe('2025-02-28');
    expect(adapter.createDate(2026, 9, 4)).toBe('2026-10-04');
    expect(adapter.getNumDaysInMonth('2024-02-10')).toBe(29);
    expect(adapter.getFirstDayOfWeek()).toBe(1);
    expect(adapter.compareDate('2026-10-04', '2026-10-05')).toBeLessThan(0);
  });

  it('accepts ISO strings as bound values and drops empty ones', () => {
    expect(adapter.deserialize('2026-10-04')).toBe('2026-10-04');
    expect(adapter.deserialize('')).toBeNull();
    expect(adapter.isValid(adapter.deserialize('04.10.2026') as string)).toBe(false);
    expect(adapter.today()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
