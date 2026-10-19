import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { ChartStub, chartStub, chartsDrawn } from '../../../shared/charts/chart.testing';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { YearTrendChartComponent, yearRows } from './year-trend-chart.component';

describe('YearTrendChartComponent', () => {
  let fixture: ComponentFixture<YearTrendChartComponent>;
  let stub: ChartStub;

  beforeEach(async () => {
    stub = chartStub();
    TestBed.configureTestingModule({
      imports: [YearTrendChartComponent, NoopAnimationsModule, translations('uk')],
      providers: [stub.provider, languageStub('uk').provider],
    });
    fixture = TestBed.createComponent(YearTrendChartComponent);
    fixture.componentRef.setInput('byYear', sampleAnalytics().byYear);
    fixture.detectChanges();
    await chartsDrawn();
  });

  afterEach(() => vi.unstubAllGlobals());

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

  it('ac2_5_stacks_the_bars_by_the_bands_present_with_translated_names', () => {
    const option = stub.options.at(-1) as {
      xAxis: { data: string[] };
      series: { name: string; stack: string; data: number[] }[];
    };

    expect(option.xAxis.data).toEqual(['2024', '2025', '2026']);
    expect(option.series.map((series) => series.name)).toEqual([
      'Рівні підрозділів',
      'Національний',
    ]);
    expect(option.series.every((series) => series.stack === 'levels')).toBe(true);
    expect(option.series[0].data).toEqual([1, 0, 1]);
  });

  it('ac2_6_shows_the_same_figures_as_a_table', () => {
    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();
    const rows = [
      ...fixture.nativeElement.querySelectorAll('[data-testid="trend-table"] tbody tr'),
    ];

    const cells = rows.map((row) =>
      [...(row as HTMLElement).children].map((cell) => cell.textContent?.trim()).join(' '),
    );

    expect(cells).toEqual(['2024 1 0 1', '2025 0 0 0', '2026 1 1 2']);
  });
});
