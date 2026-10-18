import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';

import { readProblem } from '../../../core/api/problem';
import { ComplianceService } from '../compliance.service';
import { ComplianceActions } from './compliance.actions';

@Injectable()
export class ComplianceEffects {
  private readonly actions$ = inject(Actions);
  private readonly compliance = inject(ComplianceService);

  readonly policies$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.retentionOpened),
      switchMap(() =>
        this.compliance.policies().pipe(
          map((policies) => ComplianceActions.policiesLoaded({ policies })),
          catchError((error: unknown) =>
            of(ComplianceActions.policiesLoadFailed({ problem: readProblem(error) })),
          ),
        ),
      ),
    ),
  );

  readonly runs$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.retentionOpened),
      switchMap(({ request }) =>
        this.compliance.runs(request).pipe(
          map((page) => ComplianceActions.runsLoaded({ page })),
          catchError((error: unknown) =>
            of(ComplianceActions.runsLoadFailed({ problem: readProblem(error) })),
          ),
        ),
      ),
    ),
  );

  readonly erasures$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.erasuresOpened),
      switchMap(({ request, status }) =>
        this.compliance.erasureRequests(request, status).pipe(
          map((page) => ComplianceActions.erasuresLoaded({ page })),
          catchError((error: unknown) =>
            of(ComplianceActions.erasuresLoadFailed({ problem: readProblem(error) })),
          ),
        ),
      ),
    ),
  );

  readonly holds$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.holdsOpened),
      switchMap(({ request, active }) =>
        this.compliance.legalHolds(request, active).pipe(
          map((page) => ComplianceActions.holdsLoaded({ page })),
          catchError((error: unknown) =>
            of(ComplianceActions.holdsLoadFailed({ problem: readProblem(error) })),
          ),
        ),
      ),
    ),
  );
}
