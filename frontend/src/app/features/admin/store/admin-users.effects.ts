import { HttpStatusCode } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Action, Store } from '@ngrx/store';
import { Observable, catchError, map, mergeMap, of, switchMap, take, withLatestFrom } from 'rxjs';

import { problemStatus, problemType } from '../../../core/api/problem';
import { PeopleService } from '../people.service';
import { UsersService } from '../users.service';
import { AdminUsersActions } from './admin-users.actions';
import { adminUsersFeature } from './admin-users.feature';

@Injectable()
export class AdminUsersEffects {
  private readonly actions$ = inject(Actions);
  private readonly users = inject(UsersService);
  private readonly people = inject(PeopleService);
  private readonly store = inject(Store);

  readonly loadUsers$ = createEffect(() =>
    this.actions$.pipe(
      ofType(
        AdminUsersActions.opened,
        AdminUsersActions.reloaded,
        AdminUsersActions.refreshed,
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
      mergeMap(({ id, organizationId, fromOrganizationId }) =>
        this.users
          .assignRole(id, {
            role: 'EMPLOYEE',
            organizationId,
            updateOrganization: true,
            ...(fromOrganizationId === undefined ? {} : { fromOrganizationId }),
          })
          .pipe(
            mergeMap(() => this.afterMembership(id)),
            catchError((error: unknown) => {
              const failed = AdminUsersActions.membershipConfirmFailed({
                id,
                problem: problemType(error),
              });
              return problemStatus(error) === HttpStatusCode.Conflict
                ? of(failed, AdminUsersActions.refreshed())
                : of(failed);
            }),
          ),
      ),
    ),
  );

  readonly linkMembership$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminUsersActions.membershipLinked),
      mergeMap(({ id, personId, version }) =>
        this.people.link(personId, id, version).pipe(
          mergeMap(() => this.afterMembership(id)),
          catchError((error: unknown) =>
            of(
              AdminUsersActions.membershipConfirmFailed({ id, problem: problemType(error) }),
              AdminUsersActions.refreshed(),
            ),
          ),
        ),
      ),
    ),
  );

  readonly loadUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminUsersActions.userOpened, AdminUsersActions.userReloaded),
      switchMap(({ id }) =>
        !Number.isSafeInteger(id) || id <= 0
          ? of(AdminUsersActions.userLoadFailed({ problem: 'unknown', notFound: true }))
          : this.users.get(id).pipe(
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

  /** The list reloads after a membership change; the pane only when it still shows that person. */
  private afterMembership(id: number): Observable<Action> {
    return this.store.select(adminUsersFeature.selectSelected).pipe(
      take(1),
      mergeMap((selected) =>
        selected?.id === id
          ? of(AdminUsersActions.reloaded(), AdminUsersActions.userReloaded({ id }))
          : of(AdminUsersActions.reloaded()),
      ),
    );
  }
}
