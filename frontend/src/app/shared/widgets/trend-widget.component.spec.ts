import { vi } from 'vitest';

import { TrendWidgetComponent, trendRows } from './trend-widget.component';
import { byTestId, lastOption, renderWidget, rowTexts, showData } from './widgets.testing';

const YEARS = [
  { year: 2024, total: 4 },
  { year: 2025, total: 9 },
  { year: 2026, total: 6 },
];
/** Room above the tallest bar for one and for two label lines. */
const ONE_LINE_ROOM = 24;
const TWO_LINE_ROOM = 40;

interface BarOption {
  series: {
    data: {
      value: number;
      itemStyle: { opacity: number };
      label: {
        show: boolean;
        formatter: string;
        align?: string;
      };
    }[];
  }[];
}

describe('TrendWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_3_marks_the_peak_year_and_the_partial_year', async () => {
    const { stub } = await renderWidget(TrendWidgetComponent, { years: YEARS, partialYear: 2026 });

    const bars = lastOption<BarOption>(stub).series[0].data;
    expect(bars.map((bar) => bar.value)).toEqual([4, 9, 6]);
    expect(bars.map((bar) => bar.label.show)).toEqual([false, true, true]);
    expect(bars[1].label.formatter).toBe('Пік');
    expect(bars[2].label.formatter).toBe('Неповний рік');
    expect(bars[2].itemStyle.opacity).toBeLessThan(1);
    expect(trendRows(YEARS, 2025).filter((row) => row.peak && row.partial)).toHaveLength(1);
    expect(trendRows(YEARS, null).map((row) => row.partial)).toEqual([false, false, false]);
  });

  it('ac2_3_names_both_marks_when_the_peak_year_is_the_partial_one', async () => {
    const { stub } = await renderWidget(TrendWidgetComponent, { years: YEARS, partialYear: 2025 });

    const bars = lastOption<BarOption>(stub).series[0].data;
    expect(bars[1].label.formatter).toBe('Пік\nНеповний рік');
  });

  it('ac1_6_keeps_the_edge_annotations_inside_the_chart', async () => {
    const { stub } = await renderWidget(TrendWidgetComponent, { years: YEARS, partialYear: 2026 });

    const option = lastOption<BarOption & { grid: { top: number } }>(stub);
    const labels = option.series[0].data.map((bar) => bar.label);
    expect(labels.map((label) => label.align)).toEqual(['left', 'center', 'right']);
    expect(option.grid.top).toBe(ONE_LINE_ROOM);
  });

  it('ac1_6_leaves_room_for_two_lines_only_when_a_bar_carries_both_marks', async () => {
    const { stub } = await renderWidget(TrendWidgetComponent, { years: YEARS, partialYear: 2025 });

    expect(lastOption<{ grid: { top: number } }>(stub).grid.top).toBe(TWO_LINE_ROOM);
  });

  it('ac2_6_shows_the_years_as_a_table_behind_data', async () => {
    const { fixture } = await renderWidget(TrendWidgetComponent, {
      years: YEARS,
      partialYear: 2026,
    });

    expect(byTestId(fixture, 'chart-table-switch')?.textContent).toContain('Дані');
    expect(rowTexts(showData(fixture)!)).toEqual(['2024 4', '2025 9 Пік', '2026 6 Неповний рік']);
    expect(byTestId(fixture, 'chart-canvas')).toBeNull();
  });

  it('ac2_5_shows_an_empty_state_without_awards', async () => {
    const { fixture, text } = await renderWidget(TrendWidgetComponent, {
      years: [{ year: 2026, total: 0 }],
    });

    expect(byTestId(fixture, 'chart-canvas')).toBeNull();
    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
