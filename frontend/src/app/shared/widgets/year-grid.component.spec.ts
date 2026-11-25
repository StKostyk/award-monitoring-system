import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { languageStub, translations } from '../../features/analytics/analytics.testing';
import { fitCount } from './table-scroll.component';
import { YearGridComponent, YearGridRow, yearGrid } from './year-grid.component';

const YEARS = [2024, 2025, 2026];
const ROWS: YearGridRow[] = [
  { key: 'algebra', label: 'Кафедра алгебри', counts: [11, 14, 0] },
  { key: 'analysis', label: 'Кафедра аналізу', counts: [4, 5, 3] },
];

function render(phone = false, running = true): HTMLElement {
  TestBed.configureTestingModule({
    imports: [YearGridComponent, NoopAnimationsModule, translations('uk')],
    providers: [
      languageStub('uk').provider,
      {
        provide: BreakpointObserver,
        useValue: { observe: () => of({ matches: phone, breakpoints: {} } as BreakpointState) },
      },
    ],
  });
  const fixture = TestBed.createComponent(YearGridComponent);
  fixture.componentRef.setInput('caption', 'Нагороди за роками');
  fixture.componentRef.setInput('rowHeader', 'Підрозділ');
  fixture.componentRef.setInput('years', YEARS);
  fixture.componentRef.setInput('rows', ROWS);
  fixture.componentRef.setInput('running', running);
  fixture.componentRef.setInput('testId', 'grid');
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function cells(row: Element): string[] {
  return [...row.children].map((cell) => cell.textContent!.trim());
}

describe('YearGridComponent', () => {
  it('ac2_1_totals_every_row_and_year_with_a_corner_total', () => {
    const element = render();
    const table = element.querySelector('[data-testid="grid"]')!;

    expect(table.classList).toContain('analytics-table--grid');
    expect(cells(table.querySelector('thead tr')!)).toEqual([
      'Підрозділ',
      '2024',
      '2025',
      '2026*',
      'Разом',
    ]);
    expect(cells(element.querySelector('[data-testid="year-grid-row-algebra"]')!)).toEqual([
      'Кафедра алгебри',
      '11',
      '14',
      '0',
      '25',
    ]);
    expect(cells(element.querySelector('[data-testid="year-grid-totals"]')!)).toEqual([
      'Разом',
      '15',
      '19',
      '3',
      '37',
    ]);
  });

  it('ac2_1_marks_the_running_year_with_a_footnote', () => {
    const element = render();

    expect(element.querySelectorAll('thead .is-current')).toHaveLength(1);
    expect(element.querySelectorAll('tbody td.is-current')).toHaveLength(2);
    expect(element.querySelector('[data-testid="year-grid-running"]')!.textContent).toContain(
      '* рік триває',
    );
  });

  it('ac2_1_has_no_asterisk_when_the_period_is_complete', () => {
    const element = render(false, false);

    expect(element.querySelector('[data-testid="year-grid-running"]')).toBeNull();
    expect(element.querySelector('thead')!.textContent).not.toContain('*');
  });

  it('ac2_3_puts_the_newest_year_first_on_a_phone_with_a_named_focusable_scroll_area', () => {
    const element = render(true);
    const area = element.querySelector('[data-testid="table-scroll"]')!;

    expect(cells(element.querySelector('thead tr')!).slice(1, 4)).toEqual([
      '2026*',
      '2025',
      '2024',
    ]);
    expect(cells(element.querySelector('[data-testid="year-grid-row-analysis"]')!)).toEqual([
      'Кафедра аналізу',
      '3',
      '5',
      '4',
      '12',
    ]);
    expect(area.getAttribute('tabindex')).toBe('0');
    expect(area.getAttribute('role')).toBe('region');
    expect(area.getAttribute('aria-label')).toBe('Нагороди за роками: таблиця, гортається вбік');
  });

  it('ac2_3_counts_the_year_columns_that_fit', () => {
    const columns = [
      { left: 130, right: 190 },
      { left: 190, right: 250 },
      { left: 250, right: 310 },
      { left: 310, right: 370 },
    ];

    expect(fitCount(columns, 130, 320)).toEqual({ shown: 3, total: 4 });
    expect(fitCount(columns, 130, 400)).toEqual({ shown: 4, total: 4 });
  });

  it('edge_shows_zero_and_totals_a_single_year', () => {
    const view = yearGrid([2026], [{ key: 'a', label: 'A', counts: [0] }], false, false);

    expect(view.rows[0].cells[0].count).toBe(0);
    expect(view.rows[0].total).toBe(0);
    expect(view.totals).toEqual([0]);
    expect(view.total).toBe(0);
  });
});
