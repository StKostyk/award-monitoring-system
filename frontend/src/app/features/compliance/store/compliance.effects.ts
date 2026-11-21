import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Action } from '@ngrx/store';
import { Observable, catchError, map, merge, of, switchMap } from 'rxjs';

import { ReadProblem, readProblem } from '../../../core/api/problem';
import { ComplianceService } from '../compliance.service';
import { ComplianceActions, ComplianceSection } from './compliance.actions';

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

/** Number of runs the duration chart shows. */
export const RECENT_RUNS = 7;
const FIRST = { page: 0, size: 1 };

function count(section: ComplianceSection, source: Observable<number>): Observable<Action> {
  return source.pipe(
    map((value) => ComplianceActions.countLoaded({ section, count: value })),
    catchError(() => of(ComplianceActions.countLoadFailed({ section }))),
  );
}

@Injectable()
export class ComplianceEffects {
  private readonly actions$ = inject(Actions);
  private readonly compliance = inject(ComplianceService);

  /** The three side-column counts and the newest runs, each failing on its own. */
  readonly overview$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ComplianceActions.overviewOpened),
      switchMap(() =>
        merge(
          count('retention', this.compliance.policies().pipe(map((policies) => policies.length))),
          count(
            'erasures',
            this.compliance
              .erasureRequests(FIRST, 'PENDING')
              .pipe(map((page) => page.totalElements)),
          ),
          count(
            'holds',
            this.compliance.legalHolds(FIRST, true).pipe(map((page) => page.totalElements)),
          ),
          this.compliance.runs({ page: 0, size: RECENT_RUNS }).pipe(
            map((page) => ComplianceActions.recentRunsLoaded({ runs: page.content })),
            catchError(() => of(ComplianceActions.recentRunsLoadFailed())),
          ),
        ),
      ),
    ),
  );

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
