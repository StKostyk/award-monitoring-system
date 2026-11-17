import { organizationName } from '../../shared/organization-name';
import {
  FactSource,
  YearToDate,
  facts,
  levelAreas,
  pickFact,
  records,
  yearToDate,
} from '../../shared/widgets/facts';
import {
  CalendarMonth,
  Fact,
  LabelledCount,
  LevelCount,
  MonthCount,
  TopPerson,
  YearTotal,
} from '../../shared/widgets/widget-data';
import { UnitComparisonRow } from '../analytics/units/unit-analytics.service';

/** People or units P2 lists at most. */
export const TOP_LIMIT = 5;

/** The counts of a university or unit analytics response the Hall of Fame reads. */
export interface HallOfFameSource {
  period: { from: number; to: number };
  partialYear: boolean;
  byYear: { year: number; total: number }[];
  byMonth: MonthCount[];
  byLevel: LevelCount[];
}

/** What the Hall of Fame widgets show, worked out once from one response. */
export interface HallOfFameView {
  year: number;
  today: CalendarMonth;
  /** Approved awards of the last year of the period. */
  approved: number;
  sparkline: number[];
  delta: YearToDate | null;
  years: YearTotal[];
  partialYear: number | null;
  byMonth: MonthCount[];
  records: Fact[];
  fact: Fact | null;
  top: TopPerson[];
  areas: LabelledCount[];
}

/**
 * The widget data of the Hall of Fame.
 *
 * @param source the counts of the analytics response
 * @param top the people or units P2 lists, most awards first
 * @param leader the person or unit the records name, or null
 * @param today the current Kyiv month
 * @param seed the number that picks the fact of the day, e.g. the day of the month
 * @returns the data of every widget
 */
export function hallOfFameView(
  source: HallOfFameSource,
  top: TopPerson[],
  leader: TopPerson | null,
  today: CalendarMonth,
  seed: number,
): HallOfFameView {
  const year = source.period.to;
  const years = source.byYear.map((row) => ({ year: row.year, total: row.total }));
  const counts: FactSource = {
    period: source.period,
    byYear: years,
    byMonth: source.byMonth,
    byLevel: source.byLevel,
    today,
  };
  return {
    year,
    today,
    approved: years.find((row) => row.year === year)?.total ?? 0,
    sparkline: years.map((row) => row.total),
    delta: year === today.year ? yearToDate(source.byMonth, year, today) : null,
    years,
    partialYear: source.partialYear ? year : null,
    byMonth: source.byMonth,
    records: records(counts, leader),
    fact: pickFact(facts(counts), seed),
    top: top.slice(0, TOP_LIMIT),
    areas: levelAreas(source.byLevel),
  };
}

/**
 * The units of a comparison with most approved awards in the period, for P2 and the leader record.
 *
 * @param units the comparison rows
 * @param language the active language
 * @returns the units with at least one award, most first, by name
 */
export function topUnits(units: UnitComparisonRow[], language: string): TopPerson[] {
  return units
    .filter((row) => !row.facultyLevel && row.approved.current > 0)
    .sort((a, b) => b.approved.current - a.approved.current)
    .map((row) => ({ name: organizationName(row.unit, language), count: row.approved.current }));
}
