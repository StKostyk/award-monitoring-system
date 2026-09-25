import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action, provideStore } from '@ngrx/store';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { Delegation, DelegationList, DelegationsService } from '../delegations.service';
import { DelegationsActions } from './delegations.actions';
import { DelegationsEffects } from './delegations.effects';

const delegation: Delegation = {
  id: 1,
  role: 'DEAN',
  organization: {
    id: 9,
    name: 'Faculty of Mathematics',
    nameUk: 'Факультет математики',
    code: 'FMI',
    type: 'FACULTY',
  },
  delegator: { id: 2, firstName: 'Мартин', lastName: 'Мартинюк', email: 'dean.fmi@chnu.edu.ua' },
  delegate: { id: 3, firstName: 'Аліна', lastName: 'Секретар', email: 'secretary.fmi@chnu.edu.ua' },
  validFrom: '2026-09-24',
  validTo: '2026-10-08',
  reason: null,
  state: 'active',
  createdAt: '2026-09-24T08:00:00Z',
  revokedAt: null,
};

const list: DelegationList = { given: [delegation], received: [] };

function problem(type: string, status: number): HttpErrorResponse {
  return new HttpErrorResponse({ error: { type: `urn:awards:problem:${type}` }, status });
}

describe('DelegationsEffects', () => {
  let actions$: Subject<Action>;
  let effects: DelegationsEffects;
  let api: { list: ReturnType<typeof vi.fn>; revoke: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    actions$ = new Subject<Action>();
    api = { list: vi.fn().mockReturnValue(of(list)), revoke: vi.fn().mockReturnValue(of(undefined)) };
    TestBed.configureTestingModule({
      providers: [
        provideStore(),
        provideMockActions(() => actions$),
        { provide: DelegationsService, useValue: api },
        DelegationsEffects,
      ],
    });
    effects = TestBed.inject(DelegationsEffects);
  });

  it('ac3_5_loads_the_delegations_given_and_received', async () => {
    const result = firstValueFrom(effects.load$);
    actions$.next(DelegationsActions.opened());

    expect(await result).toEqual(DelegationsActions.delegationsLoaded({ list }));
    expect(api.list).toHaveBeenCalledWith();
  });

  it('reports a failed load as a typed problem', async () => {
    api.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const result = firstValueFrom(effects.load$);
    actions$.next(DelegationsActions.opened());

    expect(await result).toEqual(DelegationsActions.delegationsLoadFailed({ problem: 'network' }));
  });

  it('ac3_4_revokes_a_delegation', async () => {
    const result = firstValueFrom(effects.revoke$);
    actions$.next(DelegationsActions.revokeRequested({ id: 1 }));

    expect(await result).toEqual(DelegationsActions.revoked({ id: 1 }));
    expect(api.revoke).toHaveBeenCalledWith(1);
  });

  it('ac3_4_keeps_a_refused_revocation_on_its_own_row', async () => {
    api.revoke.mockReturnValue(throwError(() => problem('delegation-not-active', 409)));
    const result = firstValueFrom(effects.revoke$);
    actions$.next(DelegationsActions.revokeRequested({ id: 1 }));

    expect(await result).toEqual(
      DelegationsActions.revokeFailed({ id: 1, problem: 'delegation-not-active' }),
    );
  });

  it('ac3_5_reloads_the_lists_after_a_change', async () => {
    const created = firstValueFrom(effects.refresh$);
    actions$.next(DelegationsActions.created({ delegation }));

    expect(await created).toEqual(DelegationsActions.reloaded());

    const revoked = firstValueFrom(effects.refresh$);
    actions$.next(DelegationsActions.revoked({ id: 1 }));

    expect(await revoked).toEqual(DelegationsActions.reloaded());
  });
});
