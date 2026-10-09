import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockInstance, vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationsService } from '../../../core/organizations/organizations.service';
import { Page } from '../../awards/awards.service';
import { Achievement, AchievementsService, NO_ACHIEVEMENT_FILTERS } from '../achievements.service';
import { AchievementListComponent } from './achievement-list.component';

const achievement: Achievement = {
  awardId: 7,
  title: 'Ministry letter',
  titleUk: 'Грамота МОН',
  description: null,
  descriptionUk: null,
  category: { id: 13, name: 'Ministry Recognition', nameUk: null, level: 'NATIONAL' },
  awardingOrganization: 'МОН України',
  awardDate: '2025-05-01',
  externalUrl: null,
  verified: false,
  recipient: {
    type: 'PERSON',
    personName: 'Анастасія Коваль',
    unit: { id: 64, name: 'Algebra', nameUk: null, type: 'DEPARTMENT' },
  },
};

function page(content: Achievement[]): Page<Achievement> {
  return { content, totalElements: content.length, totalPages: 1, size: 20, number: 0 };
}

const translations = {
  uk: {
    achievements: {
      empty: 'Поки що немає досягнень за цими умовами.',
      problems: { failed: 'Не вдалося завантажити досягнення.', gone: 'Такого підрозділу немає.' },
      title: 'Досягнення',
      publicTitle: 'Досягнення університету',
      publicPage: 'Публічна сторінка',
      viewAsStaff: 'Переглянути як співробітник',
    },
  },
};

