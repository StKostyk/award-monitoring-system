import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';

import { ReviewListComponent } from './review-list/review-list.component';
import { ReviewsEffects } from './store/reviews.effects';
import { reviewsFeature } from './store/reviews.feature';

export const REVIEW_ROUTES: Routes = [
  {
    path: '',
    providers: [provideState(reviewsFeature), provideEffects(ReviewsEffects)],
    children: [{ path: '', component: ReviewListComponent }],
  },
];
