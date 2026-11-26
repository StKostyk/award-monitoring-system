import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { AwardFilters, AwardPage } from '../awards.service';

export const AwardsActions = createActionGroup({
  source: 'Awards',
  events: {
    Opened: emptyProps(),
    'Filters Changed': props<{ filters: AwardFilters }>(),
    'Page Changed': props<{ pageIndex: number; pageSize: number }>(),
    'Awards Loaded': props<{ page: AwardPage }>(),
    'Awards Load Failed': props<{ problem: string }>(),
  },
});
