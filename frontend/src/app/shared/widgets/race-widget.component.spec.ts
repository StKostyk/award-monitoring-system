import { vi } from 'vitest';

import { monthCounts } from '../../features/analytics/units/unit-analytics.testing';
import { RaceWidgetComponent, monthNames } from './race-widget.component';
import { byTestId, lastOption, renderWidget, rowTexts, showData } from './widgets.testing';

const BY_MONTH = [
  ...monthCounts(2025, [2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3]),
  ...monthCounts(2026, [1, 1, 6, 4, 4, 4, 4, 3, 3]),
];

interface LineOption {
  series: {
    name: string;
    data: number[];
    markPoint?: { label: { formatter: string }; data: { coord: [number, number] }[] };
  }[];
}

describe('RaceWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_3_draws_both_years_cumulatively_and_annotates_the_overtaking_month', async () => {
    const { stub, fixture } = await renderWidget(RaceWidgetComponent, {
      byMonth: BY_MONTH,
      year: 2026,
      today: { year: 2026, month: 9 },
    });

    const [last, current] = lastOption<LineOption>(stub).series;
    expect(last.name).toBe('2025');
    expect(last.data).toHaveLength(12);
    expect(last.data.at(-1)).toBe(35);
    expect(current.data).toEqual([1, 2, 8, 12, 16, 20, 24, 27, 30]);
    expect(current.markPoint?.label.formatter).toBe('обгін');
    expect(current.markPoint?.data[0].coord).toEqual([3, 12]);
    const canvas = byTestId(fixture, 'chart-canvas')!;
    expect(canvas.getAttribute('aria-label')).toContain(`обгін: ${monthNames('uk')[3]}`);
  });

  it('ac2_3_has_no_annotation_without_a_crossing', async () => {
    const { stub } = await renderWidget(RaceWidgetComponent, {
      byMonth: [...monthCounts(2025, [1, 1]), ...monthCounts(2026, [3, 3])],
      year: 2026,
      today: { year: 2026, month: 2 },
    });

    const current = lastOption<LineOption>(stub).series[1];
    expect(current.markPoint).toBeUndefined();
  });

  it('ac2_6_shows_the_months_as_a_table_behind_data', async () => {
    const { fixture } = await renderWidget(RaceWidgetComponent, {
      byMonth: BY_MONTH,
      year: 2026,
      today: { year: 2026, month: 9 },
    });

    const rows = rowTexts(showData(fixture)!);
    expect(rows).toHaveLength(12);
    expect(rows[8]).toBe(`${monthNames('uk')[8]} 30 26`);
    expect(rows[11]).toBe(`${monthNames('uk')[11]}  35`);
  });

  it('ac2_5_shows_an_empty_state_without_awards', async () => {
    const { text } = await renderWidget(RaceWidgetComponent, {
      byMonth: [],
      year: 2026,
      today: { year: 2026, month: 3 },
    });

    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
