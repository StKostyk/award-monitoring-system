import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { PersonDialogComponent } from '../person-dialog/person-dialog.component';
import { AdminUsersActions } from '../store/admin-users.actions';
import { adminUsersFeature } from '../store/admin-users.feature';
import { UserFilters, UserPage, UserSummary, UsersService } from '../users.service';
import { UserListComponent } from './user-list.component';

const department = {
  id: 64,
  name: 'Department of Algebra',
  nameUk: 'Кафедра алгебри',
  code: 'DAI',
  type: 'DEPARTMENT' as const,
};

const newcomer: UserSummary = {
  id: 7,
  email: 'newcomer@chnu.edu.ua',
  firstName: 'Олена',
  lastName: 'Нова',
  organization: department,
  status: 'ACTIVE',
  roles: [],
  membershipConfirmed: false,
};

const employee: UserSummary = {
  id: 5,
  email: 'employee.fmi@chnu.edu.ua',
  firstName: 'Анастасія',
  lastName: 'Працівник',
  organization: department,
  status: 'ACTIVE',
  roles: [
    { id: 3, role: 'EMPLOYEE', organization: department, validFrom: '2026-09-01', validTo: null },
  ],
  membershipConfirmed: true,
};

const unclaimed: UserSummary = {
  id: 41,
  email: 'unclaimed-1f2e@unclaimed.invalid',
  firstName: 'Оксана',
  lastName: 'Мельник',
  organization: department,
  status: 'UNCLAIMED',
  roles: [],
  membershipConfirmed: false,
};

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

const permissions = signal(readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] })));

const page: UserPage = {
  content: [newcomer, employee],
  totalElements: 2,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
};

const translations = {
  uk: {
    roles: { EMPLOYEE: 'Працівник' },
    admin: {
      users: {
        title: 'Користувачі',
        empty: 'Користувачів не знайдено',
        noRoles: 'Немає ролей',
        unconfirmedBadge: 'Не підтверджено',
        filters: { q: 'Пошук', qHint: 'Щонайменше 2 символи', role: 'Роль', status: 'Статус' },
        paginator: { itemsPerPage: 'Рядків на сторінці', range: '{{from}}–{{to}} з {{total}}' },
        counts: {
          total: 'У списку: {{count}}',
          waiting: 'Чекають перевірки членства: {{count}}',
          blocked: 'Заблоковано: {{count}}',
        },
        pick: 'Оберіть людину в списку',
      },
      status: { ACTIVE: 'Активний', UNCLAIMED: 'Без облікового запису' },
      people: {
        add: 'Додати колегу без облікового запису',
        badge: 'Без облікового запису',
      },
    },
  },
};

@Component({ template: '<p data-testid="pane-stub">pane</p>' })
class PaneStubComponent {}

function counted(total: number): UserPage {
  return { ...page, content: [], totalElements: total };
}

