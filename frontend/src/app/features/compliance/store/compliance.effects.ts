import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Action } from '@ngrx/store';
import { Observable, catchError, map, of, switchMap } from 'rxjs';

import { ReadProblem, readProblem } from '../../../core/api/problem';
import { ComplianceService } from '../compliance.service';
import { ComplianceActions } from './compliance.actions';

function load<T>(
  source: Observable<T>,
  loaded: (value: T) => Action,
  failed: (problem: ReadProblem) => Action,
): Observable<Action> {
  return source.pipe(
    map(loaded),
    catchError((error: unknown) => of(failed(readProblem(error)))),
  );
}

@Injectable()
export class ComplianceEffects {
  private readonly actions$ = inject(Actions);
  private readonly compliance = inject(ComplianceService);

  readonly policies$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.retentionOpened),
      switchMap(() =>
        load(
          this.compliance.policies(),
          (policies) => ComplianceActions.policiesLoaded({ policies }),
          (problem) => ComplianceActions.policiesLoadFailed({ problem }),
        ),
      ),
    ),
  );

  readonly runs$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.retentionOpened),
      switchMap(({ request }) =>
        load(
          this.compliance.runs(request),
          (page) => ComplianceActions.runsLoaded({ page }),
          (problem) => ComplianceActions.runsLoadFailed({ problem }),
        ),
      ),
    ),
  );

  readonly erasures$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.erasuresOpened),
      switchMap(({ request, status }) =>
        load(
          this.compliance.erasureRequests(request, status),
          (page) => ComplianceActions.erasuresLoaded({ page }),
          (problem) => ComplianceActions.erasuresLoadFailed({ problem }),
        ),
      ),
    ),
  );

  readonly holds$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.holdsOpened),
      switchMap(({ request, active }) =>
        load(
          this.compliance.legalHolds(request, active),
          (page) => ComplianceActions.holdsLoaded({ page }),
          (problem) => ComplianceActions.holdsLoadFailed({ problem }),
        ),
      ),
    ),
  );
}
