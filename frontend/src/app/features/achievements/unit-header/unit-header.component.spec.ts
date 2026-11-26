import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import {
  OrganizationsService,
  OrganizationSummary,
} from '../../../core/organizations/organizations.service';
import { AchievementScope } from '../achievements.service';
import { UnitHeaderComponent } from './unit-header.component';

const faculty: OrganizationSummary = {
  id: 9,
  name: 'Faculty of Mathematics',
  nameUk: 'Факультет математики',
  code: null,
  type: 'FACULTY',
  parent: null,
};

function department(id: number, name: string, nameUk: string): OrganizationSummary {
  return {
    id,
    name,
    nameUk,
    code: null,
    type: 'DEPARTMENT',
    parent: { id: 9, name: faculty.name, nameUk: faculty.nameUk, code: null, type: 'FACULTY' },
  };
}

const departments = [
  department(65, 'Geometry', 'Кафедра геометрії'),
  department(64, 'Algebra', 'Кафедра алгебри'),
];

describe('UnitHeaderComponent', () => {
  const organizations = { ofType: vi.fn() };

  async function render(
    unitId: number,
    scope: AchievementScope = 'signed-in',
    fallback: string | null = null,
  ): Promise<ComponentFixture<UnitHeaderComponent>> {
    await TestBed.configureTestingModule({
      imports: [
        UnitHeaderComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: { achievements: { title: 'Досягнення' } }, en: {} },
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'uk' },
        }),
      ],
      providers: [provideRouter([]), { provide: OrganizationsService, useValue: organizations }],
    }).compileComponents();
    const fixture = TestBed.createComponent(UnitHeaderComponent);
    fixture.componentRef.setInput('unitId', unitId);
    fixture.componentRef.setInput('scope', scope);
    fixture.componentRef.setInput('fallback', fallback);
    fixture.detectChanges();
    return fixture;
  }

  const text = (fixture: ComponentFixture<unknown>, id: string): string[] =>
    Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll(`[data-testid="${id}"]`),
      (element) => element.textContent?.trim() ?? '',
    );
  const links = (fixture: ComponentFixture<unknown>, id: string): (string | null)[] =>
    Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll(`[data-testid="${id}"]`),
      (element) => element.getAttribute('href'),
    );

  beforeEach(() => {
    organizations.ofType
      .mockReset()
      .mockImplementation((type: string) => of(type === 'FACULTY' ? [faculty] : departments));
  });

  it('ac2_4_a_department_shows_its_name_and_its_faculty_as_a_link', async () => {
    const fixture = await render(64);

    expect(text(fixture, 'unit-name')).toEqual(['Кафедра алгебри']);
    expect(text(fixture, 'unit-faculty')).toEqual(['Факультет математики']);
    expect(links(fixture, 'unit-faculty')).toEqual(['/units/9/achievements']);
    expect(text(fixture, 'unit-department')).toEqual([]);
  });

  it('ac2_4_a_faculty_lists_its_departments_as_links_by_name', async () => {
    const fixture = await render(9);

    expect(text(fixture, 'unit-name')).toEqual(['Факультет математики']);
    expect(text(fixture, 'unit-faculty')).toEqual([]);
    expect(text(fixture, 'unit-department')).toEqual(['Кафедра алгебри', 'Кафедра геометрії']);
    expect(links(fixture, 'unit-department')).toEqual([
      '/units/64/achievements',
      '/units/65/achievements',
    ]);
  });

  it('ac2_5_links_of_the_public_page_stay_on_the_public_pages', async () => {
    const fixture = await render(64, 'public');

    expect(links(fixture, 'unit-faculty')).toEqual(['/public/units/9/achievements']);
  });

  it('ac2_8_names_the_unit_in_the_interface_language', async () => {
    const fixture = await render(64);

    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();

    expect(text(fixture, 'unit-name')).toEqual(['Algebra']);
    expect(text(fixture, 'unit-faculty')).toEqual(['Faculty of Mathematics']);
  });

  it('edge_an_inactive_unit_falls_back_to_the_name_the_awards_carry', async () => {
    const fixture = await render(70, 'signed-in', 'Кафедра історії математики');

    expect(text(fixture, 'unit-name')).toEqual(['Кафедра історії математики']);
  });

  it('edge_without_the_unit_list_the_page_keeps_a_heading', async () => {
    organizations.ofType.mockReturnValue(throwError(() => new Error('offline')));
    const fixture = await render(64);

    expect(text(fixture, 'unit-name')).toEqual(['Досягнення']);
  });
});
