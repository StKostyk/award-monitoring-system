import { Delegation, DelegationList } from '../delegations.service';
import { DelegationsActions } from './delegations.actions';
import { delegationsFeature, initialState } from './delegations.feature';

const faculty = {
  id: 9,
  name: 'Faculty of Mathematics',
  nameUk: 'Факультет математики',
  code: 'FMI',
  type: 'FACULTY' as const,
};

const dean = { id: 2, firstName: 'Мартин', lastName: 'Мартинюк', email: 'dean.fmi@chnu.edu.ua' };
const secretary = {
  id: 3,
  firstName: 'Аліна',
  lastName: 'Секретар',
  email: 'secretary.fmi@chnu.edu.ua',
};

const given: Delegation = {
  id: 1,
  role: 'DEAN',
  organization: faculty,
  delegator: dean,
  delegate: secretary,
  validFrom: '2026-09-24',
  validTo: '2026-10-08',
  reason: 'Відпустка',
  state: 'active',
  createdAt: '2026-09-24T08:00:00Z',
  revokedAt: null,
};

const list: DelegationList = { given: [given], received: [] };

const { reducer } = delegationsFeature;

describe('delegationsFeature', () => {
  it('ac3_5_keeps_the_two_lists_the_server_returned', () => {
    const loaded = reducer(
      reducer(initialState, DelegationsActions.opened()),
      DelegationsActions.delegationsLoaded({ list }),
    );

    expect(loaded.given).toEqual([given]);
    expect(loaded.received).toEqual([]);
    expect(loaded.loading).toBe(false);
    expect(loaded.problem).toBeNull();
  });

  it('ac3_5_empties_the_lists_when_the_request_fails', () => {
    const loaded = reducer(initialState, DelegationsActions.delegationsLoaded({ list }));

    const failed = reducer(loaded, DelegationsActions.delegationsLoadFailed({ problem: 'network' }));

    expect(failed.given).toEqual([]);
    expect(failed.received).toEqual([]);
    expect(failed.problem).toBe('network');
    expect(failed.loading).toBe(false);
  });

  it('ac3_1_reports_a_created_delegation', () => {
    const created = reducer(initialState, DelegationsActions.created({ delegation: given }));

    expect(created.message).toBe('delegations.messages.created');
  });

  it('ac3_4_holds_a_refused_revocation_on_its_own_row', () => {
    const requested = reducer(initialState, DelegationsActions.revokeRequested({ id: 1 }));

    expect(requested.revokingId).toBe(1);

    const failed = reducer(
      requested,
      DelegationsActions.revokeFailed({ id: 1, problem: 'delegation-not-active' }),
    );

    expect(failed.revokingId).toBeNull();
    expect(failed.revokeProblem).toEqual({ id: 1, problem: 'delegation-not-active' });
    expect(failed.message).toBeNull();
  });

  it('ac3_4_reports_a_revoked_delegation_and_clears_the_refusal_on_reload', () => {
    const revoked = reducer(
      reducer(initialState, DelegationsActions.revokeRequested({ id: 1 })),
      DelegationsActions.revoked({ id: 1 }),
    );

    expect(revoked.message).toBe('delegations.messages.revoked');

    const reloading = reducer(
      reducer(revoked, DelegationsActions.revokeFailed({ id: 1, problem: 'unknown' })),
      DelegationsActions.reloaded(),
    );

    expect(reloading.revokeProblem).toBeNull();
    expect(reloading.loading).toBe(true);
  });
});
