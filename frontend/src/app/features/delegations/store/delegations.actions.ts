import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { Delegation, DelegationList } from '../delegations.service';

export const DelegationsActions = createActionGroup({
  source: 'Delegations',
  events: {
    Opened: emptyProps(),
    Reloaded: emptyProps(),
    'Delegations Loaded': props<{ list: DelegationList }>(),
    'Delegations Load Failed': props<{ problem: string }>(),
    Created: props<{ delegation: Delegation }>(),
    'Revoke Requested': props<{ id: number }>(),
    Revoked: props<{ id: number }>(),
    'Revoke Failed': props<{ id: number; problem: string }>(),
  },
});
