import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { paneStub } from '../../../../shared/layout/pane.testing';
import { languageStub, translations } from '../../analytics.testing';
import { universityAnalytics } from '../../university/university-analytics.testing';
import { UnitComparisonRow } from '../unit-analytics.service';
import { facultyAnalytics } from '../unit-analytics.testing';
import { UnitComparisonComponent } from './unit-comparison.component';

describe('UnitComparisonComponent', () => {
  let fixture: ComponentFixture<UnitComparisonComponent>;
  let pane: ReturnType<typeof paneStub>;

  beforeEach(() => {
    pane = paneStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations('en')],
      providers: [provideRouter([]), pane.provider, languageStub('en').provider],
    });
    fixture = TestBed.createComponent(UnitComparisonComponent);
    fixture.componentRef.setInput('rows', facultyAnalytics().units);
    fixture.componentRef.setInput('period', { from: 2024, to: 2026 });
    fixture.detectChanges();
  });

  function firstCells(table = 'comparison-table'): string[] {
    return [...fixture.nativeElement.querySelectorAll(`[data-testid="${table}"] tbody tr`)].map(
      (row) => (row as HTMLElement).querySelector('th')!.textContent!.trim(),
    );
  }

  function sortBy(index: number, table = 'comparison-table'): void {
    const headers = fixture.nativeElement.querySelectorAll(
      `[data-testid="${table}"] thead th[mat-sort-header]`,
    );
    (headers[index] as HTMLElement).click();
    fixture.detectChanges();
  }

  it('ac2_4_lists_the_faculty_level_and_each_department_with_its_figures', () => {
    const rows = fixture.nativeElement.querySelectorAll(
      '[data-testid="comparison-table"] tbody tr',
    );

    expect(firstCells()[0]).toBe('Faculty level');
    expect(rows[1].querySelectorAll('td')).toHaveLength(3);
    expect(rows[1].textContent).toContain('11');
    expect(rows[1].querySelector('app-delta')!.textContent).toContain('+120 %');
    expect(rows[1].textContent).toContain('55');
    expect(rows[2].textContent).toContain('inactive');
  });

  it('ac3_3_shows_every_figure_in_the_full_table', () => {
    pane.pane.view.set('table');
    fixture.detectChanges();
    const row = fixture.nativeElement.querySelector(
      '[data-testid="comparison-full-row-64"]',
    ) as HTMLElement;

    expect(fixture.nativeElement.querySelector('[data-testid="comparison-table"]')).toBeNull();
    expect(row.querySelectorAll('td')).toHaveLength(6);
    expect(row.textContent).toContain('20');
    expect(row.textContent).toContain('55');
  });

  it('ac2_2_groups_the_columns_under_awards_and_people_with_the_total_last', () => {
    fixture.componentRef.setInput('totals', facultyAnalytics().totals);
    pane.pane.view.set('table');
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const header = (row: number) =>
      [
        ...element.querySelectorAll(
          `[data-testid="comparison-full-table"] thead tr:nth-child(${row}) th`,
        ),
      ].map((cell) => cell.textContent!.trim());
    const total = element.querySelector('[data-testid="comparison-full-total"]') as HTMLElement;
    const totals = facultyAnalytics().totals;

    expect(header(1)).toEqual(['Unit', 'Awards', 'People']);
    expect(header(2)).toEqual([
      'Total',
      'Change',
      'Verified',
      'Awarded',
      'Members',
      'Per 100 staff',
    ]);
    expect(element.querySelector('[data-testid="comparison-group-awards"]')!.classList).toContain(
      'analytics-table__tint',
    );
    expect(
      element.querySelector('[data-testid="comparison-group-people"]')!.classList,
    ).not.toContain('analytics-table__tint');
    expect(total.parentElement!.tagName).toBe('TFOOT');
    expect(total.querySelector('th')!.textContent!.trim()).toBe('Whole faculty');
    expect([...total.querySelectorAll('td')].map((cell) => cell.textContent!.trim())[0]).toBe(
      String(totals.approved.current),
    );
    expect(total.textContent).toContain(String(totals.members));
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
        5,
        [
          'Faculty level',
          'Department of Mathematical Analysis',
          'Department of Algebra and Informatics',
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
        6,
        [
          'Department of Mathematical Analysis',
          'Faculty level',
          'Department of Algebra and Informatics',
        ],
      ],
      [
        3,
        [
          'Faculty level',
          'Department of Mathematical Analysis',
          'Department of Algebra and Informatics',
        ],
      ],
    ];
    pane.pane.view.set('table');
    fixture.detectChanges();
    const full = 'comparison-full-table';
    for (const [column, order] of expectations) {
      sortBy(column, full);
      expect(firstCells(full).map((cell) => cell.replace(/\s*\(inactive\)/, ''))).toEqual(order);
    }

    sortBy(3, full);

    expect(firstCells(full)[0]).toBe('Department of Algebra and Informatics');
  });

  it('ac2_4_opens_a_department_with_the_same_period', () => {
    const link = fixture.nativeElement.querySelector(
      '[data-testid="comparison-row-64"] a',
    ) as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/analytics/units/64?from=2024&to=2026');
    expect(fixture.nativeElement.querySelector('[data-testid="comparison-row-9"] a')).toBeNull();
  });

  it('ac3_3_draws_approved_and_per_100_members_as_bars_against_the_largest_department', () => {
    const tracks = (id: number) =>
      [
        ...fixture.nativeElement.querySelectorAll(
          `[data-testid="comparison-row-${id}"] .comparison__track i`,
        ),
      ].map((bar) => (bar as HTMLElement).style.width);

    expect(tracks(9)).toEqual([]);
    expect(tracks(64)[1]).toBe('100%');
    expect(parseFloat(tracks(66)[1])).toBeCloseTo((18.75 * 100) / 55, 1);
  });

  it('ac2_4_university_links_faculties_names_the_university_row_and_leaves_colleges_plain', () => {
    fixture.componentRef.setInput('rows', universityAnalytics().units);
    fixture.componentRef.setInput('labels', {
      caption: 'universityAnalytics.sections.comparison',
      levelRow: 'universityAnalytics.comparison.universityLevel',
      chartDescription: 'universityAnalytics.comparison.chartDescription',
    });
    fixture.componentRef.setInput(
      'linkable',
      (row: UnitComparisonRow) => !row.facultyLevel && row.unit.type === 'FACULTY',
    );
    fixture.detectChanges();
    const row = (id: number) =>
      fixture.nativeElement.querySelector(`[data-testid="comparison-row-${id}"]`) as HTMLElement;

    expect(row(9).querySelector('a')!.getAttribute('href')).toBe(
      '/analytics/units/9?from=2024&to=2026',
    );
    expect(row(30).querySelector('a')).toBeNull();
    expect(row(30).textContent).toContain('Vyzhnytsia College of Applied Arts');
    expect(row(1).textContent).toContain('University level');
    expect(
      fixture.nativeElement.querySelector('[data-testid="comparison-table"] caption').textContent,
    ).toContain('Faculty and college comparison');
  });
});
