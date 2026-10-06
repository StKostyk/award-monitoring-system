import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { Page } from '../../awards/awards.service';
import { ReviewFilters, ReviewItem } from '../reviews.service';

export const ReviewsActions = createActionGroup({
  source: 'Reviews',
  events: {
    Opened: emptyProps(),
    'Filters Changed': props<{ filters: ReviewFilters }>(),
    'Page Changed': props<{ pageIndex: number; pageSize: number }>(),
    'Reviews Loaded': props<{ page: Page<ReviewItem> }>(),
    'Reviews Load Failed': props<{ problem: string }>(),
  },
});
