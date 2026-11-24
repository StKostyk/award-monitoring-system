import { TrendWidgetComponent, trendRows } from './trend-widget.component';
import { byTestId, renderWidget, rowTexts, showView } from './widgets.testing';

const YEARS = [
  { year: 2024, total: 4 },
  { year: 2025, total: 9 },
  { year: 2026, total: 6 },
];

function rows(host: HTMLElement): HTMLElement[] {
  return Array.from(host.querySelectorAll('[data-testid="bar-row"]'));
}

function part(row: HTMLElement, name: string): string | null {
  return row.querySelector(`.bars__${name}`)?.textContent?.trim() ?? null;
}

describe('TrendWidgetComponent', () => {
  it('ac5_2_draws_a_thick_bar_per_year_and_hatches_the_running_year', async () => {
    const { fixture, text } = await renderWidget(TrendWidgetComponent, {
      years: YEARS,
      partialYear: 2026,
    });

    const bars = byTestId(fixture, 'trend-widget-bars')!;
    const drawn = rows(bars);
    expect(drawn.map((row) => `${part(row, 'label')} ${part(row, 'count')}`)).toEqual([
      '2024 4',
      '2025 9',
      '2026 6',
    ]);
    expect(bars.querySelector('.bars--thick')).not.toBeNull();
    expect(drawn[2].classList).toContain('bars__row--current');
    expect(part(drawn[2], 'running')).toBe('рік триває');
    expect(drawn[1].querySelector('[data-testid="bar-running"]')).toBeNull();
    expect(text()).toContain('2025 — рекордний рік: 9.');
    expect(byTestId(fixture, 'trend-widget-table')).toBeNull();
  });

  it('ac2_3_marks_the_peak_year_and_the_partial_year', () => {
    expect(trendRows(YEARS, 2026).map((row) => [row.peak, row.partial])).toEqual([
      [false, false],
      [true, false],
      [false, true],
    ]);
    expect(trendRows(YEARS, null).map((row) => row.partial)).toEqual([false, false, false]);
  });

  it('ac5_2_says_when_the_record_is_the_running_year', async () => {
    const { text } = await renderWidget(TrendWidgetComponent, {
      years: [
        { year: 2025, total: 4 },
        { year: 2026, total: 9 },
      ],
      partialYear: 2026,
    });

    expect(text()).toContain('2026 — рекорд: 9, і рік ще триває.');
  });

  it('ac5_2_has_no_caption_for_a_single_year', async () => {
    const { fixture } = await renderWidget(TrendWidgetComponent, {
      years: [{ year: 2026, total: 3 }],
    });

    expect(fixture.nativeElement.querySelector('.trend__caption')).toBeNull();
  });

  it('ac2_6_shows_the_years_as_a_table_in_the_table_view', async () => {
    const { fixture } = await renderWidget(TrendWidgetComponent, {
      years: YEARS,
      partialYear: 2026,
    });

    showView(fixture);

    expect(rowTexts(byTestId(fixture, 'trend-widget-table')!)).toEqual([
      '2024 4',
      '2025 9 Пік',
      '2026 6 Неповний рік',
    ]);
    expect(byTestId(fixture, 'trend-widget-bars')).toBeNull();
  });

  it('ac2_5_shows_an_empty_state_without_awards', async () => {
    const { fixture, text } = await renderWidget(TrendWidgetComponent, {
      years: [{ year: 2026, total: 0 }],
    });

    expect(byTestId(fixture, 'trend-widget-bars')).toBeNull();
    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
