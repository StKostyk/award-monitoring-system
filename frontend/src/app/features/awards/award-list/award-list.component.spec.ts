import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { provideIsoDateAdapter } from '../../../core/i18n/iso-date-adapter';
import { LanguageService } from '../../../core/i18n/language.service';
import { Award, AwardFilters, AwardsService, NO_FILTERS } from '../awards.service';
import { AwardsActions } from '../store/awards.actions';
import { awardsFeature } from '../store/awards.feature';
import { AwardListComponent, linkedFilters } from './award-list.component';

function award(overrides: Partial<Award> = {}): Award {
  return {
    id: 5,
    title: null,
    titleUk: 'Грамота Міністерства освіти і науки',
    description: null,
    descriptionUk: null,
    category: {
      id: 13,
      name: 'Ministry Recognition',
      nameUk: 'Відзнака міністерства',
      level: 'NATIONAL',
    },
    awardingOrganization: 'МОН України',
    awardDate: '2025-05-01',
    externalUrl: null,
    status: 'DRAFT',
    impactScore: null,
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    recipient: { type: 'PERSON', organization: null },
    organization: {
      id: 64,
      name: 'Algebra',
      nameUk: 'Кафедра алгебри',
      code: 'DAI',
      type: 'DEPARTMENT',
    },
    visibility: 'PRIVATE',
    request: null,
    warnings: [],
    createdAt: '2026-09-28T08:00:00Z',
    updatedAt: '2026-09-28T08:00:00Z',
    version: 1,
    ...overrides,
  };
}

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

const translations = {
  uk: {
    app: { loading: 'Завантаження' },
    categories: { levels: { NATIONAL: 'Національний' } },
    awards: {
      title: 'Мої нагороди',
      timing: { expected: 'Очікується до {{date}}', delayed: 'Затримка' },
      statusPanel: { returned: 'Очікує ваших виправлень' },
      add: 'Додати нагороду',
      resetFilters: 'Скинути фільтри',
      empty: 'Нагород ще немає.',
      noMatch: 'Немає нагород, що відповідають пошуку.',
      filters: {
        search: 'Пошук у моїх нагородах',
        status: 'Статус',
        all: 'Усі',
        toggle: 'Фільтри',
        toggleActive: 'Фільтри: {{count}}',
      },
      messages: { removed: 'Чернетку видалено.' },
      status: {
        DRAFT: 'Чернетка',
        PENDING: 'На розгляді',
        APPROVED: 'Затверджено',
        REJECTED: 'Відхилено',
        ARCHIVED: 'В архіві',
      },
      fields: { recipient: 'Отримувач' },
      visibility: { chips: { UNIVERSITY: 'Колегам', PUBLIC: 'Публічно' } },
    },
  },
};

const COUNTS = { DRAFT: 2, PENDING: 1, APPROVED: 7, REJECTED: 0, ARCHIVED: 0 };

