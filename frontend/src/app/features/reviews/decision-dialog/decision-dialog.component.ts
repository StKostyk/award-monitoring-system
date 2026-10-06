import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
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

import { ApprovalLevel } from '../../awards/awards.service';
import { DecisionType } from '../reviews.service';

/** Longest comment the server accepts. */
export const COMMENT_MAX_LENGTH = 2000;

/** The decision to confirm, the level an escalation goes to and whether the award has documents. */
export interface DecisionDialogData {
  decision: DecisionType;
  target: ApprovalLevel | null;
  documents: number;
}

/** What the reviewer entered; the dialog closes with nothing when cancelled. */
export interface DecisionInput {
  comment?: string;
  verified?: boolean;
}

/** Confirms a review decision with a comment, required for a return or a rejection. */
@Component({
  selector: 'app-decision-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    MatCheckbox,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title data-testid="decision-title">
      {{
        'reviews.decide.title.' + data.decision
          | transloco: { level: ('reviews.decide.to.' + data.target | transloco) }
      }}
    </h2>
    <mat-dialog-content class="decision">
      <mat-form-field class="decision__comment" subscriptSizing="dynamic">
        <mat-label>{{
          (commentRequired ? 'reviews.decide.commentRequired' : 'reviews.decide.comment')
            | transloco
        }}</mat-label>
        <textarea
          matInput
          rows="4"
          [formControl]="comment"
          [maxlength]="maxLength"
          data-testid="decision-comment"
        ></textarea>
        <mat-hint>{{ 'reviews.decide.commentHint' | transloco }}</mat-hint>
        @if (comment.hasError('required')) {
          <mat-error>{{ 'reviews.decide.required' | transloco }}</mat-error>
        }
        @if (comment.hasError('maxlength')) {
          <mat-error>{{ 'reviews.decide.tooLong' | transloco }}</mat-error>
        }
      </mat-form-field>
      @if (data.decision === 'APPROVE' && data.documents > 0) {
        <mat-checkbox [formControl]="verified" data-testid="decision-verified">
          {{ 'reviews.decide.verified' | transloco }}
        </mat-checkbox>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close data-testid="decision-cancel">
        {{ 'reviews.decide.cancel' | transloco }}
      </button>
      <button mat-flat-button type="button" (click)="confirm()" data-testid="decision-confirm">
        {{ 'reviews.decide.confirm.' + data.decision | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .decision {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .decision__comment {
      width: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionDialogComponent {
  readonly data = inject<DecisionDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<DecisionDialogComponent, DecisionInput>>(MatDialogRef);

  readonly maxLength = COMMENT_MAX_LENGTH;
  readonly commentRequired = this.data.decision === 'RETURN' || this.data.decision === 'REJECT';
  readonly comment = new FormControl('', {
    nonNullable: true,
    validators: this.commentRequired
      ? [Validators.required, notBlank, Validators.maxLength(COMMENT_MAX_LENGTH)]
      : [Validators.maxLength(COMMENT_MAX_LENGTH)],
  });
  readonly verified = new FormControl(false, { nonNullable: true });

  confirm(): void {
    if (this.comment.invalid) {
      this.comment.markAsTouched();
      return;
    }
    const comment = this.comment.value.trim();
    this.ref.close({
      ...(comment ? { comment } : {}),
      ...(this.verified.value ? { verified: true } : {}),
    });
  }
}

function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() ? null : { required: true };
}
