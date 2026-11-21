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
    fixture.detectChanges();
  }

  function bars(): HTMLElement[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="faculty-bar-"]')];
  }

  it('ac6_3_lists_the_faculties_and_colleges_by_approved_awards_without_the_university_level', () => {
    render(universityAnalytics().units);

    expect(bars().map((bar) => bar.dataset['testid'])).toEqual(['faculty-bar-9', 'faculty-bar-30']);
    expect(bars()[0].textContent).toContain('Факультет математики та інформатики');
    expect(bars()[0].textContent).toContain('80');
    expect(bars()[1].textContent).toContain('5');
  });

  it('ac6_3_sizes_each_bar_against_the_largest_count', () => {
    render(universityAnalytics().units);

    const widths = bars().map(
      (bar) => (bar.querySelector('.bars__fill') as HTMLElement).style.width,
    );
    expect(widths).toEqual(['100%', '6.25%']);
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