function countsBy(query: UserFilters): number {
  if (query.unconfirmed) {
    return 3;
  }
  return query.status === 'SUSPENDED' ? 1 : 42;
}

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let store: Store;
  let api: { list: ReturnType<typeof vi.fn> };

  async function setUp(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        UserListComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([
          {
            path: 'admin/users',
            component: UserListComponent,
            children: [{ path: ':id', component: PaneStubComponent }],
          },
        ]),
        provideStore(),
        provideState(adminUsersFeature),
        { provide: UsersService, useValue: api },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { permissions } },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
  }

  beforeEach(async () => {
    api = { list: vi.fn((query: UserFilters) => of(counted(countsBy(query)))) };
    permissions.set(readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] })));
    await setUp();
    fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
  });

  function rows(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="user-link"]'));
  }

  it('ac4_1_lists_people_with_initials_role_unit_and_status_chips', () => {
    store.dispatch(AdminUsersActions.usersLoaded({ page }));
    fixture.detectChanges();

    expect(rows()).toHaveLength(2);
    expect(rows()[0].textContent).toContain('Нова Олена');
    expect(rows()[0].textContent).toContain('ОН');
    expect(rows()[0].textContent).toContain('Немає ролей · Кафедра алгебри');
    expect(rows()[0].querySelector('[data-testid="user-status"]')?.textContent).toContain(
      'Активний',
    );
    expect(rows()[0].querySelector('[data-testid="unconfirmed-badge"]')?.textContent).toContain(
      'Не підтверджено',
    );
    expect(rows()[1].querySelector('[data-testid="unconfirmed-badge"]')).toBeNull();
    expect(rows()[1].textContent).toContain('Працівник · Кафедра алгебри');
    expect(rows()[0].getAttribute('href')).toBe('/admin/users/7');
    expect(rows()[0].dataset['email']).toBe('newcomer@chnu.edu.ua');
    expect(fixture.nativeElement.querySelector('[data-testid="users-pick-hint"]')).not.toBeNull();
  });

  it('ac4_2_counts_the_accounts_the_waiting_and_the_blocked_in_the_context_line', () => {
    expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({ size: 1, unconfirmed: true, status: 'ACTIVE' }),
    );
    expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({ size: 1, status: 'SUSPENDED' }),
    );
    expect(fixture.nativeElement.querySelector('[data-testid="page-context"]')?.textContent).toBe(
      'У списку: 42 · Чекають перевірки членства: 3 · Заблоковано: 1',
    );
  });

  it('ac4_2_ac4_5_counts_again_after_a_change_in_the_pane', () => {
    vi.useFakeTimers();
    api.list.mockClear();

    store.dispatch(AdminUsersActions.refreshed());
    vi.advanceTimersByTime(300);
    vi.useRealTimers();

    expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({ size: 1, status: 'SUSPENDED' }),
    );
  });

  it('ac4_2_leaves_out_a_count_that_failed', () => {
    api.list.mockImplementation((query: UserFilters) =>
      query.unconfirmed ? throwError(() => new Error('down')) : of(counted(5)),
    );
    const other = TestBed.createComponent(UserListComponent);
    other.detectChanges();

    expect(other.nativeElement.querySelector('[data-testid="page-context"]')?.textContent).toBe(
      'У списку: 5 · Заблоковано: 5',
    );
  });

  it('ac4_1_ac4_3_a_direct_link_selects_the_row_and_opens_the_pane', async () => {
    const harness = await RouterTestingHarness.create();
    const list = await harness.navigateByUrl('/admin/users/5', UserListComponent);
    store.dispatch(AdminUsersActions.usersLoaded({ page }));
    harness.detectChanges();
    const element = harness.routeNativeElement as HTMLElement;

    expect(list.selectedId()).toBe(5);
    expect(
      element.querySelector('[data-testid="user-link"][aria-current="true"]')?.textContent,
    ).toContain('Працівник Анастасія');
    expect(element.querySelector('[data-testid="pane-stub"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="users-pick-hint"]')).toBeNull();

    await harness.navigateByUrl('/admin/users/7');

    expect(list.selectedId()).toBe(7);

    const dispatch = vi.spyOn(store, 'dispatch');
    await harness.navigateByUrl('/admin/users');

    expect(list.selectedId()).toBeNull();
    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.refreshed());
  });

  it('ac2_10_ignores_a_free_text_filter_shorter_than_two_characters', () => {
    vi.useFakeTimers();
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.componentInstance.filters.controls.q.setValue(' н ');
    vi.advanceTimersByTime(300);

    expect(dispatch).toHaveBeenLastCalledWith(
      AdminUsersActions.filtersChanged({
        filters: { q: '', role: null, status: null, unconfirmed: false },
      }),
    );

    fixture.componentInstance.filters.patchValue({ q: 'нов', unconfirmed: true });
    vi.advanceTimersByTime(300);

    expect(dispatch).toHaveBeenLastCalledWith(
      AdminUsersActions.filtersChanged({
        filters: { q: 'нов', role: null, status: null, unconfirmed: true },
      }),
    );
    vi.useRealTimers();
  });

  it('ac2_10_asks_the_server_for_the_next_page', () => {
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.componentInstance.changePage({ pageIndex: 2, pageSize: 50, length: 120 });

    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.pageChanged({ page: 2, size: 50 }));
  });

  it('ac2_10_shows_the_empty_state_when_nothing_matches', () => {
    store.dispatch(
      AdminUsersActions.usersLoaded({ page: { ...page, content: [], totalElements: 0 } }),
    );
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="users-empty"]').textContent,
    ).toContain('Користувачів не знайдено');
  });

  it('ac1_9_lists_a_person_without_an_account_with_the_chip_and_no_placeholder_address', () => {
    store.dispatch(AdminUsersActions.usersLoaded({ page: { ...page, content: [unclaimed] } }));
    fixture.detectChanges();
    const row = rows()[0];

    expect(row.querySelector('[data-testid="unclaimed-badge"]')?.textContent).toContain(
      'Без облікового запису',
    );
    expect(row.querySelector('[data-testid="unconfirmed-badge"]')).toBeNull();
    expect(row.textContent).not.toContain('unclaimed.invalid');
    expect(row.dataset['email']).toBeUndefined();
  });

  it('ac1_9_ac4_4_offers_the_add_button_and_selects_the_new_person_in_a_refreshed_list', () => {
    const dialog = TestBed.inject(MatDialog);
    const open = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of({ id: 41 }) } as ReturnType<typeof dialog.open>);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const dispatch = vi.spyOn(store, 'dispatch');
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="person-add-open"]',
    );

    expect(button.textContent).toContain('Додати колегу без облікового запису');
    button.click();

    expect(open).toHaveBeenCalledWith(
      PersonDialogComponent,
      expect.objectContaining({ data: { person: null } }),
    );
    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.refreshed());
    expect(navigate).toHaveBeenCalledWith(['/admin/users', 41]);
  });

  it('ac1_9_hides_the_add_button_without_a_secretary_or_dean_role', () => {
    permissions.set(readPermissions(token({ role_scopes: ['RECTOR_SECRETARY:1'] })));
    const other = TestBed.createComponent(UserListComponent);
    other.detectChanges();

    expect(other.nativeElement.querySelector('[data-testid="person-add-open"]')).toBeNull();
  });
});
