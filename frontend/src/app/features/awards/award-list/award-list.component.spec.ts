import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { provideIsoDateAdapter } from '../../../core/i18n/iso-date-adapter';
import { LanguageService } from '../../../core/i18n/language.service';
import { Award, AwardsService } from '../awards.service';
import { AwardsActions } from '../store/awards.actions';
import { awardsFeature } from '../store/awards.feature';
import { AwardListComponent } from './award-list.component';

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
    awards: {
      title: 'Мої нагороди',
      timing: { expected: 'Очікується до {{date}}', delayed: 'Затримка' },
      statusPanel: { returned: 'Очікує ваших виправлень' },
      add: 'Додати нагороду',
      empty: 'Нагород ще немає.',
      messages: { removed: 'Чернетку видалено.' },
      status: { DRAFT: 'Чернетка', PENDING: 'На розгляді', APPROVED: 'Затверджено' },
      visibility: { chips: { UNIVERSITY: 'Колегам', PUBLIC: 'Публічно' } },
    },
  },
};

describe('AwardListComponent', () => {
  let fixture: ComponentFixture<AwardListComponent>;
  let store: Store;
  const permissions = signal(
    readPermissions(token({ permissions: ['award:read:own', 'award:create'] })),
  );

  async function create(): Promise<void> {
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
        { provide: AwardsService, useValue: { categories: () => of([]) } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { permissions } },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    vi.spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(AwardListComponent);
    fixture.detectChanges();
  }

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
        filters: { status: 'PENDING', category: null, dateFrom: null, dateTo: null },
      }),
    );
  });

  it('ac1_2_hides_the_add_button_without_award_create', async () => {
    permissions.set(readPermissions(token({ permissions: ['award:read:own'] })));
    await create();

    expect(fixture.nativeElement.querySelector('[data-testid="award-add"]')).toBeNull();
  });
});
