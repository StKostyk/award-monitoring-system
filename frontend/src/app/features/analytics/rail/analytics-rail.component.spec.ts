import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { TokenPermissions, readPermissions } from '../../../core/auth/permissions';
import { languageStub, tokenWith, translations } from '../analytics.testing';
import { AnalysableUnit } from '../units/unit-analytics.service';
import { ALGEBRA, ANALYSIS, FACULTY } from '../units/unit-analytics.testing';
import { AnalyticsRailComponent } from './analytics-rail.component';

describe('AnalyticsRailComponent', () => {
  let fixture: ComponentFixture<AnalyticsRailComponent>;
  const PHYSICS: AnalysableUnit = {
    ...FACULTY,
    id: 12,
    name: 'Faculty of Physics',
    nameUk: 'Фізичний факультет',
  };
  const OPTICS: AnalysableUnit = {
    ...ALGEBRA,
    id: 70,
    name: 'Department of Optics',
    nameUk: 'Кафедра оптики',
    parentId: PHYSICS.id,
  };
  const RECTOR = ['analytics:read:department', 'analytics:read:university'];
  const DEAN = ['analytics:read:department'];

  function render(
    units: AnalysableUnit[],
    selected: number | null,
    options: {
      permissions?: string[];
      counts?: ReadonlyMap<number, number> | null;
      universityPage?: boolean;
    } = {},
  ): void {
    const permissions = signal<TokenPermissions>(
      readPermissions(tokenWith({ permissions: options.permissions ?? RECTOR })),
    );
    TestBed.configureTestingModule({
      imports: [AnalyticsRailComponent, translations('uk')],
      providers: [
        provideRouter([]),
        languageStub('uk').provider,
        { provide: AuthService, useValue: { permissions } },
      ],
    });
    fixture = TestBed.createComponent(AnalyticsRailComponent);
    fixture.componentRef.setInput('units', units);
    fixture.componentRef.setInput('selected', selected);
    fixture.componentRef.setInput('counts', options.counts ?? null);
    fixture.componentRef.setInput('universityPage', options.universityPage ?? false);
    fixture.detectChanges();
  }

  function rows(): HTMLAnchorElement[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="unit-option-"]')];
  }

  function ids(): (string | undefined)[] {
    return rows().map((row) => row.dataset['testid']);
  }

  it('ac12_1_offers_the_whole_university_and_every_faculty_to_a_rector_on_the_university_page', () => {
    render([FACULTY, ALGEBRA, ANALYSIS, PHYSICS, OPTICS], null, { universityPage: true });

    expect(ids()).toEqual(['unit-option-university', 'unit-option-9', 'unit-option-12']);
    expect(rows()[0].textContent).toContain('Увесь університет');
    expect(rows()[0].getAttribute('href')).toBe('/analytics/university');
    expect(rows()[0].getAttribute('aria-current')).toBe('true');
    expect(rows()[1].textContent).toContain('Факультет математики та інформатики');
  });

  it('ac12_1_opens_the_departments_of_the_shown_faculty_only_and_indents_them', () => {
    render([FACULTY, ALGEBRA, ANALYSIS, PHYSICS, OPTICS], ALGEBRA.id);

    expect(ids()).toEqual([
      'unit-option-university',
      'unit-option-9',
      'unit-option-64',
      'unit-option-66',
      'unit-option-12',
    ]);
    expect(rows()[2].getAttribute('aria-current')).toBe('true');
    expect(rows()[1].getAttribute('aria-current')).toBeNull();
    expect(rows()[2].classList).toContain('rail__child');
    expect(rows()[1].classList).not.toContain('rail__child');
  });

  it('ac12_2_offers_a_dean_only_the_faculty_the_api_returns_without_the_university', () => {
    render([FACULTY, ALGEBRA, ANALYSIS], FACULTY.id, { permissions: DEAN });

    expect(ids()).toEqual(['unit-option-9', 'unit-option-64', 'unit-option-66']);
    expect(fixture.nativeElement.textContent).not.toContain('Увесь університет');
  });

  it('ac12_2_lists_the_departments_when_their_faculty_is_not_open_to_the_caller', () => {
    render([ALGEBRA, ANALYSIS], ALGEBRA.id, { permissions: DEAN });

    expect(ids()).toEqual(['unit-option-64', 'unit-option-66']);
    expect(rows()[0].classList).not.toContain('rail__child');
  });

  it('ac12_1_treats_a_faculty_under_the_university_unit_as_a_top_row', () => {
    const university = 1;
    render([{ ...FACULTY, parentId: university }, ALGEBRA], ALGEBRA.id, { permissions: DEAN });

    expect(ids()).toEqual(['unit-option-9', 'unit-option-64']);
  });

  it('ac12_3_shows_counts_for_the_open_faculty_only', () => {
    render([FACULTY, ALGEBRA, ANALYSIS, PHYSICS], FACULTY.id, {
      counts: new Map([
        [FACULTY.id, 15],
        [ALGEBRA.id, 11],
        [ANALYSIS.id, 3],
        [PHYSICS.id, 8],
      ]),
    });

    const counts = rows().map(
      (row) => row.querySelector('.rail__count')?.textContent?.trim() ?? '',
    );
    expect(counts[1]).toContain('15');
    expect(counts[2]).toContain('11');
    expect(counts[3]).toContain('3');
    expect(counts[4]).toBe('');
    expect(counts[0]).toBe('');
  });

  it('ac12_1_marks_no_row_while_a_unit_page_has_no_unit_yet', () => {
    render([FACULTY, ALGEBRA], null);

    expect(rows().filter((row) => row.getAttribute('aria-current') === 'true')).toEqual([]);
  });

  it('ac12_3_shows_no_count_while_the_counts_are_unknown', () => {
    render([FACULTY, ALGEBRA], ALGEBRA.id);

    expect(fixture.nativeElement.querySelector('.rail__count')).toBeNull();
  });

  it('ac12_4_opens_another_unit_in_place_and_keeps_the_period', () => {
    render([FACULTY, ALGEBRA, ANALYSIS], ALGEBRA.id);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    rows()[3].click();

    expect(rows()[3].getAttribute('href')).toBe('/analytics/units/66');
    expect(navigate).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ state: { stayInPlace: true } }),
    );
  });

  it('ac12_4_opens_the_university_page_as_a_page_change', () => {
    render([FACULTY, ALGEBRA], FACULTY.id);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    rows()[0].click();

    expect(navigate).toHaveBeenCalledWith(
      expect.anything(),
      expect.not.objectContaining({ state: { stayInPlace: true } }),
    );
  });
});