describe('AwardListComponent', () => {
  let counts: (filters: AwardFilters) => Observable<typeof COUNTS>;
  let categoryLoads: number;
  let fixture: ComponentFixture<AwardListComponent>;
  let store: Store;
  const permissions = signal(
    readPermissions(token({ permissions: ['award:read:own', 'award:create'] })),
  );

  beforeEach(() => {
    counts = () => of(COUNTS);
    categoryLoads = 0;
  });

  async function create(narrow = false): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        AwardListComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideIsoDateAdapter(),
        provideRouter([]),
        provideStore(),
        provideState(awardsFeature),
        {
          provide: AwardsService,
          useValue: {
            categories: () =>
              new Observable((subscriber) => {
                categoryLoads++;
                subscriber.next([]);
                subscriber.complete();
              }),
            statusCounts: (f: AwardFilters) => counts(f),
          },
        },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { permissions } },
        {
          provide: BreakpointObserver,
          useValue: { observe: () => of({ matches: narrow, breakpoints: {} }) },
        },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    vi.spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(AwardListComponent);
    fixture.detectChanges();
  }

  it('ac1_loads_the_categories_in_a_later_task', async () => {
    await create();

    expect(categoryLoads).toBe(0);
    await new Promise((resolve) => setTimeout(resolve));
    expect(categoryLoads).toBe(1);
  });

  it('ac1_9_lists_own_awards_with_their_status_chip_and_links_drafts_to_the_form', async () => {
    await create();
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: {
          content: [award(), award({ id: 6, status: 'PENDING', titleUk: 'Подяка' })],
          totalElements: 2,
          totalPages: 1,
          size: 100,
          number: 0,
        },
      }),
    );
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const items = element.querySelectorAll('[data-testid="award-item"]');

    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Відзнака міністерства');
    expect(items[0].textContent).toContain('01.05.2025');
    expect(items[0].querySelector('[data-testid="award-status"]')?.textContent).toContain(
      'Чернетка',
    );
    expect(items[0].querySelector('a')?.getAttribute('href')).toBe('/awards/5/edit');
    expect(items[1].querySelector('a')?.getAttribute('href')).toBe('/awards/6');
    expect(element.querySelector('[data-testid="award-add"]')).not.toBeNull();
  });

  it('ac1_9_approved_shared_awards_carry_whom_they_are_shown_to', async () => {
    await create();
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: {
          content: [
            award({ id: 5, status: 'APPROVED', visibility: 'UNIVERSITY' }),
            award({ id: 6, status: 'APPROVED', visibility: 'PUBLIC' }),
            award({ id: 7, status: 'APPROVED', visibility: 'PRIVATE' }),
            award({ id: 8, status: 'DRAFT', visibility: 'PUBLIC' }),
          ],
          totalElements: 4,
          totalPages: 1,
          size: 20,
          number: 0,
        },
      }),
    );
    fixture.detectChanges();
    const chips = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="award-item"]'),
    ).map(
      (item) => item.querySelector('[data-testid="award-visibility"]')?.textContent?.trim() ?? null,
    );

    expect(chips).toEqual(['Колегам', 'Публічно', null, null]);
  });

  it('ac0_7_a_unit_award_carries_the_unit_as_a_chip', async () => {
    await create();
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: {
          content: [
            award(),
            award({
              id: 6,
              recipient: {
                type: 'UNIT',
                organization: {
                  id: 64,
                  name: 'Algebra',
                  nameUk: 'Кафедра алгебри',
                  type: 'DEPARTMENT',
                },
              },
            }),
          ],
          totalElements: 2,
          totalPages: 1,
          size: 100,
          number: 0,
        },
      }),
    );
    fixture.detectChanges();
    const items = (fixture.nativeElement as HTMLElement).querySelectorAll(
      '[data-testid="award-item"]',
    );

    expect(items[0].querySelector('[data-testid="award-unit"]')).toBeNull();
    expect(items[1].querySelector('[data-testid="award-unit"]')?.textContent).toContain(
      'Кафедра алгебри',
    );
  });

  it('ac1_18_a_returned_request_waits_for_the_owner_corrections', async () => {
    await create();
    const request = {
      status: 'RETURNED' as const,
      currentLevel: 'FACULTY_SECRETARY' as const,
      submittedAt: '2026-09-28T09:00:00Z',
      deadline: '2026-10-01T09:00:00Z',
      estimatedCompletion: null,
      overdue: false,
    };
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: {
          content: [award({ id: 6, status: 'PENDING', request })],
          totalElements: 1,
          totalPages: 1,
          size: 100,
          number: 0,
        },
      }),
    );
    fixture.detectChanges();
    const item = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="award-item"]');

    const timing = item?.querySelector('[data-testid="request-timing"]')?.textContent;
    expect(timing).toContain('Очікує ваших виправлень');
    expect(timing).not.toContain('Очікується до');
  });

  it('ac1_18_submitted_awards_show_the_expected_date_and_a_delay_chip', async () => {
    await create();
    const request = {
      status: 'SUBMITTED' as const,
      currentLevel: 'FACULTY_SECRETARY' as const,
      submittedAt: '2026-09-28T09:00:00Z',
      deadline: '2026-10-01T09:00:00Z',
      estimatedCompletion: '2026-10-07',
      overdue: false,
    };
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: {
          content: [
            award({ id: 6, status: 'PENDING', request }),
            award({ id: 7, status: 'PENDING', request: { ...request, overdue: true } }),
            award(),
          ],
          totalElements: 3,
          totalPages: 1,
          size: 100,
          number: 0,
        },
      }),
    );
    fixture.detectChanges();
    const items = (fixture.nativeElement as HTMLElement).querySelectorAll(
      '[data-testid="award-item"]',
    );

    expect(items[0].querySelector('[data-testid="request-timing"]')?.textContent).toContain(
      'Очікується до 07.10.2026',
    );
    expect(items[0].querySelector('[data-testid="request-delayed"]')).toBeNull();
    expect(items[1].querySelector('[data-testid="request-delayed"]')?.textContent).toContain(
      'Затримка',
    );
    expect(items[2].querySelector('[data-testid="request-timing"]')).toBeNull();
  });

  it('ac1_9_shows_the_empty_list_and_asks_for_the_first_award', async () => {
    await create();
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: { content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 },
      }),
    );
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="awards-empty"]')?.textContent,
    ).toContain('Нагород ще немає.');
  });

  it('ac8_pages_through_the_awards_instead_of_cutting_the_list', async () => {
    await create();
    const element: HTMLElement = fixture.nativeElement;
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 },
      }),
    );
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="awards-paginator"]')).toBeNull();

    store.dispatch(
      AwardsActions.awardsLoaded({
        page: {
          content: [award(), award({ id: 6 })],
          totalElements: 130,
          totalPages: 7,
          size: 20,
          number: 0,
        },
      }),
    );
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')?.click();

    expect(element.querySelector('[data-testid="awards-paginator"]')).not.toBeNull();
    expect(store.dispatch).toHaveBeenCalledWith(
      AwardsActions.pageChanged({ pageIndex: 1, pageSize: 20 }),
    );
  });

  it('f9_shows_the_notice_of_a_deleted_draft', async () => {
    history.replaceState({ notice: 'awards.messages.removed' }, '');
    await create();
    fixture.detectChanges();
    history.replaceState(null, '');

    expect(
      fixture.nativeElement.querySelector('[data-testid="awards-notice"]')?.textContent,
    ).toContain('Чернетку видалено.');
  });

  it('ac1_7_changing_a_filter_reloads_the_list', async () => {
    await create();

    fixture.componentInstance.filter({ status: 'PENDING' });

    expect(store.dispatch).toHaveBeenCalledWith(
      AwardsActions.filtersChanged({
        filters: { ...NO_FILTERS, status: 'PENDING' },
      }),
    );
  });

  it('ac1_10_a_search_reloads_the_list_with_the_other_filters', async () => {
    await create();
    store.dispatch(AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, status: 'DRAFT' } }));

    fixture.componentInstance.filter({ q: 'horizon' });

    expect(store.dispatch).toHaveBeenLastCalledWith(
      AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, status: 'DRAFT', q: 'horizon' } }),
    );
  });

  it('ac1_10_shows_the_search_field_with_the_search_of_the_list', async () => {
    await create();
    store.dispatch(AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, q: 'horizon' } }));
    fixture.detectChanges();
    await fixture.whenStable();

    const input = fixture.nativeElement.querySelector(
      '[data-testid="filter-q"]',
    ) as HTMLInputElement;
    expect(input.value).toBe('horizon');
  });

  it('ac1_10_a_search_without_matches_says_so', async () => {
    await create();
    store.dispatch(AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, q: 'horizon' } }));
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 },
      }),
    );
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="awards-empty"]')?.textContent,
    ).toContain('Немає нагород, що відповідають пошуку.');
  });

  it('ac1_2_hides_the_add_button_without_award_create', async () => {
    permissions.set(readPermissions(token({ permissions: ['award:read:own'] })));
    await create();

    expect(fixture.nativeElement.querySelector('[data-testid="award-add"]')).toBeNull();
  });

  it('ac1_2_a_phone_shows_the_search_and_opens_the_other_filters_on_request', async () => {
    await create(true);
    const element: HTMLElement = fixture.nativeElement;
    const toggle = element.querySelector<HTMLButtonElement>('[data-testid="filters-toggle"]');

    expect(element.querySelector('app-search-field')).not.toBeNull();
    expect(element.querySelector('[data-testid="filter-status"]')).toBeNull();
    expect(toggle?.getAttribute('aria-expanded')).toBe('false');
    expect(toggle?.textContent).toContain('Фільтри');

    toggle?.click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="filter-status"]')).not.toBeNull();
    expect(toggle?.getAttribute('aria-expanded')).toBe('true');
  });

  it('ac1_2_the_filter_button_counts_the_filters_in_use', async () => {
    await create(true);
    store.dispatch(
      AwardsActions.filtersChanged({
        filters: { ...NO_FILTERS, status: 'PENDING', dateFrom: '2026-01-01', q: 'грамота' },
      }),
    );
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="filters-toggle"]').textContent,
    ).toContain('Фільтри: 2');
  });

  it('ac1_2_a_wide_screen_shows_every_filter_without_the_button', async () => {
    await create();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="filters-toggle"]')).toBeNull();
    expect(element.querySelector('[data-testid="filter-status"]')).not.toBeNull();
  });

  function loaded(content: Award[]): void {
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: { content, totalElements: content.length, totalPages: 1, size: 20, number: 0 },
      }),
    );
    fixture.detectChanges();
  }

  it('ac5_1_a_row_shows_level_title_recipient_date_and_status', async () => {
    await create();
    loaded([award()]);
    const item = fixture.nativeElement.querySelector('[data-testid="award-item"]') as HTMLElement;

    expect(item.querySelector('[data-testid="award-level"]')?.textContent).toContain(
      'Національний',
    );
    expect(item.querySelector('[data-testid="award-title"]')?.textContent).toContain('Грамота');
    expect(item.querySelector('[data-testid="award-recipient"]')?.textContent).toContain(
      'Анастасія Коваль',
    );
    expect(item.textContent).toContain('01.05.2025');
    expect(item.querySelector('[data-testid="award-status"]')?.textContent).toContain('Чернетка');
  });

  it('ac5_1_status_chips_show_their_counts_and_filter_the_list', async () => {
    await create();
    loaded([award()]);
    const element: HTMLElement = fixture.nativeElement;
    const count = (testId: string): string | undefined =>
      element.querySelector(`[data-testid="${testId}"] [data-testid="status-count"]`)?.textContent;

    expect(count('status-chip-all')).toBe('10');
    expect(count('status-chip-APPROVED')).toBe('7');
    expect(count('status-chip-REJECTED')).toBe('0');
    expect(
      element.querySelector('[data-testid="status-chip-all"]')?.getAttribute('aria-pressed'),
    ).toBe('true');

    (element.querySelector('[data-testid="status-chip-APPROVED"]') as HTMLButtonElement).click();

    expect(store.dispatch).toHaveBeenLastCalledWith(
      AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, status: 'APPROVED' } }),
    );
  });

  it('ac5_1_counts_follow_the_other_filters_but_not_the_status', async () => {
    const asked: AwardFilters[] = [];
    counts = (filters) => {
      asked.push(filters);
      return of(COUNTS);
    };
    await create();

    store.dispatch(AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, status: 'DRAFT' } }));
    store.dispatch(AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, q: 'horizon' } }));

    expect(asked).toEqual([NO_FILTERS, { ...NO_FILTERS, q: 'horizon' }]);
  });

  it('ac5_1_chips_stay_without_numbers_when_the_counts_fail', async () => {
    counts = () => throwError(() => new Error('down'));
    await create();
    loaded([award()]);

    expect(fixture.nativeElement.querySelector('[data-testid="status-chips"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="status-count"]')).toBeNull();
  });

  it('ac5_4_a_filter_without_results_offers_to_clear_the_filters', async () => {
    await create();
    store.dispatch(
      AwardsActions.filtersChanged({ filters: { ...NO_FILTERS, status: 'REJECTED' } }),
    );
    loaded([]);
    const empty = fixture.nativeElement.querySelector(
      '[data-testid="awards-empty"]',
    ) as HTMLElement;

    expect(empty.querySelector('app-empty-state app-icon')).not.toBeNull();
    expect(empty.textContent).toContain('Немає нагород, що відповідають пошуку.');
    (empty.querySelector('[data-testid="awards-reset"]') as HTMLButtonElement).click();

    expect(store.dispatch).toHaveBeenLastCalledWith(
      AwardsActions.filtersChanged({ filters: NO_FILTERS }),
    );
  });

  it('ac5_4_an_empty_list_offers_the_first_award', async () => {
    permissions.set(readPermissions(token({ permissions: ['award:read:own', 'award:create'] })));
    await create();
    loaded([]);

    expect(
      fixture.nativeElement.querySelector('[data-testid="awards-empty-add"]')?.getAttribute('href'),
    ).toBe('/awards/new');
  });

  it('ac5_5_skeleton_rows_replace_the_list_while_it_loads', async () => {
    await create();
    const element: HTMLElement = fixture.nativeElement;

    expect(
      element.querySelector('[data-testid="awards-loading"]')?.getAttribute('aria-label'),
    ).toBe('Завантаження');
    expect(element.querySelectorAll('[data-testid="skeleton-row"]').length).toBeGreaterThan(0);
    expect(element.querySelector('[data-testid="award-list"]')).toBeNull();
    expect(element.querySelector('[data-testid="awards-empty"]')).toBeNull();

    loaded([award()]);

    expect(element.querySelector('[data-testid="awards-loading"]')).toBeNull();
    expect(element.querySelector('[data-testid="award-list"]')).not.toBeNull();
  });
});

describe('linkedFilters', () => {
  it('ac4_2_reads_the_status_and_visibility_of_a_dashboard_link', () => {
    expect(linkedFilters(convertToParamMap({ status: 'APPROVED', visibility: 'PUBLIC' }))).toEqual({
      ...NO_FILTERS,
      status: 'APPROVED',
      visibility: 'PUBLIC',
    });
    expect(linkedFilters(convertToParamMap({ status: 'DRAFT' }))).toEqual({
      ...NO_FILTERS,
      status: 'DRAFT',
    });
  });

  it('ac4_2_ignores_unknown_values_and_keeps_the_stored_filters_without_a_link', () => {
    expect(linkedFilters(convertToParamMap({ status: 'LOST', visibility: 'ALL' }))).toBeNull();
    expect(linkedFilters(convertToParamMap({}))).toBeNull();
  });
});
