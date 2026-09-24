import { createFeature, createReducer, createSelector, on } from '@ngrx/store';

import { UserDetail, UserFilters, UserQuery, UserSummary } from '../users.service';
import { AdminUsersActions } from './admin-users.actions';

export interface ConfirmProblem {
  id: number;
  problem: string;
}

export interface AdminUsersState {
  filters: UserFilters;
  page: number;
  size: number;
  users: UserSummary[];
  total: number;
  loading: boolean;
  problem: string | null;
  confirmingId: number | null;
  confirmProblem: ConfirmProblem | null;
  selected: UserDetail | null;
  selectedLoading: boolean;
  selectedProblem: string | null;
  notFound: boolean;
}

export const NO_FILTERS: UserFilters = { q: '', role: null, status: null, unconfirmed: false };

export const initialState: AdminUsersState = {
  filters: NO_FILTERS,
  page: 0,
  size: 20,
  users: [],
  total: 0,
  loading: false,
  problem: null,
  confirmingId: null,
  confirmProblem: null,
  selected: null,
  selectedLoading: false,
  selectedProblem: null,
  notFound: false,
};

export const adminUsersFeature = createFeature({
  name: 'adminUsers',
  reducer: createReducer(
    initialState,
    on(AdminUsersActions.opened, (state) => ({ ...state, loading: true, problem: null })),
    on(AdminUsersActions.reloaded, (state) => ({
      ...state,
      loading: true,
      problem: null,
      confirmingId: null,
      confirmProblem: null,
    })),
    on(AdminUsersActions.filtersChanged, (state, { filters }) => ({
      ...state,
      filters,
      page: 0,
      loading: true,
      problem: null,
    })),
    on(AdminUsersActions.pageChanged, (state, { page, size }) => ({
      ...state,
      page,
      size,
      loading: true,
      problem: null,
    })),
    on(AdminUsersActions.usersLoaded, (state, { page }) => ({
      ...state,
      users: page.content,
      total: page.totalElements,
      page: page.number,
      size: page.size,
      loading: false,
      problem: null,
    })),
    on(AdminUsersActions.usersLoadFailed, (state, { problem }) => ({
      ...state,
      users: [],
      total: 0,
      loading: false,
      problem,
    })),
    on(AdminUsersActions.membershipConfirmed, (state, { id }) => ({
      ...state,
      confirmingId: id,
      confirmProblem: null,
    })),
    on(AdminUsersActions.membershipConfirmFailed, (state, { id, problem }) => ({
      ...state,
      confirmingId: null,
      confirmProblem: { id, problem },
    })),
    on(AdminUsersActions.userOpened, (state) => ({
      ...state,
      selected: null,
      selectedLoading: true,
      selectedProblem: null,
      notFound: false,
    })),
    on(AdminUsersActions.userReloaded, (state) => ({
      ...state,
      selectedLoading: true,
      selectedProblem: null,
    })),
    on(AdminUsersActions.userLoaded, (state, { user }) => ({
      ...state,
      selected: user,
      selectedLoading: false,
      selectedProblem: null,
      notFound: false,
    })),
    on(AdminUsersActions.userLoadFailed, (state, { problem, notFound }) => ({
      ...state,
      selectedLoading: false,
      selectedProblem: problem,
      notFound,
    })),
  ),
  extraSelectors: ({ selectFilters, selectPage, selectSize }) => ({
    selectQuery: createSelector(
      selectFilters,
      selectPage,
      selectSize,
      (filters, page, size): UserQuery => ({ ...filters, page, size }),
    ),
  }),
});
