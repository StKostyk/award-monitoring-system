import { LEVEL_BANDS, levelBand } from '../../features/analytics/analytics.service';
import { RECOGNITION_LEVELS, RecognitionLevel } from '../../features/awards/awards.service';
import { kyivToday } from '../date-format';
import {
  CalendarMonth,
  Fact,
  LabelledCount,
  LevelCount,
  MonthCount,
  TopPerson,
  YearTotal,
} from './widget-data';

/** Years in a row with awards before a streak becomes a fact. */
export const MIN_STREAK = 3;

/** Records H1 lists at most. */
export const MAX_RECORDS = 3;

/** Facts H4 lists at most. */
export const MAX_FACTS = 4;

const MONTHS = 12;
const PERCENT = 100;

/** What the fact generators read: the counts of an analytics response and the current Kyiv month. */
export interface FactSource {
  period: { from: number; to: number };
  byYear: YearTotal[];
  byMonth: MonthCount[];
  byLevel: LevelCount[];
  today: CalendarMonth;
}

/** A year-to-date count beside the one of the same months a year earlier. */
export interface YearToDate {
  year: number;
  /** How many months from January both counts cover. */
  months: number;
  current: number;
  previous: number;
  delta: number;
  deltaPercent: number | null;
}

/** The month this year's running total overtook last year's or fell behind it. */
export interface Crossing {
  month: number;
  kind: 'OVERTOOK' | 'FELL_BEHIND';
}

/** The current month in Kyiv. */
export function currentKyivMonth(now = new Date()): CalendarMonth {
  const [year, month] = kyivToday(now).split('-').map(Number);
  return { year, month };
}

/**
 * The last month that counts for a year: the current month in the current year, December in a past year.
 *
 * @param year the year
 * @param today the current month
 * @returns the month, 1 to 12
 */
export function lastMonthOf(year: number, today: CalendarMonth): number {
  return year < today.year ? MONTHS : today.month;
}

/**
 * The months of a year that are over: all twelve in a past year, those before the current month in the current
 * year, and January alone while January runs.
 *
 * @param year the year
 * @param today the current month
 * @returns the number of months to compare, 1 to 12
 */
export function completeMonthsOf(year: number, today: CalendarMonth): number {
  return year < today.year ? MONTHS : Math.max(today.month - 1, 1);
}

/** The twelve monthly counts of a year, 0 for months the list leaves out. */
export function monthsOf(byMonth: MonthCount[], year: number): number[] {
  const counts = new Array<number>(MONTHS).fill(0);
  for (const row of byMonth) {
    if (row.year === year && row.month >= 1 && row.month <= MONTHS) {
      counts[row.month - 1] = row.count;
    }
  }
  return counts;
}

/** Running totals of the monthly counts of a year, for the first `months` months. */
export function cumulative(byMonth: MonthCount[], year: number, months = MONTHS): number[] {
  let sum = 0;
  return monthsOf(byMonth, year)
    .slice(0, months)
    .map((count) => (sum += count));
}

/**
 * The count of the year up to its last counted month against the same months of the year before.
 *
 * @param byMonth the monthly counts of both years
 * @param year the year
 * @param today the current month
 * @returns the two counts, their difference and the change in per cent (null when the previous count is 0)
 */
export function yearToDate(byMonth: MonthCount[], year: number, today: CalendarMonth): YearToDate {
  const months = completeMonthsOf(year, today);
  const current = cumulative(byMonth, year, months).at(-1) ?? 0;
  const previous = cumulative(byMonth, year - 1, months).at(-1) ?? 0;
  const delta = current - previous;
  return {
    year,
    months,
    current,
    previous,
    delta,
    deltaPercent: previous === 0 ? null : (delta * PERCENT) / previous,
  };
}

/**
 * The last month in which this year's running total moved ahead of last year's or behind it.
 *
 * @param thisYear running totals of this year, one per month so far
 * @param lastYear running totals of last year
 * @returns the month and the direction, or null when the lead never changed
 */
export function crossing(thisYear: number[], lastYear: number[]): Crossing | null {
  let lead = 0;
  let found: Crossing | null = null;
  thisYear.forEach((total, index) => {
    const sign = Math.sign(total - (lastYear[index] ?? 0));
    if (sign !== 0 && lead !== 0 && sign !== lead) {
      found = { month: index + 1, kind: sign > 0 ? 'OVERTOOK' : 'FELL_BEHIND' };
    }
    if (sign !== 0) {
      lead = sign;
    }
  });
  return found;
}

/** The year with the most awards, the earliest on a tie; null when no year has any. */
export function peakYear(byYear: YearTotal[]): YearTotal | null {
  return byYear.reduce<YearTotal | null>(
    (best, year) => (year.total > (best?.total ?? 0) ? year : best),
    null,
  );
}

/** The highest recognition level with at least one award; null when there is none. */
export function highestLevel(byLevel: LevelCount[]): RecognitionLevel | null {
  const present = new Set(byLevel.filter((row) => row.count > 0).map((row) => row.level));
  return [...RECOGNITION_LEVELS].reverse().find((level) => present.has(level)) ?? null;
}

/** The period as a label: one year, or the first and the last joined by a dash. */
export function periodLabel(period: { from: number; to: number }): string {
  return period.from === period.to ? String(period.to) : `${period.from}–${period.to}`;
}

/**
 * The records of H1: the peak year, the person or unit with most awards and the highest level reached.
 *
 * @param source the counts
 * @param leader the person or unit with most awards, or null
 * @returns at most three records, those without data left out
 */
