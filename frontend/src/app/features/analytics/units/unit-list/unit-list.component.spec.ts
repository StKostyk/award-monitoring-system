import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { languageStub, translations } from '../../analytics.testing';
import { ALGEBRA, ANALYSIS, FACULTY } from '../unit-analytics.testing';
import { AnalysableUnit } from '../unit-analytics.service';
import { UnitListComponent } from './unit-list.component';

describe('UnitListComponent', () => {
  let fixture: ComponentFixture<UnitListComponent>;
  const OTHER_FACULTY: AnalysableUnit = {
    ...FACULTY,
    id: 12,
    name: 'Faculty of Physics',
    nameUk: null,
  };

  function render(
    units: AnalysableUnit[],
    selected: number,
    counts: ReadonlyMap<number, number> | null = null,
  ): void {
    TestBed.configureTestingModule({
      imports: [UnitListComponent, translations('uk')],
      providers: [provideRouter([]), languageStub('uk').provider],
    });
    fixture = TestBed.createComponent(UnitListComponent);
    fixture.componentRef.setInput('units', units);
    fixture.componentRef.setInput('selected', selected);
    fixture.componentRef.setInput('counts', counts);
    fixture.detectChanges();
  }

  function rows(): HTMLAnchorElement[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="unit-option-"]')];
  }

  it('ac6_1_lists_the_whole_faculty_first_then_its_departments_with_their_counts', () => {
    render(
      [OTHER_FACULTY, FACULTY, ALGEBRA, ANALYSIS],
      FACULTY.id,
      new Map([
        [FACULTY.id, 15],
        [ALGEBRA.id, 11],
        [ANALYSIS.id, 3],
      ]),
    );

    expect(rows().map((row) => row.dataset['testid'])).toEqual([
      'unit-option-9',
      'unit-option-64',
      'unit-option-66',
    ]);
    expect(rows()[0].textContent).toContain('Увесь факультет');
    expect(rows()[0].textContent).toContain('15');
    expect(rows()[1].textContent).toContain('11');
    expect(rows()[2].textContent).toContain('3');
    expect(rows()[0].getAttribute('aria-current')).toBe('true');
  });

  it('ac6_1_links_each_row_to_its_page_and_marks_the_shown_department', () => {
    render([FACULTY, ALGEBRA, ANALYSIS], ALGEBRA.id);

    expect(rows().map((row) => row.getAttribute('href'))).toEqual([
      '/analytics/units/9',
      '/analytics/units/64',
      '/analytics/units/66',
    ]);
    expect(rows()[1].getAttribute('aria-current')).toBe('true');
    expect(rows()[0].getAttribute('aria-current')).toBeNull();
  });

  it('ac11_3_opens_another_unit_without_leaving_the_place_on_the_page', () => {
    render([FACULTY, ALGEBRA, ANALYSIS], ALGEBRA.id);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    rows()[2].click();

    expect(navigate).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ state: { stayInPlace: true } }),
    );
  });

  it('ac6_1_shows_no_count_while_the_counts_are_unknown', () => {
    render([FACULTY, ALGEBRA], ALGEBRA.id);

    expect(fixture.nativeElement.querySelector('.picker__count')).toBeNull();
  });

  it('ac6_1_treats_a_faculty_under_the_university_as_the_whole_faculty', () => {
    const university = 1;
    const faculty = { ...FACULTY, parentId: university };
    render([faculty, OTHER_FACULTY, ALGEBRA], FACULTY.id);

    expect(rows().map((row) => row.dataset['testid'])).toEqual(['unit-option-9', 'unit-option-64']);
    expect(rows()[0].textContent).toContain('Увесь факультет');
  });

  it('ac6_1_lists_only_the_departments_when_the_faculty_is_not_open_to_the_caller', () => {
    render([ALGEBRA, ANALYSIS], ALGEBRA.id);

    expect(rows().map((row) => row.dataset['testid'])).toEqual([
      'unit-option-64',
      'unit-option-66',
    ]);
    expect(fixture.nativeElement.textContent).not.toContain('Увесь факультет');
  });
});
