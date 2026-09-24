import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
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
  roles: [{ id: 3, role: 'EMPLOYEE', organization: department, validFrom: '2026-09-01', validTo: null }],
  membershipConfirmed: true,
};

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
      status: { ACTIVE: 'Активний' },
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
      ],
    }).compileComponents();
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
    expect(
      rows[0].querySelector('[data-testid="user-link"]')?.getAttribute('href'),
    ).toBe('/admin/users/7');
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
    store.dispatch(AdminUsersActions.usersLoaded({ page: { ...page, content: [], totalElements: 0 } }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="users-empty"]').textContent).toContain(
      'Користувачів не знайдено',
    );
  });
});
