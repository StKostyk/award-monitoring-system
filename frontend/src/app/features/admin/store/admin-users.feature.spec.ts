import { UserDetail, UserPage, UserSummary } from '../users.service';
import { AdminUsersActions } from './admin-users.actions';
import { adminUsersFeature, initialState } from './admin-users.feature';

const organization = {
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
  organization,
  status: 'ACTIVE',
  roles: [],
  membershipConfirmed: false,
};

const page: UserPage = {
  content: [newcomer],
  totalElements: 1,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
};

const { reducer } = adminUsersFeature;

describe('adminUsersFeature', () => {
  it('ac2_10_keeps_the_filters_and_returns_to_the_first_page', () => {
    const paged = reducer(initialState, AdminUsersActions.pageChanged({ page: 3, size: 50 }));

    const filtered = reducer(
      paged,
      AdminUsersActions.filtersChanged({
        filters: { q: 'нова', role: 'EMPLOYEE', status: null, unconfirmed: true },
      }),
    );

    expect(filtered.page).toBe(0);
    expect(filtered.size).toBe(50);
    expect(filtered.loading).toBe(true);
    expect(adminUsersFeature.selectQuery.projector(filtered.filters, filtered.page, filtered.size)).toEqual({
      q: 'нова',
      role: 'EMPLOYEE',
      status: null,
      unconfirmed: true,
      page: 0,
      size: 50,
    });
  });

  it('ac2_10_stores_a_loaded_page_and_clears_the_problem', () => {
    const failed = reducer(initialState, AdminUsersActions.usersLoadFailed({ problem: 'network' }));

    expect(failed.users).toEqual([]);
    expect(failed.problem).toBe('network');

    const loaded = reducer(failed, AdminUsersActions.usersLoaded({ page }));

    expect(loaded.users).toEqual([newcomer]);
    expect(loaded.total).toBe(1);
    expect(loaded.loading).toBe(false);
    expect(loaded.problem).toBeNull();
  });

  it('ac2_7_remembers_which_row_is_being_confirmed_and_its_problem', () => {
    const confirming = reducer(
      initialState,
      AdminUsersActions.membershipConfirmed({ id: 7, organizationId: 64 }),
    );

    expect(confirming.confirmingId).toBe(7);

    const refused = reducer(
      confirming,
      AdminUsersActions.membershipConfirmFailed({ id: 7, problem: 'role-already-assigned' }),
    );

    expect(refused.confirmingId).toBeNull();
    expect(refused.confirmProblem).toEqual({ id: 7, problem: 'role-already-assigned' });

    expect(reducer(refused, AdminUsersActions.reloaded()).confirmProblem).toBeNull();
  });

  it('ac2_10_marks_an_unknown_user_as_not_found', () => {
    const detail: UserDetail = { ...newcomer, createdAt: '2026-09-01T00:00:00Z', lastLoginAt: null, roleHistory: [] };
    const loaded = reducer(initialState, AdminUsersActions.userLoaded({ user: detail }));

    expect(loaded.selected).toEqual(detail);
    expect(loaded.notFound).toBe(false);

    const missing = reducer(
      loaded,
      AdminUsersActions.userLoadFailed({ problem: 'unknown', notFound: true }),
    );

    expect(missing.notFound).toBe(true);
    expect(missing.selectedLoading).toBe(false);
    expect(reducer(missing, AdminUsersActions.userOpened({ id: 9 })).selected).toBeNull();
  });
});