describe('AchievementListComponent', () => {
  const service = { list: vi.fn(), units: vi.fn() };
  const signedIn = signal(true);
  let params: BehaviorSubject<ParamMap>;
  let unitId: BehaviorSubject<ParamMap>;
  let navigate: MockInstance<Router['navigate']>;

  async function open(
    scope: 'signed-in' | 'public' = 'signed-in',
  ): Promise<ComponentFixture<AchievementListComponent>> {
    await TestBed.configureTestingModule({
      imports: [
        AchievementListComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: params, paramMap: unitId, snapshot: { data: { scope } } },
        },
        { provide: AchievementsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { isAuthenticated: signedIn } },
        { provide: OrganizationsService, useValue: { ofType: () => of([]) } },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AchievementListComponent);
    fixture.detectChanges();
    return fixture;
  }

  const element = (fixture: ComponentFixture<unknown>, id: string): Element | null =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);

  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({}));
    unitId = new BehaviorSubject(convertToParamMap({}));
    signedIn.set(true);
    service.list.mockReset().mockReturnValue(of(page([achievement])));
    service.units.mockReset().mockReturnValue(of([]));
  });

  it('ac1_8_shows_the_shared_awards_as_cards', async () => {
    const fixture = await open();

    expect(service.list).toHaveBeenCalledWith(
      {
        filters: NO_ACHIEVEMENT_FILTERS,
        page: 0,
        size: 20,
      },
      'signed-in',
    );
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="achievement-card"]'),
    ).toHaveLength(1);
  });

  it('ac1_8_loads_the_filters_from_the_address_bar_and_follows_its_changes', async () => {
    params.next(convertToParamMap({ unit: '9', year: '2025' }));
    await open();

    expect(service.list).toHaveBeenLastCalledWith(
      { filters: { ...NO_ACHIEVEMENT_FILTERS, unit: 9, year: 2025 }, page: 0, size: 20 },
      'signed-in',
    );

    params.next(convertToParamMap({ recipient: 'UNIT' }));

    expect(service.list).toHaveBeenLastCalledWith(
      { filters: { ...NO_ACHIEVEMENT_FILTERS, recipient: 'UNIT' }, page: 0, size: 20 },
      'signed-in',
    );
  });

  it('ac1_8_a_changed_filter_goes_into_the_address_bar_on_the_first_page', async () => {
    params.next(convertToParamMap({ unit: '9', page: '3' }));
    const fixture = await open();

    fixture.componentInstance.filter({ recipient: 'UNIT' });

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { unit: 9, recipient: 'UNIT' } }),
    );
  });

  it('ac1_8_a_page_change_goes_into_the_address_bar', async () => {
    const fixture = await open();

    fixture.componentInstance.page({ pageIndex: 1, pageSize: 50, length: 120 });

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { page: 1, size: 50 } }),
    );
  });

  it('ac1_8_an_empty_result_says_so', async () => {
    service.list.mockReturnValue(of(page([])));
    const fixture = await open();

    expect(element(fixture, 'achievements-empty')?.textContent).toContain(
      'Поки що немає досягнень за цими умовами.',
    );
  });

  it('ac1_6_an_unknown_unit_is_reported_without_a_retry', async () => {
    service.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const fixture = await open();

    expect(element(fixture, 'achievements-error')?.textContent).toContain(
      'Такого підрозділу немає.',
    );
    expect(element(fixture, 'achievements-retry')).toBeNull();
    expect(element(fixture, 'achievements-empty')).toBeNull();
  });

  it('edge_a_failed_load_offers_a_retry_that_loads_again', async () => {
    service.list.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    const fixture = await open();

    (element(fixture, 'achievements-retry') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledTimes(2);
    expect(element(fixture, 'achievements-error')).toBeNull();
    expect(element(fixture, 'achievement-card')).not.toBeNull();
  });

  it('ac2_4_a_unit_page_lists_the_unit_with_the_other_filters_and_no_unit_filter', async () => {
    unitId.next(convertToParamMap({ id: '64' }));
    params.next(convertToParamMap({ unit: '9', year: '2025' }));
    const fixture = await open();

    expect(service.list).toHaveBeenLastCalledWith(
      { filters: { ...NO_ACHIEVEMENT_FILTERS, unit: 64, year: 2025 }, page: 0, size: 20 },
      'signed-in',
    );
    expect(element(fixture, 'filter-unit')).toBeNull();
    expect(element(fixture, 'unit-header')).not.toBeNull();
    expect(element(fixture, 'achievements-title')).toBeNull();
  });

  it('ac2_4_a_filter_on_a_unit_page_keeps_the_unit_out_of_the_address_bar', async () => {
    unitId.next(convertToParamMap({ id: '64' }));
    const fixture = await open();

    fixture.componentInstance.filter({ year: 2024 });

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { year: 2024 } }),
    );
  });

  it('ac2_4_an_unknown_unit_shows_the_not_found_page', async () => {
    unitId.next(convertToParamMap({ id: '1' }));
    service.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await open();

    expect(navigate).toHaveBeenCalledWith(['/not-found'], { skipLocationChange: true });
  });

  it('ac2_4_an_id_that_is_no_number_shows_the_not_found_page_without_a_request', async () => {
    unitId.next(convertToParamMap({ id: 'abc' }));
    await open();

    expect(service.list).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/not-found'], { skipLocationChange: true });
  });

  it('ac2_5_an_anonymous_visitor_gets_the_public_set_without_a_staff_link', async () => {
    signedIn.set(false);
    const fixture = await open('public');

    expect(service.list).toHaveBeenCalledWith(expect.anything(), 'public');
    expect(element(fixture, 'achievements-title')?.textContent).toContain(
      'Досягнення університету',
    );
    expect(element(fixture, 'achievements-counterpart')).toBeNull();
    expect(element(fixture, 'achievement-unit')?.getAttribute('href')).toBe(
      '/public/units/64/achievements',
    );
  });

  it('ac2_6_a_signed_in_visitor_of_a_public_unit_page_is_offered_the_staff_page', async () => {
    unitId.next(convertToParamMap({ id: '64' }));
    params.next(convertToParamMap({ year: '2025' }));
    const fixture = await open('public');

    const link = element(fixture, 'achievements-counterpart');
    expect(link?.textContent).toContain('Переглянути як співробітник');
    expect(link?.getAttribute('href')).toBe('/units/64/achievements?year=2025');
  });

  it('ac2_6_the_staff_page_leads_to_the_public_page_with_the_same_filters', async () => {
    params.next(convertToParamMap({ unit: '9', level: 'NATIONAL' }));
    const fixture = await open();

    const link = element(fixture, 'achievements-counterpart');
    expect(link?.textContent).toContain('Публічна сторінка');
    expect(link?.getAttribute('href')).toBe('/public/achievements?unit=9&level=NATIONAL');
    expect(element(fixture, 'achievement-unit')?.getAttribute('href')).toBe(
      '/units/64/achievements',
    );
  });
});
