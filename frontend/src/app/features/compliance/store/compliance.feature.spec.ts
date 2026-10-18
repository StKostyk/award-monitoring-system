import { RetentionRun } from '../compliance.service';
import { ComplianceActions } from './compliance.actions';
import { complianceFeature, initialState } from './compliance.feature';

const reducer = complianceFeature.reducer;
const request = { page: 0, size: 20 };
const run: RetentionRun = {
  id: 3,
  status: 'COMPLETED',
  startedAt: '2026-10-18T01:00:00Z',
  finishedAt: '2026-10-18T01:00:42Z',
  results: [],
};

describe('complianceFeature', () => {
  it('ac2_5_keeps_the_policies_in_run_order', () => {
    const state = reducer(
      initialState,
      ComplianceActions.policiesLoaded({
        policies: [
          { key: 'audit-logs', period: 'P7Y', action: 'drop-partitions', order: 9 },
          { key: 'notifications', period: 'P365D', action: 'delete', order: 1 },
        ],
      }),
    );

    expect(state.policies.map((policy) => policy.key)).toEqual(['notifications', 'audit-logs']);
  });

  it('ac2_6_shows_the_loading_state_then_the_page_or_the_problem', () => {
    const opened = reducer(initialState, ComplianceActions.retentionOpened({ request }));

    expect(opened.runs.loading).toBe(true);
    expect(opened.policiesLoading).toBe(true);

    const loaded = reducer(
      opened,
      ComplianceActions.runsLoaded({
        page: { content: [run], totalElements: 21, totalPages: 2, size: 20, number: 0 },
      }),
    );

    expect(loaded.runs).toEqual({ items: [run], total: 21, loading: false, problem: null });

    const failed = reducer(
      reducer(loaded, ComplianceActions.holdsOpened({ request, active: true })),
      ComplianceActions.holdsLoadFailed({ problem: 'failed' }),
    );

    expect(failed.holds).toEqual({ items: [], total: 0, loading: false, problem: 'failed' });
    expect(failed.runs.items).toEqual([run]);
  });

  it('ac2_6_clears_an_earlier_problem_when_the_list_is_read_again', () => {
    const failed = reducer(
      initialState,
      ComplianceActions.erasuresLoadFailed({ problem: 'failed' }),
    );
    const retried = reducer(failed, ComplianceActions.erasuresOpened({ request, status: null }));

    expect(retried.erasures.problem).toBeNull();
    expect(retried.erasures.loading).toBe(true);
    expect(
      reducer(initialState, ComplianceActions.policiesLoadFailed({ problem: 'denied' }))
        .policiesProblem,
    ).toBe('denied');
  });
});
