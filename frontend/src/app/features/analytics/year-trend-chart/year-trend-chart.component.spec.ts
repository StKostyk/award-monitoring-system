import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { paneStub } from '../../../shared/layout/pane.testing';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { YearTrendChartComponent, yearRows, yearSummary } from './year-trend-chart.component';

describe('YearTrendChartComponent', () => {
  let fixture: ComponentFixture<YearTrendChartComponent>;
  let pane: ReturnType<typeof paneStub>;

  beforeEach(() => {
    pane = paneStub();
    TestBed.configureTestingModule({
      imports: [YearTrendChartComponent, NoopAnimationsModule, translations('uk')],
      providers: [pane.provider, languageStub('uk').provider],
    });
    fixture = TestBed.createComponent(YearTrendChartComponent);
    fixture.componentRef.setInput('byYear', sampleAnalytics().byYear);
    fixture.detectChanges();
  });

  function text(testId: string): string {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`).textContent.trim();
  }

  it('ac2_5_starts_at_the_first_year_with_awards_unless_the_start_was_chosen', () => {
    const byYear = sampleAnalytics().byYear;

    expect(yearRows(byYear, false).map((row) => row.year)).toEqual([2024, 2025, 2026]);
    expect(yearRows(byYear, true).map((row) => row.year)).toEqual([2023, 2024, 2025, 2026]);
    expect(yearRows(byYear, false)[2].bands).toEqual({
      UNIT: 1,
      UNIVERSITY: 0,
      REGIONAL: 0,
      NATIONAL: 1,
      INTERNATIONAL: 0,
    });
    expect(yearRows([{ year: 2026, total: 0, byLevel: {} }], false)).toHaveLength(1);
  });

  it('ac2_4_summarises_the_last_year_the_year_before_and_the_earlier_average', () => {
    expect(yearSummary(yearRows(sampleAnalytics().byYear, false))).toEqual({
      year: 2026,
      count: 2,
      previousYear: 2025,
      previousCount: 0,
      average: 0.5,
      averageFrom: 2024,
    });
    expect(yearSummary([])).toBeNull();
    expect(text('trend-card-now')).toBe('2');
    expect(text('trend-card-previous')).toBe('0');
    expect(text('trend-card-average')).toBe('0,5');
  });

  it('ac2_4_shows_a_dash_for_the_average_without_earlier_years', () => {
    fixture.componentRef.setInput('byYear', [{ year: 2026, total: 1, byLevel: { FACULTY: 1 } }]);
    fixture.detectChanges();

    expect(text('trend-card-average')).toBe('—');
    expect(text('trend-card-previous')).toBe('0');
  });

  it('ac2_5_draws_one_bar_per_year_newest_on_top_and_marks_it', () => {
    const rows = [
      ...fixture.nativeElement.querySelectorAll(
        '[data-testid="trend-bars"] [data-testid="bar-row"]',
      ),
    ] as HTMLElement[];

    const read = (row: HTMLElement, part: string) =>
      row.querySelector(`.bars__${part}`)!.textContent!.trim();
    expect(rows.map((row) => `${read(row, 'label')} ${read(row, 'count')}`)).toEqual([
      '2026 2',
      '2025 0',
      '2024 1',
    ]);
    expect(rows[0].classList).toContain('bars__row--current');
    expect(fixture.nativeElement.querySelector('[data-testid="trend-table"]')).toBeNull();
  });

  it('ac2_6_shows_the_same_figures_as_a_table', () => {
    pane.pane.view.set('table');
    fixture.detectChanges();
    const rows = [
      ...fixture.nativeElement.querySelectorAll('[data-testid="trend-table"] tbody tr'),
    ];

    const cells = rows.map((row) =>
      [...(row as HTMLElement).children].map((cell) => cell.textContent?.trim()).join(' '),
    );

    expect(cells).toEqual(['2026 2', '2025 0', '2024 1']);
    expect(fixture.nativeElement.querySelector('[data-testid="trend-cards"]')).toBeNull();
  });
});
