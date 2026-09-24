import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { catchError, map, mergeMap, of, switchMap, withLatestFrom } from 'rxjs';

import { problemStatus, problemType } from '../../../core/api/problem';
import { UsersService } from '../users.service';
import { AdminUsersActions } from './admin-users.actions';
import { adminUsersFeature } from './admin-users.feature';

@Injectable()
export class AdminUsersEffects {
  private readonly actions$ = inject(Actions);
  private readonly users = inject(UsersService);
  private readonly store = inject(Store);

  readonly loadUsers$ = createEffect(() =>
    this.actions$.pipe(
      ofType(
        AdminUsersActions.opened,
        AdminUsersActions.reloaded,
        AdminUsersActions.filtersChanged,
        AdminUsersActions.pageChanged,
      ),
      withLatestFrom(this.store.select(adminUsersFeature.selectQuery)),
      switchMap(([, query]) =>
        this.users.list(query).pipe(
          map((page) => AdminUsersActions.usersLoaded({ page })),
          catchError((error: unknown) =>
            of(AdminUsersActions.usersLoadFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );

  readonly confirmMembership$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminUsersActions.membershipConfirmed),
      mergeMap(({ id, organizationId }) =>
        this.users
          .assignRole(id, { role: 'EMPLOYEE', organizationId, updateOrganization: true })
          .pipe(
            map(() => AdminUsersActions.reloaded()),
            catchError((error: unknown) =>
              of(AdminUsersActions.membershipConfirmFailed({ id, problem: problemType(error) })),
            ),
          ),
      ),
    ),
  );

  readonly loadUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminUsersActions.userOpened, AdminUsersActions.userReloaded),
      switchMap(({ id }) =>
        this.users.get(id).pipe(
          map((user) => AdminUsersActions.userLoaded({ user })),
          catchError((error: unknown) =>
            of(
              AdminUsersActions.userLoadFailed({
                problem: problemType(error),
                notFound: problemStatus(error) === 404,
              }),
            ),
          ),
        ),
      ),
    ),
  );
}
