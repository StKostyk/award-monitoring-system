import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action, provideStore } from '@ngrx/store';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ComplianceService } from '../compliance.service';
import { ComplianceActions } from './compliance.actions';
import { ComplianceEffects } from './compliance.effects';

const request = { page: 0, size: 20 };
const empty = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };
const policies = [{ key: 'notifications', period: 'P365D', action: 'delete' as const, order: 1 }];

describe('ComplianceEffects', () => {
  let actions$: Subject<Action>;
  let effects: ComplianceEffects;
  let api: Record<'policies' | 'runs' | 'erasureRequests' | 'legalHolds', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    actions$ = new Subject<Action>();
    api = {
      policies: vi.fn().mockReturnValue(of(policies)),
      runs: vi.fn().mockReturnValue(of(empty)),
      erasureRequests: vi.fn().mockReturnValue(of(empty)),
      legalHolds: vi.fn().mockReturnValue(of(empty)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideStore(),
        provideMockActions(() => actions$),
        { provide: ComplianceService, useValue: api },
        ComplianceEffects,
      ],
    });
    effects = TestBed.inject(ComplianceEffects);
  });

  it('ac2_5_loads_the_policies_and_the_runs_when_the_retention_tab_opens', async () => {
    const loadedPolicies = firstValueFrom(effects.policies$);
    const loadedRuns = firstValueFrom(effects.runs$);
    actions$.next(ComplianceActions.retentionOpened({ request }));

    expect(await loadedPolicies).toEqual(ComplianceActions.policiesLoaded({ policies }));
    expect(await loadedRuns).toEqual(ComplianceActions.runsLoaded({ page: empty }));
    expect(api.runs).toHaveBeenCalledWith(request);
  });

  it('ac2_5_passes_the_filters_of_the_erasure_and_hold_tabs', async () => {
    const erasures = firstValueFrom(effects.erasures$);
    actions$.next(ComplianceActions.erasuresOpened({ request, status: 'COMPLETED' }));
    const holds = firstValueFrom(effects.holds$);
    actions$.next(ComplianceActions.holdsOpened({ request, active: false }));

    expect(await erasures).toEqual(ComplianceActions.erasuresLoaded({ page: empty }));
    expect(await holds).toEqual(ComplianceActions.holdsLoaded({ page: empty }));
    expect(api.erasureRequests).toHaveBeenCalledWith(request, 'COMPLETED');
    expect(api.legalHolds).toHaveBeenCalledWith(request, false);
  });

  it('ac2_6_reports_a_failed_or_refused_load_as_a_read_problem', async () => {
    api.runs.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    api.erasureRequests.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    api.legalHolds.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    api.policies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const runs = firstValueFrom(effects.runs$);
    const policiesResult = firstValueFrom(effects.policies$);
    actions$.next(ComplianceActions.retentionOpened({ request }));
    const erasures = firstValueFrom(effects.erasures$);
    actions$.next(ComplianceActions.erasuresOpened({ request, status: null }));
    const holds = firstValueFrom(effects.holds$);
    actions$.next(ComplianceActions.holdsOpened({ request, active: true }));

    expect(await runs).toEqual(ComplianceActions.runsLoadFailed({ problem: 'failed' }));
    expect(await policiesResult).toEqual(
      ComplianceActions.policiesLoadFailed({ problem: 'failed' }),
    );
    expect(await erasures).toEqual(ComplianceActions.erasuresLoadFailed({ problem: 'denied' }));
    expect(await holds).toEqual(ComplianceActions.holdsLoadFailed({ problem: 'failed' }));
  });
});
