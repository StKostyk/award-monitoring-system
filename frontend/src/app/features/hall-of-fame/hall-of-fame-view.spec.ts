import { universityAnalytics } from '../analytics/university/university-analytics.testing';
import { TOP_LIMIT, hallOfFameView, topUnits } from './hall-of-fame-view';

const SEPTEMBER = { year: 2026, month: 9 };

describe('hallOfFameView', () => {
  it('ac3_1 takes K1 from the last year of the period with the delta of complete months', () => {
    const view = hallOfFameView(universityAnalytics(), [], null, SEPTEMBER, 17);

    expect(view.year).toBe(2026);
    expect(view.approved).toBe(30);
    expect(view.sparkline).toEqual([20, 25, 30, 35, 30]);
    expect(view.delta).toEqual(
      expect.objectContaining({ year: 2026, current: 27, previous: 23, delta: 4 }),
    );
    expect(view.partialYear).toBe(2026);
    expect(view.today).toEqual(SEPTEMBER);
  });

  it('leaves the delta out when the period ends before the current year', () => {
    const data = { ...universityAnalytics(), period: { from: 2022, to: 2025 }, partialYear: false };

    const view = hallOfFameView(data, [], null, SEPTEMBER, 1);

    expect(view.approved).toBe(35);
    expect(view.delta).toBeNull();
    expect(view.partialYear).toBeNull();
  });

  it('names the leader in the records and keeps at most five in the top list', () => {
    const top = Array.from({ length: 7 }, (_, index) => ({
      name: `Unit ${index}`,
      count: 10 - index,
    }));

    const view = hallOfFameView(universityAnalytics(), top, top[0], SEPTEMBER, 1);

    expect(view.top.length).toBe(TOP_LIMIT);
    expect(view.records.map((fact) => fact.key)).toContain('facts.records.leader');
    expect(view.records.find((fact) => fact.key === 'facts.records.leader')?.params).toEqual({
      name: 'Unit 0',
      count: 10,
    });
  });

  it('picks the same fact for the same seed and draws the levels by count', () => {
    const first = hallOfFameView(universityAnalytics(), [], null, SEPTEMBER, 17);
    const again = hallOfFameView(universityAnalytics(), [], null, SEPTEMBER, 17);

    expect(first.fact).not.toBeNull();
    expect(again.fact).toEqual(first.fact);
    expect(first.areas).toEqual([{ key: 'categories.levels.NATIONAL', count: 40 }]);
  });

  it('has no fact, record or area without awards', () => {
    const data = {
      ...universityAnalytics(),
      byYear: [],
      byMonth: [],
      byLevel: [],
    };

    const view = hallOfFameView(data, [], null, SEPTEMBER, 3);

    expect(view.approved).toBe(0);
    expect(view.records).toEqual([]);
    expect(view.areas).toEqual([]);
  });
});

describe('topUnits', () => {
  it('ac3_1 ranks the faculties by approved awards and leaves out the university level', () => {
    expect(topUnits(universityAnalytics().units, 'en')).toEqual([
      { name: 'Faculty of Mathematics and Informatics', count: 80 },
      { name: 'Vyzhnytsia College of Applied Arts', count: 5 },
    ]);
  });

  it('uses the Ukrainian names in Ukrainian', () => {
    expect(topUnits(universityAnalytics().units, 'uk')[0].name).toBe(
      'Факультет математики та інформатики',
    );
  });
});
