import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { PersonDialogComponent } from '../person-dialog/person-dialog.component';
import { AdminUsersActions } from '../store/admin-users.actions';
import { adminUsersFeature } from '../store/admin-users.feature';
import { UserPage, UserSummary } from '../users.service';
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
        confirm: 'Підтвердити',
        correctDepartment: 'Змінити кафедру',
        filters: { q: 'Пошук', qHint: 'Щонайменше 2 символи', role: 'Роль', status: 'Статус' },
        columns: { name: 'Користувач', email: 'Пошта', organization: 'Підрозділ' },
        paginator: { itemsPerPage: 'Рядків на сторінці', range: '{{from}}–{{to}} з {{total}}' },
      },
      status: { ACTIVE: 'Активний', UNCLAIMED: 'Без облікового запису' },
      people: {
        add: 'Додати колегу без облікового запису',
        badge: 'Без облікового запису',
        unknownAddress: 'Адреса невідома',
      },
      problems: { 'role-already-assigned': 'Роль уже призначено' },
    },
  },
};

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let store: Store;

  beforeEach(async () => {
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
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideStore(),
        provideState(adminUsersFeature),
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { permissions } },
      ],
    }).compileComponents();
    permissions.set(readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] })));
    store = TestBed.inject(Store);
    fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
  });

  it('ac2_10_lists_users_with_their_roles_status_and_the_unconfirmed_badge', () => {
    store.dispatch(AdminUsersActions.usersLoaded({ page }));
    fixture.detectChanges();
    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));

    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Нова Олена');
    expect(rows[0].textContent).toContain('Кафедра алгебри');
    expect(rows[0].textContent).toContain('Активний');
    expect(rows[0].querySelector('[data-testid="unconfirmed-badge"]')?.textContent).toContain(
      'Не підтверджено',
    );
    expect(rows[1].querySelector('[data-testid="unconfirmed-badge"]')).toBeNull();
    expect(rows[1].textContent).toContain('Працівник');
    expect(rows[0].querySelector('[data-testid="user-link"]')?.getAttribute('href')).toBe(
      '/admin/users/7',
    );
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

  it('ac2_7_confirms_membership_with_the_users_own_department_in_one_click', () => {
    store.dispatch(AdminUsersActions.usersLoaded({ page }));
    fixture.detectChanges();
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.nativeElement.querySelector('[data-testid="confirm-membership"]').click();

    expect(dispatch).toHaveBeenCalledWith(
      AdminUsersActions.membershipConfirmed({ id: 7, organizationId: 64 }),
    );
  });

  it('ac4_2_links_the_chosen_colleague_instead_of_a_plain_confirmation', () => {
    store.dispatch(AdminUsersActions.usersLoaded({ page }));
    fixture.detectChanges();
    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of({ organizationId: 64, person: { id: 41, version: 3 } }),
    } as ReturnType<typeof dialog.open>);
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.nativeElement.querySelector('[data-testid="correct-department"]').click();

    expect(dispatch).toHaveBeenCalledWith(
      AdminUsersActions.membershipLinked({ id: 7, personId: 41, version: 3 }),
    );
  });

  it('ac2_7_renders_a_refused_confirmation_inline_on_its_row', () => {
    store.dispatch(AdminUsersActions.usersLoaded({ page }));
    store.dispatch(
      AdminUsersActions.membershipConfirmFailed({ id: 7, problem: 'role-already-assigned' }),
    );
    fixture.detectChanges();
    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));

    expect(rows[0].querySelector('[data-testid="confirm-error"]')?.textContent).toContain(
      'Роль уже призначено',
    );
    expect(rows[1].querySelector('[data-testid="confirm-error"]')).toBeNull();
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
    const row: HTMLElement = fixture.nativeElement.querySelector('tbody tr');

    expect(row.querySelector('[data-testid="unclaimed-badge"]')?.textContent).toContain(
      'Без облікового запису',
    );
    expect(row.querySelector('[data-testid="unconfirmed-badge"]')).toBeNull();
    expect(row.querySelector('[data-testid="confirm-membership"]')).toBeNull();
    expect(row.textContent).toContain('Адреса невідома');
    expect(row.textContent).not.toContain('unclaimed.invalid');
  });

  it('ac1_9_offers_the_add_button_to_a_secretary_and_opens_the_new_person', () => {
    const dialog = TestBed.inject(MatDialog);
    const open = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of({ id: 41 }) } as ReturnType<typeof dialog.open>);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="person-add-open"]',
    );

    expect(button.textContent).toContain('Додати колегу без облікового запису');
    button.click();

    expect(open).toHaveBeenCalledWith(
      PersonDialogComponent,
      expect.objectContaining({ data: { person: null } }),
    );
    expect(navigate).toHaveBeenCalledWith(['/admin/users', 41]);
  });

  it('ac1_9_hides_the_add_button_without_a_secretary_or_dean_role', () => {
    permissions.set(readPermissions(token({ role_scopes: ['RECTOR_SECRETARY:1'] })));
    const other = TestBed.createComponent(UserListComponent);
    other.detectChanges();

    expect(other.nativeElement.querySelector('[data-testid="person-add-open"]')).toBeNull();
  });
});