export function records(source: FactSource, leader: TopPerson | null): Fact[] {
  const found: Fact[] = [];
  const peak = peakYear(source.byYear);
  if (peak) {
    found.push({ key: 'facts.records.peakYear', params: { year: peak.year, count: peak.total } });
  }
  if (leader && leader.count > 0) {
    found.push({ key: 'facts.records.leader', params: { name: leader.name, count: leader.count } });
  }
  const level = highestLevel(source.byLevel);
  if (level) {
    found.push({
      key: 'facts.records.highestLevel',
      params: { period: periodLabel(source.period) },
      keyParams: { level: `categories.levels.${level}` },
    });
  }
  return found.slice(0, MAX_RECORDS);
}

/** The share of awards at the highest level reached, when there are awards. */
export function levelShareFact(source: FactSource): Fact | null {
  const level = highestLevel(source.byLevel);
  const total = source.byLevel.reduce((sum, row) => sum + row.count, 0);
  if (!level || total === 0) {
    return null;
  }
  const count = source.byLevel
    .filter((row) => row.level === level)
    .reduce((sum, row) => sum + row.count, 0);
  return {
    key: 'facts.levelShare',
    params: { percent: Math.round((count * PERCENT) / total), period: periodLabel(source.period) },
    keyParams: { level: `categories.levels.${level}` },
  };
}

/** Growth of the year to date against the same months of the year before, when there is growth to tell. */
export function growthFact(source: FactSource): Fact | null {
  if (source.period.to !== source.today.year) {
    return null;
  }
  const ytd = yearToDate(source.byMonth, source.period.to, source.today);
  if (ytd.deltaPercent === null || ytd.delta <= 0) {
    return null;
  }
  return {
    key: 'facts.growth',
    params: { percent: Math.round(ytd.deltaPercent), year: ytd.year - 1 },
  };
}

/**
 * Years in a row with awards up to the last year, or up to the year before when the current year has none yet.
 *
 * @param byYear the yearly counts
 * @param today the current month
 * @returns the first year of the streak and its length
 */
export function streak(
  byYear: YearTotal[],
  today: CalendarMonth,
): { since: number; years: number } {
  const sorted = [...byYear].sort((a, b) => a.year - b.year);
  const last = sorted.at(-1);
  const rows = last && last.year === today.year && last.total === 0 ? sorted.slice(0, -1) : sorted;
  let years = 0;
  for (let index = rows.length - 1; index >= 0 && rows[index].total > 0; index--) {
    const next = rows[index + 1];
    if (index < rows.length - 1 && next.year !== rows[index].year + 1) {
      break;
    }
    years++;
  }
  return { since: (rows.at(-1)?.year ?? today.year) - years + 1, years };
}

/** The streak of years with awards up to now, when it is at least `MIN_STREAK` long. */
export function streakFact(source: FactSource): Fact | null {
  const found = streak(source.byYear, source.today);
  return found.years < MIN_STREAK ? null : { key: 'facts.streak', params: { years: found.years } };
}

/** The year with most awards, when at least two years have any. */
export function bestYearFact(source: FactSource): Fact | null {
  const peak = peakYear(source.byYear);
  if (!peak || source.byYear.filter((year) => year.total > 0).length < 2) {
    return null;
  }
  return { key: 'facts.bestYear', params: { year: peak.year, count: peak.total } };
}

/** The earliest year of the period with an award, when there is one. */
export function firstAwardFact(source: FactSource): Fact | null {
  const years = source.byYear.filter((year) => year.total > 0).map((year) => year.year);
  return years.length === 0
    ? null
    : {
        key: 'facts.firstAward',
        params: { year: Math.min(...years), period: periodLabel(source.period) },
      };
}

/** The facts the counts support, in a fixed order, at most `MAX_FACTS`. */
export function facts(source: FactSource): Fact[] {
  return [
    bestYearFact(source),
    firstAwardFact(source),
    streakFact(source),
    growthFact(source),
    levelShareFact(source),
  ]
    .filter((fact): fact is Fact => fact !== null)
    .slice(0, MAX_FACTS);
}

/**
 * The sentence of a fact in the active language.
 *
 * @param fact the fact
 * @param translate the translation function, e.g. `TranslocoService.translate`
 * @returns the sentence with its values, the key values translated first
 */
export function translateFact(
  fact: Fact,
  translate: (key: string, params?: Record<string, unknown>) => string,
): string {
  const translated = Object.fromEntries(
    Object.entries(fact.keyParams ?? {}).map(([name, key]) => [name, translate(key)]),
  );
  return translate(fact.key, { ...fact.params, ...translated });
}

/** The level counts summed by band, from the unit levels up to the international one, as label keys. */
export function levelSteps(byLevel: LevelCount[]): LabelledCount[] {
  return LEVEL_BANDS.map((band) => ({
    key: `analytics.bands.${band}`,
    count: byLevel
      .filter((row) => levelBand(row.level as RecognitionLevel) === band)
      .reduce((sum, row) => sum + row.count, 0),
  }));
}

/** The level counts with at least one award, largest first, as label keys. */
export function levelAreas(byLevel: LevelCount[]): LabelledCount[] {
  return byLevel
    .filter((row) => row.count > 0)
    .sort((a, b) => b.count - a.count)
    .map((row) => ({ key: `categories.levels.${row.level}`, count: row.count }));
}
