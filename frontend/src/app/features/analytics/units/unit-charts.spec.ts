import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { chartStub, chartsDrawn } from '../../../shared/charts/chart.testing';
import { paneStub } from '../../../shared/layout/pane.testing';
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

  let pane: ReturnType<typeof paneStub>;

  function setUp(language: 'en' | 'uk' = 'en') {
    const stub = chartStub();
    pane = paneStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations(language)],
      providers: [stub.provider, pane.provider, languageStub(language).provider],
    });
    return stub;
  }

  function comparison(count: number) {
    const fixture = TestBed.createComponent(ComparisonBarsComponent);
    fixture.componentRef.setInput('rows', rows(count));
    fixture.componentRef.setInput('caption', 'By category');
    fixture.componentRef.setInput('labelHeader', 'Category');
    fixture.componentRef.setInput('tableId', 'unit-category-table');
    fixture.detectChanges();
    return fixture;
  }

  function texts(root: HTMLElement, selector: string): string[] {
    return [...root.querySelectorAll(selector)].map((node) =>
      node.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  function rows(count: number): ComparisonRow[] {
    return Array.from({ length: count }, (_, index) => ({
      key: String(index),
      label: `Category ${index + 1}`,
      current: count - index,
      previous: 1,
    }));
  }

  it('ac2_3_draws_this_period_beside_the_previous_one_with_a_table', () => {
    setUp();
    const fixture = comparison(2);
    const element: HTMLElement = fixture.nativeElement;
    const pairs = element.querySelectorAll('[data-testid="pair-row"]');

    expect(element.querySelector('[data-testid="pair-bars"]')!.getAttribute('aria-label')).toBe(
      'By category',
    );
    expect(texts(element, '[data-testid="pair-row"] .pairs__label')).toEqual([
      'Category 1',
      'Category 2',
    ]);
    expect(
      texts(element, '[data-testid="pair-row"] .pairs__count:not(.pairs__count--previous)'),
    ).toEqual(['This period: 2', 'This period: 1']);
    expect(
      (pairs[0].querySelector('.pairs__track:not(.pairs__track--previous) i') as HTMLElement).style
        .width,
    ).toBe('100%');
    expect((pairs[0].querySelector('.pairs__track--previous i') as HTMLElement).style.width).toBe(
      '50%',
    );

    pane.pane.view.set('table');
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="pair-bars"]')).toBeNull();
    expect(element.querySelector('[data-testid="unit-category-table"]')!.textContent).toContain(
      'Category 2',
    );
  });

  it('ac2_7_rolls_categories_beyond_twelve_into_other_in_the_chart_only', () => {
    setUp();
    const fixture = comparison(15);
    const element: HTMLElement = fixture.nativeElement;
    const labels = texts(element, '[data-testid="pair-row"] .pairs__label');

    expect(labels).toHaveLength(13);
    expect(labels.at(-1)).toBe('Other');
    expect(texts(element, '[data-testid="pair-row"] .pairs__count').slice(-2)).toEqual([
      'Previous period: 3',
      'This period: 6',
    ]);

    pane.pane.view.set('table');
    fixture.detectChanges();

    expect(element.querySelectorAll('[data-testid="unit-category-table"] tbody tr')).toHaveLength(
      15,
    );
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

  it('ac2_3_ac4_2_draws_one_small_chart_per_department_without_a_subtitle', () => {
    setUp('uk');
    const fixture = TestBed.createComponent(UnitTrendChartComponent);
    fixture.componentRef.setInput('analytics', facultyAnalytics());
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const multiples = element.querySelectorAll('[data-testid="unit-trend-multiple"]');

    expect(texts(element, '.multiple__name')).toEqual([
      'Рівень факультету',
      'Кафедра алгебри та інформатики',
      'Кафедра математичного аналізу',
    ]);
    expect(texts(multiples[1] as HTMLElement, '.columns__count')).toEqual([
      '2024: 3',
      '2025: 3',
      '2026: рік триває, 5',
    ]);
    expect(multiples[1].querySelector('.multiple__total')!.textContent).toContain('11');
    expect(multiples[1].querySelectorAll('.columns__item--current')).toHaveLength(1);
    expect((multiples[0].querySelector('.columns__bar') as HTMLElement).style.height).toBe('0%');
    expect(element.querySelector('.multiples__key')).toBeNull();
    const running = multiples[1].querySelector('.columns__item--running')!;
    expect(running.getAttribute('title')).toBe('2026: рік триває');

    pane.pane.view.set('table');
    fixture.detectChanges();

    expect(element.querySelectorAll('[data-testid="unit-trend-table"] tbody tr')).toHaveLength(3);
    expect(element.querySelector('[data-testid="year-grid-running"]')).toBeNull();
    expect(element.querySelector('thead .is-current')!.getAttribute('title')).toBe('рік триває');
  });

  it('ac2_4_the_faculty_grid_shows_the_numbers_of_the_small_charts', () => {
    setUp();
    const fixture = TestBed.createComponent(UnitTrendChartComponent);
    fixture.componentRef.setInput('analytics', facultyAnalytics());
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const charted = [...element.querySelectorAll('[data-testid="unit-trend-multiple"]')].map(
      (multiple) =>
        [...multiple.querySelectorAll('.columns__count')].map((count) =>
          count.textContent!.replace(/^.*[:,]\s*/s, '').trim(),
        ),
    );

    pane.pane.view.set('table');
    fixture.detectChanges();
    const tabled = [...element.querySelectorAll('[data-testid="unit-trend-table"] tbody tr')].map(
      (row) =>
        [...row.querySelectorAll('td:not(.is-total)')].map((cell) => cell.textContent!.trim()),
    );

    expect(tabled).toEqual(charted);
  });

  it('ac2_3_draws_one_small_chart_of_the_totals_for_a_department', () => {
    setUp();
    const fixture = TestBed.createComponent(UnitTrendChartComponent);
    fixture.componentRef.setInput('analytics', departmentAnalytics());
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelectorAll('[data-testid="unit-trend-multiple"]')).toHaveLength(1);
    expect(texts(element, '.columns__count')).toEqual(['2024: 4', '2025: 5', '2026: 6']);
    expect(element.querySelector('.multiples__key')).toBeNull();

    pane.pane.view.set('table');
    fixture.detectChanges();
    const rows = [
      ...element.querySelectorAll(
        '[data-testid="unit-trend-table"] tbody tr, [data-testid="unit-trend-table"] tfoot tr',
      ),
    ].map((row) => [...row.children].map((cell) => cell.textContent!.trim()).join(' '));

    expect(rows).toEqual(['Personal awards 3 4 5 12', 'Unit awards 1 1 1 3', 'Total 4 5 6 15']);
  });
});
