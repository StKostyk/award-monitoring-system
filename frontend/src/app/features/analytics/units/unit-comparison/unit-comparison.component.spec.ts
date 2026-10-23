import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { chartStub, chartsDrawn, ChartStub } from '../../../../shared/charts/chart.testing';
import { languageStub, translations } from '../../analytics.testing';
import { facultyAnalytics } from '../unit-analytics.testing';
import { UnitComparisonComponent } from './unit-comparison.component';

describe('UnitComparisonComponent', () => {
  let fixture: ComponentFixture<UnitComparisonComponent>;
  let stub: ChartStub;

  beforeEach(() => {
    stub = chartStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations('en')],
      providers: [provideRouter([]), stub.provider, languageStub('en').provider],
    });
    fixture = TestBed.createComponent(UnitComparisonComponent);
    fixture.componentRef.setInput('rows', facultyAnalytics().units);
    fixture.componentRef.setInput('period', { from: 2024, to: 2026 });
    fixture.detectChanges();
  });

  afterEach(() => vi.unstubAllGlobals());

  function firstCells(): string[] {
    return [
      ...fixture.nativeElement.querySelectorAll('[data-testid="comparison-table"] tbody tr'),
    ].map((row) => (row as HTMLElement).querySelector('th')!.textContent!.trim());
  }

  function sortBy(index: number): void {
    const headers = fixture.nativeElement.querySelectorAll(
      '[data-testid="comparison-table"] thead th',
    );
    (headers[index] as HTMLElement).click();
    fixture.detectChanges();
  }

  it('ac2_4_lists_the_faculty_level_and_each_department_with_its_figures', () => {
    const rows = fixture.nativeElement.querySelectorAll(
      '[data-testid="comparison-table"] tbody tr',
    );

    expect(firstCells()[0]).toBe('Faculty level');
    expect(rows[1].textContent).toContain('20');
    expect(rows[1].textContent).toContain('+6 (+120 %)');
    expect(rows[1].textContent).toContain('55');
    expect(rows[2].textContent).toContain('inactive');
  });

  it('ac2_4_sorts_by_every_column', () => {
    const expectations: [number, string[]][] = [
      [
        0,
        [
          'Department of Algebra and Informatics',
          'Department of Mathematical Analysis',
          'Faculty level',
        ],
      ],
      [
        1,
        [
          'Faculty level',
          'Department of Mathematical Analysis',
          'Department of Algebra and Informatics',
        ],
      ],
      [
        2,
        [
          'Faculty level',
          'Department of Mathematical Analysis',
          'Department of Algebra and Informatics',
        ],
      ],
      [
        3,
        [
          'Department of Mathematical Analysis',
          'Faculty level',
          'Department of Algebra and Informatics',
        ],
      ],
      [
        4,
        [
          'Faculty level',
          'Department of Mathematical Analysis',
          'Department of Algebra and Informatics',
        ],
      ],
      [
        5,
        [
          'Department of Mathematical Analysis',
          'Faculty level',
          'Department of Algebra and Informatics',
        ],
      ],
      [
        6,
        [
          'Faculty level',
          'Department of Mathematical Analysis',
          'Department of Algebra and Informatics',
        ],
      ],
    ];
    for (const [column, order] of expectations) {
      sortBy(column);
      expect(firstCells().map((cell) => cell.replace(/\s*\(inactive\)/, ''))).toEqual(order);
    }

    sortBy(6);

    expect(firstCells()[0]).toBe('Department of Algebra and Informatics');
  });

  it('ac2_4_opens_a_department_with_the_same_period', () => {
    const link = fixture.nativeElement.querySelector(
      '[data-testid="comparison-row-64"] a',
    ) as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/analytics/units/64?from=2024&to=2026');
    expect(fixture.nativeElement.querySelector('[data-testid="comparison-row-9"] a')).toBeNull();
  });

  it('ac2_4_draws_approved_per_100_members_per_department', async () => {
    await chartsDrawn();
    const option = stub.options.at(-1) as {
      yAxis: { data: string[] };
      series: { data: number[] }[];
    };

    expect(option.yAxis.data).toEqual([
      'Department of Mathematical Analysis',
      'Department of Algebra and Informatics',
    ]);
    expect(option.series[0].data).toEqual([18.8, 55]);
  });
});
