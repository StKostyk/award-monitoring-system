import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, mergeMap, of, switchMap } from 'rxjs';

import { problemType } from '../../../core/api/problem';
import { DelegationsService } from '../delegations.service';
import { DelegationsActions } from './delegations.actions';

@Injectable()
export class DelegationsEffects {
  private readonly actions$ = inject(Actions);
  private readonly delegations = inject(DelegationsService);

  readonly load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DelegationsActions.opened, DelegationsActions.reloaded),
      switchMap(() =>
        this.delegations.list().pipe(
          map((list) => DelegationsActions.delegationsLoaded({ list })),
          catchError((error: unknown) =>
            of(DelegationsActions.delegationsLoadFailed({ problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );

  readonly revoke$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DelegationsActions.revokeRequested),
      mergeMap(({ id }) =>
        this.delegations.revoke(id).pipe(
          map(() => DelegationsActions.revoked({ id })),
          catchError((error: unknown) =>
            of(DelegationsActions.revokeFailed({ id, problem: problemType(error) })),
          ),
        ),
      ),
    ),
  );

  readonly refresh$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DelegationsActions.created, DelegationsActions.revoked),
      map(() => DelegationsActions.reloaded()),
    ),
  );
}
