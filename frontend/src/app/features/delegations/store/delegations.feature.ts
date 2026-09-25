import { createFeature, createReducer, on } from '@ngrx/store';

import { Delegation } from '../delegations.service';
import { DelegationsActions } from './delegations.actions';

export interface RevokeProblem {
  id: number;
  problem: string;
}

export interface DelegationsState {
  given: Delegation[];
  received: Delegation[];
  loading: boolean;
  problem: string | null;
  revokingId: number | null;
  revokeProblem: RevokeProblem | null;
  message: string | null;
}

export const initialState: DelegationsState = {
  given: [],
  received: [],
  loading: false,
  problem: null,
  revokingId: null,
  revokeProblem: null,
  message: null,
};

export const delegationsFeature = createFeature({
  name: 'delegations',
  reducer: createReducer(
    initialState,
    on(DelegationsActions.opened, (state) => ({
      ...state,
      loading: true,
      problem: null,
      message: null,
    })),
    on(DelegationsActions.reloaded, (state) => ({
      ...state,
      loading: true,
      problem: null,
      revokingId: null,
      revokeProblem: null,
    })),
    on(DelegationsActions.delegationsLoaded, (state, { list }) => ({
      ...state,
      given: list.given,
      received: list.received,
      loading: false,
      problem: null,
    })),
    on(DelegationsActions.delegationsLoadFailed, (state, { problem }) => ({
      ...state,
      given: [],
      received: [],
      loading: false,
      problem,
    })),
    on(DelegationsActions.created, (state) => ({
      ...state,
      message: 'delegations.messages.created',
      revokeProblem: null,
    })),
    on(DelegationsActions.revokeRequested, (state, { id }) => ({
      ...state,
      revokingId: id,
      revokeProblem: null,
      message: null,
    })),
    on(DelegationsActions.revoked, (state) => ({
      ...state,
      revokingId: null,
      message: 'delegations.messages.revoked',
    })),
    on(DelegationsActions.revokeFailed, (state, { id, problem }) => ({
      ...state,
      revokingId: null,
      revokeProblem: { id, problem },
      message: null,
    })),
  ),
});
