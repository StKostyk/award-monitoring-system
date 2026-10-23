import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { chartStub, chartsDrawn } from '../../../shared/charts/chart.testing';
import { LevelChartComponent } from '../level-chart/level-chart.component';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { CategoryChartComponent } from './category-chart.component';

describe('CategoryChartComponent and LevelChartComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  function setUp(language: 'en' | 'uk') {
    const stub = chartStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations(language)],
      providers: [stub.provider, languageStub(language).provider],
    });
    return stub;
  }

  it('ac2_5_draws_categories_as_horizontal_bars_highest_on_top', async () => {
    const stub = setUp('uk');
    const fixture = TestBed.createComponent(CategoryChartComponent);
    fixture.componentRef.setInput('byCategory', sampleAnalytics().byCategory);
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as {
      yAxis: { data: string[] };
      series: { data: number[] }[];
      aria: { label: { description: string } };
    };

    expect(option.yAxis.data).toEqual(['Диплом', 'Грант']);
    expect(option.series[0].data).toEqual([1, 2]);
    expect(option.aria.label.description).toContain('«Грант»: 2');

    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="category-table"]').textContent,
    ).toContain('Національний');
  });

  it('ac4_5_draws_the_twelve_largest_categories_and_one_other_bar', async () => {
    const stub = setUp('en');
    const template = sampleAnalytics().byCategory[0];
    const byCategory = Array.from({ length: 15 }, (_, index) => ({
      category: { ...template.category, id: index + 1, name: `Category ${index + 1}` },
      count: 20 - index,
    }));
    const fixture = TestBed.createComponent(CategoryChartComponent);
    fixture.componentRef.setInput('byCategory', byCategory);
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as {
      yAxis: { data: string[] };
      series: { data: number[] }[];
      aria: { label: { description: string } };
    };

    expect(option.yAxis.data).toHaveLength(13);
    expect(option.yAxis.data[0]).toBe('Other');
    expect(option.yAxis.data.at(-1)).toBe('Category 1');
    expect(option.series[0].data[0]).toBe(8 + 7 + 6);
    expect(option.aria.label.description).toContain('15 categories');

    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelectorAll('[data-testid="category-table"] tbody tr'),
    ).toHaveLength(15);
  });

  it('ac2_5_draws_levels_from_the_highest_and_lists_them_in_the_description', async () => {
    const stub = setUp('en');
    const fixture = TestBed.createComponent(LevelChartComponent);
    fixture.componentRef.setInput('byLevel', sampleAnalytics().byLevel);
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as {
      yAxis: { data: string[] };
      aria: { label: { description: string } };
    };

    expect(option.yAxis.data).toEqual(['National', 'Faculty', 'Department']);
    expect(option.aria.label.description).toBe(
      'Bar chart of approved awards by level: Department: 1, Faculty: 1, National: 1.',
    );

    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelectorAll('[data-testid="level-table"] tbody tr'),
    ).toHaveLength(3);
  });
});
