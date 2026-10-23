import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { chartStub, chartsDrawn } from '../../../shared/charts/chart.testing';
import { languageStub, translations } from '../analytics.testing';
import {
  ComparisonBarsComponent,
  ComparisonRow,
} from './comparison-bars/comparison-bars.component';
import { SourceChartComponent } from './source-chart/source-chart.component';
import { departmentAnalytics, facultyAnalytics } from './unit-analytics.testing';
import { UnitTrendChartComponent } from './unit-trend-chart/unit-trend-chart.component';

interface DrawnOption {
  yAxis: { data: string[] };
  xAxis: { data: string[] };
  series: { type: string; name: string; data: number[] }[];
  aria: { enabled: boolean; label: { description: string } };
}

describe('Unit analytics charts', () => {
  afterEach(() => vi.unstubAllGlobals());

  function setUp(language: 'en' | 'uk' = 'en') {
    const stub = chartStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations(language)],
      providers: [stub.provider, languageStub(language).provider],
    });
    return stub;
  }

  function rows(count: number): ComparisonRow[] {
    return Array.from({ length: count }, (_, index) => ({
      key: String(index),
      label: `Category ${index + 1}`,
      current: count - index,
      previous: 1,
    }));
  }

  it('ac2_3_draws_this_period_beside_the_previous_one_with_a_table', async () => {
    const stub = setUp();
    const fixture = TestBed.createComponent(ComparisonBarsComponent);
    fixture.componentRef.setInput('rows', rows(2));
    fixture.componentRef.setInput('caption', 'By category');
    fixture.componentRef.setInput('labelHeader', 'Category');
    fixture.componentRef.setInput('tableId', 'unit-category-table');
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as unknown as DrawnOption;

    expect(option.series.map((series) => series.name)).toEqual(['Previous period', 'This period']);
    expect(option.series[1].data).toEqual([1, 2]);
    expect(option.aria.label.description).toContain('«Category 1» with 2 (previously 1)');

    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="unit-category-table"]').textContent,
    ).toContain('Category 2');
  });

  it('ac2_7_rolls_categories_beyond_twelve_into_other_in_the_chart_only', async () => {
    const stub = setUp();
    const fixture = TestBed.createComponent(ComparisonBarsComponent);
    fixture.componentRef.setInput('rows', rows(15));
    fixture.componentRef.setInput('caption', 'By category');
    fixture.componentRef.setInput('labelHeader', 'Category');
    fixture.componentRef.setInput('tableId', 'unit-category-table');
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as unknown as DrawnOption;

    expect(option.yAxis.data).toHaveLength(13);
    expect(option.yAxis.data[0]).toBe('Other');
    expect(option.series[1].data[0]).toBe(3 + 2 + 1);
    expect(option.series[0].data[0]).toBe(3);

    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelectorAll('[data-testid="unit-category-table"] tbody tr'),
    ).toHaveLength(15);
  });

  it('ac2_7_adds_the_sources_beyond_twelve_and_the_rest_to_one_other_bar', async () => {
    const stub = setUp();
    const fixture = TestBed.createComponent(SourceChartComponent);
    const sources = Array.from({ length: 15 }, (_, index) => ({
      name: `Source ${index + 1}`,
      count: 20 - index,
    }));
    fixture.componentRef.setInput('bySource', { sources, otherCount: 4 });
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as unknown as DrawnOption;

    expect(option.yAxis.data).toHaveLength(13);
    expect(option.yAxis.data[0]).toBe('Other');
    expect(option.series[0].data[0]).toBe(8 + 7 + 6 + 4);

    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();
    const table = fixture.nativeElement.querySelector('[data-testid="source-table"]');

    expect(table.querySelectorAll('tbody tr')).toHaveLength(16);
    expect(table.textContent).toContain('All other organisations');
  });

  it('ac2_3_draws_one_line_per_department_for_a_faculty', async () => {
    const stub = setUp('uk');
    const fixture = TestBed.createComponent(UnitTrendChartComponent);
    fixture.componentRef.setInput('analytics', facultyAnalytics());
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as unknown as DrawnOption;

    expect(option.xAxis.data).toEqual(['2024', '2025', '2026']);
    expect(option.series.map((series) => series.type)).toEqual(['line', 'line', 'line']);
    expect(option.series.map((series) => series.name)).toEqual([
      'Рівень факультету',
      'Кафедра алгебри та інформатики',
      'Кафедра математичного аналізу',
    ]);
    expect(option.series[1].data).toEqual([3, 3, 5]);
  });

  it('ac2_3_draws_personal_and_unit_awards_as_bars_for_a_department', async () => {
    const stub = setUp();
    const fixture = TestBed.createComponent(UnitTrendChartComponent);
    fixture.componentRef.setInput('analytics', departmentAnalytics());
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as unknown as DrawnOption;

    expect(option.series.map((series) => [series.type, series.name])).toEqual([
      ['bar', 'Personal awards'],
      ['bar', 'Unit awards'],
    ]);
    expect(option.series[0].data).toEqual([3, 4, 5]);
    expect(option.aria.enabled).toBe(true);
  });
});
