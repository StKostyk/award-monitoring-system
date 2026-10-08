import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { ReviewPeriod, ReviewsService } from '../reviews.service';

export const MIN_WORKING_DAYS = 1;
export const MAX_WORKING_DAYS = 20;

const KNOWN_PROBLEMS = ['validation-failed', 'network'];

/** Changes the review period of a faculty; closes with the period after the change, or nothing when cancelled. */
@Component({
  selector: 'app-review-period-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    MatFormField,
    MatLabel,
    MatInput,
    MatHint,
    MatError,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'reviews.period.dialog.title' | transloco }}</h2>
    <mat-dialog-content>
      <mat-form-field class="review-period__field" subscriptSizing="dynamic">
        <mat-label>{{ 'reviews.period.dialog.label' | transloco }}</mat-label>
        <input
          matInput
          type="number"
          inputmode="numeric"
          [min]="min"
          [max]="max"
          step="1"
          [formControl]="days"
          (keydown.enter)="submit()"
          data-testid="review-period-input"
        />
        <mat-hint>{{ 'reviews.period.dialog.hint' | transloco }}</mat-hint>
        @if (days.invalid) {
          <mat-error data-testid="review-period-range">{{
            'reviews.period.dialog.range' | transloco
          }}</mat-error>
        }
      </mat-form-field>
      @if (problem(); as problem) {
        <p class="review-period__problem" role="alert" data-testid="review-period-error">
          {{ 'reviews.period.problems.' + problem | transloco }}
        </p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close data-testid="review-period-cancel">
        {{ 'reviews.period.dialog.cancel' | transloco }}
      </button>
      <button
        mat-button
        type="button"
        [disabled]="busy() || data.workingDays === null"
        (click)="restoreDefault()"
        data-testid="review-period-default"
      >
        {{ 'reviews.period.dialog.reset' | transloco }}
      </button>
      <button
        mat-flat-button
        type="button"
        [disabled]="busy()"
        (click)="submit()"
        data-testid="review-period-save"
      >
        {{ 'reviews.period.dialog.save' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .review-period__field {
      width: 100%;
    }

    .review-period__problem {
      color: var(--mat-sys-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewPeriodDialogComponent {
  protected readonly data = inject<ReviewPeriod>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ReviewPeriodDialogComponent, ReviewPeriod>);
  private readonly reviews = inject(ReviewsService);

  protected readonly min = MIN_WORKING_DAYS;
  protected readonly max = MAX_WORKING_DAYS;
  readonly days = new FormControl<number | null>(this.data.effectiveWorkingDays, [
    Validators.required,
    Validators.min(MIN_WORKING_DAYS),
    Validators.max(MAX_WORKING_DAYS),
    Validators.pattern(/^\d+$/),
  ]);
  readonly busy = signal(false);
  readonly problem = signal<string | null>(null);

  /** Saves the entered period once it is a whole number from 1 to 20. */
  submit(): void {
    if (this.days.invalid) {
      this.days.markAsTouched();
      return;
    }
    this.save(this.days.value);
  }

  /** Restores the global default. */
  restoreDefault(): void {
    this.save(null);
  }

  private save(workingDays: number | null): void {
    this.busy.set(true);
    this.problem.set(null);
    this.reviews.setReviewPeriod(this.data.organizationId, workingDays).subscribe({
      next: (period) => this.dialogRef.close(period),
      error: (error: unknown) => {
        const type = problemType(error);
        this.busy.set(false);
        this.problem.set(KNOWN_PROBLEMS.includes(type) ? type : 'unknown');
      },
    });
  }
}
