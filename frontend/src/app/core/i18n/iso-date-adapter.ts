import { EnvironmentProviders, Injectable, inject, makeEnvironmentProviders } from '@angular/core';
import {
  DateAdapter,
  MAT_DATE_FORMATS,
  MAT_DATE_LOCALE,
  MatDateFormats,
  NativeDateAdapter,
} from '@angular/material/core';

const ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
const TYPED = /^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/;
const INVALID = 'invalid';
const INPUT_FORMAT = 'input';
const MONDAY = 1;

/** Locale of the date picker for each UI language. */
export const DATE_LOCALES = { uk: 'uk-UA', en: 'en-GB' } as const;

/** Display formats: the typed field uses the day-first numeric form of the locale. */
export const ISO_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: INPUT_FORMAT },
  display: {
    dateInput: INPUT_FORMAT,
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
  },
};

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function toDate(iso: string): Date {
  const [, year, month, day] = ISO.exec(iso) ?? [];
  return new Date(Number(year), Number(month) - 1, Number(day));
}

function existing(year: number, month: number, day: number): string | null {
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
    ? toIso(date)
    : null;
}

/**
 * Date adapter whose values are ISO calendar dates (`yyyy-MM-dd` strings), as the API expects, shown and typed
 * day first: `dd.MM.yyyy` in Ukrainian, `dd/MM/yyyy` in English.
 */
@Injectable()
export class IsoDateAdapter extends DateAdapter<string> {
  private readonly native = new NativeDateAdapter();

  constructor() {
    super();
    this.setLocale(inject<string | null>(MAT_DATE_LOCALE, { optional: true }) ?? DATE_LOCALES.uk);
  }

  override setLocale(locale: string): void {
    super.setLocale(locale);
    this.native.setLocale(locale);
  }

  getYear(date: string): number {
    return toDate(date).getFullYear();
  }

  getMonth(date: string): number {
    return toDate(date).getMonth();
  }

  getDate(date: string): number {
    return toDate(date).getDate();
  }

  getDayOfWeek(date: string): number {
    return toDate(date).getDay();
  }

  getMonthNames(style: 'long' | 'short' | 'narrow'): string[] {
    return this.native.getMonthNames(style);
  }

  getDateNames(): string[] {
    return this.native.getDateNames();
  }

  getDayOfWeekNames(style: 'long' | 'short' | 'narrow'): string[] {
    return this.native.getDayOfWeekNames(style);
  }

  getYearName(date: string): string {
    return String(this.getYear(date));
  }

  getFirstDayOfWeek(): number {
    return MONDAY;
  }

  getNumDaysInMonth(date: string): number {
    return this.native.getNumDaysInMonth(toDate(date));
  }

  clone(date: string): string {
    return date;
  }

  createDate(year: number, month: number, date: number): string {
    return toIso(this.native.createDate(year, month, date));
  }

  today(): string {
    return toIso(new Date());
  }

  parse(value: unknown): string | null {
    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }
    const text = value.trim();
    const iso = ISO.exec(text);
    if (iso) {
      return existing(Number(iso[1]), Number(iso[2]), Number(iso[3])) ?? INVALID;
    }
    const typed = TYPED.exec(text);
    return typed
      ? (existing(Number(typed[3]), Number(typed[2]), Number(typed[1])) ?? INVALID)
      : INVALID;
  }

  format(date: string, displayFormat: unknown): string {
    if (!this.isValid(date)) {
      return '';
    }
    if (displayFormat === INPUT_FORMAT) {
      const separator = String(this.locale).startsWith('uk') ? '.' : '/';
      const parsed = toDate(date);
      return [pad(parsed.getDate()), pad(parsed.getMonth() + 1), parsed.getFullYear()].join(
        separator,
      );
    }
    return this.native.format(toDate(date), displayFormat as Intl.DateTimeFormatOptions);
  }

  addCalendarYears(date: string, years: number): string {
    return toIso(this.native.addCalendarYears(toDate(date), years));
  }

  addCalendarMonths(date: string, months: number): string {
    return toIso(this.native.addCalendarMonths(toDate(date), months));
  }

  addCalendarDays(date: string, days: number): string {
    return toIso(this.native.addCalendarDays(toDate(date), days));
  }

  toIso8601(date: string): string {
    return date;
  }

  isDateInstance(obj: unknown): boolean {
    return typeof obj === 'string';
  }

  isValid(date: string): boolean {
    const iso = ISO.exec(date);
    return iso !== null && existing(Number(iso[1]), Number(iso[2]), Number(iso[3])) !== null;
  }

  invalid(): string {
    return INVALID;
  }

  override deserialize(value: unknown): string | null {
    if (value === '' || value === null || value === undefined) {
      return null;
    }
    return typeof value === 'string' && this.isValid(value) ? value : this.invalid();
  }
}

/**
 * Date picker providers: ISO string values, day-first display, Ukrainian locale until the language service
 * switches it.
 *
 * @return the providers
 */
export function provideIsoDateAdapter(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: MAT_DATE_LOCALE, useValue: DATE_LOCALES.uk },
    { provide: DateAdapter, useClass: IsoDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: ISO_DATE_FORMATS },
  ]);
}
