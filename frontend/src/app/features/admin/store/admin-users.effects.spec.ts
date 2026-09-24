import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action, Store, provideState, provideStore } from '@ngrx/store';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { UserDetail, UserPage, UsersService } from '../users.service';
import { AdminUsersActions } from './admin-users.actions';
import { AdminUsersEffects } from './admin-users.effects';
import { adminUsersFeature } from './admin-users.feature';

const page: UserPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
  first: true,
  last: true,
};

const detail: UserDetail = {
  id: 7,
  email: 'newcomer@chnu.edu.ua',
  firstName: 'Олена',
  lastName: 'Нова',
  organization: { id: 64, name: 'Department', nameUk: 'Кафедра', code: 'DAI', type: 'DEPARTMENT' },
  status: 'ACTIVE',
  roles: [],
  membershipConfirmed: false,
  createdAt: '2026-09-01T00:00:00Z',
  lastLoginAt: null,
  roleHistory: [],
};

function problem(type: string, status: number): HttpErrorResponse {
  return new HttpErrorResponse({ error: { type: `urn:awards:problem:${type}` }, status });
}

describe('AdminUsersEffects', () => {
  let actions$: Subject<Action>;
  let effects: AdminUsersEffects;
  let api: {
    list: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
    assignRole: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    actions$ = new Subject<Action>();
    api = {
      list: vi.fn().mockReturnValue(of(page)),
      get: vi.fn().mockReturnValue(of(detail)),
      assignRole: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [
        provideStore(),
        provideState(adminUsersFeature),
        provideMockActions(() => actions$),
        { provide: UsersService, useValue: api },
        AdminUsersEffects,
      ],
    });
    effects = TestBed.inject(AdminUsersEffects);
  });

  it('ac2_10_loads_the_page_with_the_filters_held_in_the_store', async () => {
    TestBed.inject(Store).dispatch(
      AdminUsersActions.filtersChanged({
        filters: { q: 'нова', role: 'EMPLOYEE', status: 'ACTIVE', unconfirmed: true },
      }),
    );
    const result = firstValueFrom(effects.loadUsers$);
    actions$.next(AdminUsersActions.opened());

    expect(await result).toEqual(AdminUsersActions.usersLoaded({ page }));
    expect(api.list).toHaveBeenCalledWith({
      q: 'нова',
      role: 'EMPLOYEE',
      status: 'ACTIVE',
      unconfirmed: true,
      page: 0,
      size: 20,
    });
  });

  it('reports a failed page as a typed problem', async () => {
    api.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const result = firstValueFrom(effects.loadUsers$);
    actions$.next(AdminUsersActions.opened());

    expect(await result).toEqual(AdminUsersActions.usersLoadFailed({ problem: 'network' }));
  });

  it('ac2_7_confirms_membership_with_the_employee_role_and_reloads_the_list', async () => {
    const result = firstValueFrom(effects.confirmMembership$);
    actions$.next(AdminUsersActions.membershipConfirmed({ id: 7, organizationId: 64 }));

    expect(await result).toEqual(AdminUsersActions.reloaded());
    expect(api.assignRole).toHaveBeenCalledWith(7, {
      role: 'EMPLOYEE',
      organizationId: 64,
      updateOrganization: true,
    });
  });

  it('ac2_7_keeps_a_refused_confirmation_on_its_own_row', async () => {
    api.assignRole.mockReturnValue(throwError(() => problem('role-already-assigned', 409)));
    const result = firstValueFrom(effects.confirmMembership$);
    actions$.next(AdminUsersActions.membershipConfirmed({ id: 7, organizationId: 64 }));

    expect(await result).toEqual(
      AdminUsersActions.membershipConfirmFailed({ id: 7, problem: 'role-already-assigned' }),
    );
  });

  it('ac2_10_answers_a_missing_user_with_a_not_found_state_and_one_request', async () => {
    api.get.mockReturnValue(throwError(() => problem('user-not-found', 404)));
    const result = firstValueFrom(effects.loadUser$);
    actions$.next(AdminUsersActions.userOpened({ id: 999999 }));

    expect(await result).toEqual(
      AdminUsersActions.userLoadFailed({ problem: 'user-not-found', notFound: true }),
    );
    expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('loads one user', async () => {
    const result = firstValueFrom(effects.loadUser$);
    actions$.next(AdminUsersActions.userOpened({ id: 7 }));

    expect(await result).toEqual(AdminUsersActions.userLoaded({ user: detail }));
  });
});
