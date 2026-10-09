import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockInstance, vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
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
    },
  },
};

describe('AchievementListComponent', () => {
  const service = { list: vi.fn(), units: vi.fn() };
  let params: BehaviorSubject<ParamMap>;
  let navigate: MockInstance<Router['navigate']>;

  async function open(): Promise<ComponentFixture<AchievementListComponent>> {
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
        { provide: ActivatedRoute, useValue: { queryParamMap: params } },
        { provide: AchievementsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
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
    service.list.mockReset().mockReturnValue(of(page([achievement])));
    service.units.mockReset().mockReturnValue(of([]));
  });

  it('ac1_8_shows_the_shared_awards_as_cards', async () => {
    const fixture = await open();

    expect(service.list).toHaveBeenCalledWith({
      filters: NO_ACHIEVEMENT_FILTERS,
      page: 0,
      size: 20,
    });
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="achievement-card"]'),
    ).toHaveLength(1);
  });

  it('ac1_8_loads_the_filters_from_the_address_bar_and_follows_its_changes', async () => {
    params.next(convertToParamMap({ unit: '9', year: '2025' }));
    await open();

    expect(service.list).toHaveBeenLastCalledWith({
      filters: { ...NO_ACHIEVEMENT_FILTERS, unit: 9, year: 2025 },
      page: 0,
      size: 20,
    });

    params.next(convertToParamMap({ recipient: 'UNIT' }));

    expect(service.list).toHaveBeenLastCalledWith({
      filters: { ...NO_ACHIEVEMENT_FILTERS, recipient: 'UNIT' },
      page: 0,
      size: 20,
    });
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
});
