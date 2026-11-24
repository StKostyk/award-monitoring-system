import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AnalyticsPeriod } from '../../analytics.service';
import { languageStub, translations } from '../../analytics.testing';
import { universityAnalytics } from '../university-analytics.testing';
import { UnitComparisonRow } from '../../units/unit-analytics.service';
import { FacultyBarsComponent } from './faculty-bars.component';

describe('FacultyBarsComponent', () => {
  let fixture: ComponentFixture<FacultyBarsComponent>;

  function render(
    rows: UnitComparisonRow[],
    period: AnalyticsPeriod = { from: 2021, to: 2025 },
  ): void {
    TestBed.configureTestingModule({
      imports: [FacultyBarsComponent, translations('uk')],
      providers: [provideRouter([]), languageStub('uk').provider],
    });
    fixture = TestBed.createComponent(FacultyBarsComponent);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('period', period);
    fixture.componentRef.setInput('shown', { from: 2021, to: 2025 });
    fixture.componentRef.setInput('previous', { from: 2016, to: 2020 });
    fixture.detectChanges();
  }

  function bars(): HTMLElement[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="faculty-bar-"]')];
  }

  function read(testId: string): string[] {
    return bars().map((bar) => bar.querySelector(`[data-testid="${testId}"]`)!.textContent!.trim());
  }

  function withApproved(current: number, previous: number): UnitComparisonRow[] {
    return universityAnalytics().units.map((row) =>
      row.unit.id === 9 ? { ...row, approved: { ...row.approved, current, previous } } : row,
    );
  }

  it('ac6_3_lists_the_faculties_and_colleges_by_approved_awards_without_the_university_level', () => {
    render(universityAnalytics().units);

    expect(bars().map((bar) => bar.dataset['testid'])).toEqual(['faculty-bar-9', 'faculty-bar-30']);
    expect(bars()[0].textContent).toContain('Факультет математики та інформатики');
    expect(read('faculty-count')).toEqual(['80', '5']);
  });

  it('ac4_2_sizes_bars_and_ticks_against_the_largest_count_of_either_period', () => {
    render(universityAnalytics().units);

    const widths = bars().map(
      (bar) => (bar.querySelector('.bars__fill') as HTMLElement).style.width,
    );
    const ticks = bars().map(
      (bar) => (bar.querySelector('[data-testid="faculty-tick"]') as HTMLElement).style.left,
    );
    expect(widths).toEqual(['100%', '6.25%']);
    expect(ticks).toEqual(['62.5%', '12.5%']);
    expect(read('faculty-change')).toEqual(['+30', '−5']);
    expect(
      fixture.nativeElement.querySelector('[data-testid="faculty-key"]').textContent.trim(),
    ).toBe('2021–2025 2016–2020');
  });

  it('ac4_2_marks_no_change_and_a_new_faculty', () => {
    render(withApproved(10, 10));
    expect(read('faculty-change')[0]).toBe('0');

    TestBed.resetTestingModule();
    render(withApproved(10, 0));
    expect(read('faculty-change')).toEqual(['нове', '−5']);
    expect(bars()[1].querySelector('[data-testid="faculty-tick"]')).not.toBeNull();
    expect(bars()[0].querySelector('[data-testid="faculty-tick"]')).toBeNull();
  });

  it('ac4_2_draws_no_ticks_or_key_without_a_previous_period', () => {
    render(
      universityAnalytics().units.map((row) => ({
        ...row,
        approved: { ...row.approved, previous: 0 },
      })),
    );

    expect(fixture.nativeElement.querySelector('[data-testid="faculty-tick"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="faculty-key"]')).toBeNull();
  });

  it('ac6_3_links_a_faculty_to_its_page_for_the_period_and_not_a_college', () => {
    render(universityAnalytics().units);

    expect(bars()[0].querySelector('a')!.getAttribute('href')).toBe(
      '/analytics/units/9?from=2021&to=2025',
    );
    expect(bars()[1].querySelector('a')).toBeNull();
  });

  it('ac6_3_says_so_when_no_faculty_has_approved_awards', () => {
    render([]);

    expect(bars()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('жоден факультет');
  });
});
