import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { UserDetail, UserFilters, UserPage } from '../users.service';

export const AdminUsersActions = createActionGroup({
  source: 'Admin Users',
  events: {
    Opened: emptyProps(),
    Reloaded: emptyProps(),
    'Filters Changed': props<{ filters: UserFilters }>(),
    'Page Changed': props<{ page: number; size: number }>(),
    'Users Loaded': props<{ page: UserPage }>(),
    'Users Load Failed': props<{ problem: string }>(),
    'Membership Confirmed': props<{ id: number; organizationId: number }>(),
    'Membership Confirm Failed': props<{ id: number; problem: string }>(),
    'User Opened': props<{ id: number }>(),
    'User Reloaded': props<{ id: number }>(),
    'User Loaded': props<{ user: UserDetail }>(),
    'User Load Failed': props<{ problem: string; notFound: boolean }>(),
  },
});
