import {
  FactSource,
  bestYearFact,
  crossing,
  cumulative,
  currentKyivMonth,
  facts,
  firstAwardFact,
  growthFact,
  levelAreas,
  levelShareFact,
  levelSteps,
  peakYear,
  records,
  streak,
  streakFact,
  yearToDate,
} from './facts';
import { MonthCount } from './widget-data';

function months(year: number, counts: number[]): MonthCount[] {
  return counts.map((count, index) => ({ year, month: index + 1, count }));
}

function source(): FactSource {
  return {
    period: { from: 2022, to: 2026 },
    byYear: [
      { year: 2022, total: 20 },
      { year: 2023, total: 25 },
      { year: 2024, total: 40 },
      { year: 2025, total: 35 },
      { year: 2026, total: 30 },
    ],
    byMonth: [
      ...months(2025, [2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3]),
      ...months(2026, [1, 1, 6, 4, 4, 4, 4, 3, 3, 0, 0, 0]),
    ],
    byLevel: [
      { level: 'FACULTY', count: 80 },
      { level: 'NATIONAL', count: 40 },
      { level: 'INTERNATIONAL', count: 30 },
    ],
    today: { year: 2026, month: 9 },
  };
}

describe('facts', () => {
  it('ac2_1_takes_the_current_month_in_kyiv', () => {
    expect(currentKyivMonth(new Date('2026-12-31T22:30:00Z'))).toEqual({ year: 2027, month: 1 });
  });

  it('ac2_2_compares_the_complete_months_of_the_year_with_the_same_months_of_last_year', () => {
    expect(yearToDate(source().byMonth, 2026, { year: 2026, month: 9 })).toEqual({
      year: 2026,
      months: 8,
      current: 27,
      previous: 23,
      delta: 4,
      deltaPercent: (4 * 100) / 23,
    });
    expect(yearToDate(source().byMonth, 2026, { year: 2026, month: 2 }).deltaPercent).toBe(-50);
    expect(yearToDate(months(2026, [3]), 2026, { year: 2026, month: 1 }).deltaPercent).toBeNull();
    expect(yearToDate(source().byMonth, 2025, { year: 2026, month: 2 }).current).toBe(35);
  });

  it('ac2_3_runs_the_totals_and_finds_the_month_the_lead_changed', () => {
    const thisYear = cumulative(source().byMonth, 2026, 9);
    const lastYear = cumulative(source().byMonth, 2025);

    expect(thisYear).toEqual([1, 2, 8, 12, 16, 20, 24, 27, 30]);
    expect(lastYear.at(-1)).toBe(35);
    expect(crossing(thisYear, lastYear)).toEqual({ month: 4, kind: 'OVERTOOK' });
    expect(crossing([5, 5, 5], [1, 2, 9])).toEqual({ month: 3, kind: 'FELL_BEHIND' });
    expect(crossing([1, 2, 3], [1, 2, 3])).toBeNull();
    expect(crossing([5, 6], [1, 2])).toBeNull();
  });

  it('ac2_3_marks_the_peak_year_and_the_earliest_on_a_tie', () => {
    expect(peakYear(source().byYear)?.year).toBe(2024);
    expect(
      peakYear([
        { year: 2024, total: 3 },
        { year: 2025, total: 3 },
      ])?.year,
    ).toBe(2024);
    expect(peakYear([{ year: 2024, total: 0 }])).toBeNull();
  });

  it('ac2_4_lists_up_to_three_records_and_leaves_out_those_without_data', () => {
    expect(records(source(), { name: 'ФМІ', count: 80 })).toEqual([
      { key: 'facts.records.peakYear', params: { year: 2024, count: 40 } },
      { key: 'facts.records.leader', params: { name: 'ФМІ', count: 80 } },
      {
        key: 'facts.records.highestLevel',
        params: { period: '2022–2026' },
        keyParams: { level: 'categories.levels.INTERNATIONAL' },
      },
    ]);
    const empty = { ...source(), byYear: [], byLevel: [] };
    expect(records(empty, null)).toEqual([]);
  });

  it('ac2_4_generates_facts_only_from_generators_with_data', () => {
    expect(levelShareFact(source())).toEqual({
      key: 'facts.levelShare',
      params: { percent: 20, period: '2022–2026' },
      keyParams: { level: 'categories.levels.INTERNATIONAL' },
      lead: { key: 'facts.leads.percent', params: { percent: 20 } },
    });
    expect(growthFact(source())).toEqual({
      key: 'facts.growth',
      params: { percent: 17, year: 2025 },
      lead: { key: 'facts.leads.percent', params: { percent: '+17' } },
    });
    expect(streakFact(source())).toEqual({
      key: 'facts.streak',
      params: { years: 5 },
      lead: { key: 'facts.leads.value', params: { value: 5 } },
    });

    const quiet: FactSource = {
      ...source(),
      byYear: [{ year: 2026, total: 1 }],
      byMonth: months(2026, [1]),
      byLevel: [],
    };
    expect(facts(quiet)).toEqual([
      {
        key: 'facts.firstAward',
        params: { year: 2026, period: '2022–2026' },
        lead: { key: 'facts.leads.value', params: { value: 2026 } },
      },
    ]);
    expect(facts({ ...quiet, byYear: [] })).toEqual([]);
    expect(growthFact({ ...source(), today: { year: 2026, month: 2 } })).toBeNull();
    expect(growthFact({ ...source(), today: { year: 2027, month: 3 } })).toBeNull();
    const longer = { ...source(), byYear: [{ year: 2021, total: 0 }, ...source().byYear] };
    expect(streakFact(longer)?.params).toEqual({ years: 5 });
  });

  it('ac2_4_counts_the_streak_up_to_last_year_while_this_year_has_none', () => {
    const byYear = [
      { year: 2021, total: 2 },
      { year: 2022, total: 0 },
      { year: 2023, total: 1 },
      { year: 2024, total: 1 },
      { year: 2025, total: 4 },
      { year: 2026, total: 0 },
    ];

    expect(streak(byYear, { year: 2026, month: 1 })).toEqual({ since: 2023, years: 3 });
    expect(streak(byYear, { year: 2027, month: 1 })).toEqual({ since: 2027, years: 0 });
    expect(streak([], { year: 2026, month: 1 }).years).toBe(0);
  });

  it('ac3_lists_up_to_four_facts_with_the_best_year_and_the_first_award_first', () => {
    expect(bestYearFact(source())).toEqual({
      key: 'facts.bestYear',
      params: { year: 2024, count: 40 },
      lead: { key: 'facts.leads.value', params: { value: 2024 } },
    });
    expect(firstAwardFact(source())).toEqual({
      key: 'facts.firstAward',
      params: { year: 2022, period: '2022–2026' },
      lead: { key: 'facts.leads.value', params: { value: 2022 } },
    });
    expect(facts(source()).map((fact) => fact.key)).toEqual([
      'facts.bestYear',
      'facts.firstAward',
      'facts.streak',
      'facts.growth',
    ]);
    expect(bestYearFact({ ...source(), byYear: [{ year: 2026, total: 4 }] })).toBeNull();
  });

  it('ac2_5_sums_levels_into_steps_and_areas', () => {
    expect(levelSteps(source().byLevel).map((step) => step.count)).toEqual([80, 0, 0, 40, 30]);
    expect(levelSteps(source().byLevel)[0].key).toBe('analytics.bands.UNIT');
    expect(levelAreas([...source().byLevel, { level: 'REGIONAL', count: 0 }])).toEqual([
      { key: 'categories.levels.FACULTY', count: 80 },
      { key: 'categories.levels.NATIONAL', count: 40 },
      { key: 'categories.levels.INTERNATIONAL', count: 30 },
    ]);
  });
});
