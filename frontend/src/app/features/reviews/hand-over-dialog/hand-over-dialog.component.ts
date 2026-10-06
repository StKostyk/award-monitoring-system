import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { ReviewerCandidate, ReviewsService } from '../reviews.service';

/** The award whose request is handed over. */
export interface HandOverDialogData {
  awardId: number;
}

/** Picks the colleague a request is handed over to; closes with the colleague, or nothing when cancelled. */
@Component({
  selector: 'app-hand-over-dialog',
  imports: [
    FormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    MatProgressBar,
    MatRadioGroup,
    MatRadioButton,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title id="hand-over-title">{{ 'reviews.handOver.title' | transloco }}</h2>
    <mat-dialog-content>
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (problem(); as problem) {
        <p class="hand-over__problem" role="alert" data-testid="hand-over-error">
          {{ 'reviews.problems.' + problem | transloco }}
        </p>
      }
      @if (!loading() && !problem() && candidates().length === 0) {
        <p data-testid="hand-over-empty">{{ 'reviews.handOver.empty' | transloco }}</p>
      }
      <mat-radio-group
        class="hand-over__list"
        aria-labelledby="hand-over-title"
        [(ngModel)]="selected"
        data-testid="hand-over-list"
      >
        @for (candidate of candidates(); track candidate.id) {
          <mat-radio-button [value]="candidate" data-testid="hand-over-candidate">
            {{ candidate.name }}
            @if (candidate.delegated) {
              <span class="hand-over__delegated"
                >({{ 'reviews.handOver.delegated' | transloco }})</span
              >
            }
          </mat-radio-button>
        }
      </mat-radio-group>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close data-testid="hand-over-cancel">
        {{ 'reviews.handOver.cancel' | transloco }}
      </button>
      <button
        mat-flat-button
        type="button"
        [disabled]="!selected()"
        [mat-dialog-close]="selected()"
        data-testid="hand-over-confirm"
      >
        {{ 'reviews.handOver.confirm' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .hand-over__list {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .hand-over__delegated {
      color: var(--mat-sys-on-surface-variant);
    }

    .hand-over__problem {
      color: var(--mat-sys-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HandOverDialogComponent implements OnInit {
  private readonly data = inject<HandOverDialogData>(MAT_DIALOG_DATA);
  private readonly reviews = inject(ReviewsService);

  readonly candidates = signal<ReviewerCandidate[]>([]);
  readonly selected = signal<ReviewerCandidate | null>(null);
  readonly loading = signal(true);
  readonly problem = signal<string | null>(null);

  ngOnInit(): void {
    this.reviews.candidates(this.data.awardId).subscribe({
      next: (candidates) => {
        this.candidates.set(candidates);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.problem.set(problemType(error));
      },
    });
  }
}
