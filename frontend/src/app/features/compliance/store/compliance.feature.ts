import { createFeature, createReducer, on } from '@ngrx/store';

import { ReadProblem } from '../../../core/api/problem';
import { Page } from '../../../shared/page';
import { ErasureEntry, HoldEntry, RetentionPolicy, RetentionRun } from '../compliance.service';
import { ComplianceActions, ComplianceSection } from './compliance.actions';

/** One paged list of the compliance page. */
export interface ListState<T> {
  items: T[];
  total: number;
  loading: boolean;
  problem: ReadProblem | null;
}

export interface ComplianceState {
  policies: RetentionPolicy[];
  policiesLoading: boolean;
  policiesProblem: ReadProblem | null;
  runs: ListState<RetentionRun>;
  erasures: ListState<ErasureEntry>;
  holds: ListState<HoldEntry>;
  /** Side-column counts by section; null while unknown or when the count could not be read. */
  counts: Record<ComplianceSection, number | null>;
  /** The newest retention runs for the duration chart, newest first. */
  recentRuns: RetentionRun[];
}

const EMPTY_LIST = { items: [], total: 0, loading: false, problem: null };
const NO_COUNTS = { retention: null, erasures: null, holds: null };

export const initialState: ComplianceState = {
  policies: [],
  policiesLoading: false,
  policiesProblem: null,
  runs: EMPTY_LIST,
  erasures: EMPTY_LIST,
  holds: EMPTY_LIST,
  counts: NO_COUNTS,
  recentRuns: [],
};

function loading<T>(list: ListState<T>): ListState<T> {
  return { ...list, loading: true, problem: null };
}

function loaded<T>(page: Page<T>): ListState<T> {
  return { items: page.content, total: page.totalElements, loading: false, problem: null };
}

function failed<T>(problem: ReadProblem): ListState<T> {
  return { items: [], total: 0, loading: false, problem };
}

export const complianceFeature = createFeature({
  name: 'compliance',
  reducer: createReducer(
    initialState,
    on(ComplianceActions.overviewOpened, (state) => ({
      ...state,
      counts: NO_COUNTS,
      recentRuns: [],
    })),
    on(ComplianceActions.countLoaded, (state, { section, count }) => ({
      ...state,
      counts: { ...state.counts, [section]: count },
    })),
    on(ComplianceActions.countLoadFailed, (state, { section }) => ({
      ...state,
      counts: { ...state.counts, [section]: null },
    })),
    on(ComplianceActions.recentRunsLoaded, (state, { runs }) => ({ ...state, recentRuns: runs })),
    on(ComplianceActions.recentRunsLoadFailed, (state) => ({ ...state, recentRuns: [] })),
    on(ComplianceActions.retentionOpened, (state) => ({
      ...state,
      policiesLoading: true,
      policiesProblem: null,
      runs: loading(state.runs),
    })),
    on(ComplianceActions.policiesLoaded, (state, { policies }) => ({
      ...state,
      policies: [...policies].sort((a, b) => a.order - b.order),
      policiesLoading: false,
    })),
    on(ComplianceActions.policiesLoadFailed, (state, { problem }) => ({
      ...state,
      policies: [],
      policiesLoading: false,
      policiesProblem: problem,
    })),
    on(ComplianceActions.runsLoaded, (state, { page }) => ({ ...state, runs: loaded(page) })),
    on(ComplianceActions.runsLoadFailed, (state, { problem }) => ({
      ...state,
      runs: failed<RetentionRun>(problem),
    })),
    on(ComplianceActions.erasuresOpened, (state) => ({
      ...state,
      erasures: loading(state.erasures),
    })),
    on(ComplianceActions.erasuresLoaded, (state, { page }) => ({
      ...state,
      erasures: loaded(page),
    })),
    on(ComplianceActions.erasuresLoadFailed, (state, { problem }) => ({
      ...state,
      erasures: failed<ErasureEntry>(problem),
    })),
    on(ComplianceActions.holdsOpened, (state) => ({ ...state, holds: loading(state.holds) })),
    on(ComplianceActions.holdsLoaded, (state, { page }) => ({ ...state, holds: loaded(page) })),
    on(ComplianceActions.holdsLoadFailed, (state, { problem }) => ({
      ...state,
      holds: failed<HoldEntry>(problem),
    })),
  ),
});
