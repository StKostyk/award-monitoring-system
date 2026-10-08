import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';

import { ApprovalLevel } from '../../awards/awards.service';
import { DecisionType, ReviewTemplate, ReviewsService } from '../reviews.service';

/** Longest comment the server accepts. */
export const COMMENT_MAX_LENGTH = 2000;

/**
 * The decision to confirm, the level an escalation goes to (null: the next level of each award) and whether the
 * award has documents; `count` is the number of awards of a batch.
 */
export interface DecisionDialogData {
  decision: DecisionType;
  target: ApprovalLevel | null;
  documents: number;
  count?: number;
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
    MatSelect,
    MatOption,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title data-testid="decision-title">
      {{
        'reviews.decide.title.' + data.decision
          | transloco: { level: ('reviews.decide.to.' + (data.target ?? 'higher') | transloco) }
      }}
    </h2>
    <mat-dialog-content class="decision">
      @if (data.count) {
        <p class="decision__count" data-testid="decision-count">
          {{ 'reviews.batch.count' | transloco: { count: data.count } }}
        </p>
      }
      @if (templates().length > 0) {
        <mat-form-field class="decision__comment" subscriptSizing="dynamic">
          <mat-label>{{ 'reviews.decide.template' | transloco }}</mat-label>
          <mat-select
            [formControl]="template"
            (selectionChange)="pick($event.value)"
            data-testid="decision-template"
          >
            @for (option of templates(); track option.id) {
              <mat-option [value]="option.id">{{ option.title }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
      @if (pending(); as replacing) {
        <div
          class="decision__replace"
          role="alertdialog"
          aria-labelledby="decision-replace"
          data-testid="decision-replace"
        >
          <span id="decision-replace">{{ 'reviews.decide.replace' | transloco }}</span>
          <button mat-button type="button" (click)="keep()" data-testid="decision-replace-no">
            {{ 'reviews.decide.replaceNo' | transloco }}
          </button>
          <button
            mat-flat-button
            type="button"
            (click)="fill(replacing)"
            data-testid="decision-replace-yes"
          >
            {{ 'reviews.decide.replaceYes' | transloco }}
          </button>
        </div>
      }
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
      @if (data.decision === 'APPROVE' && data.documents > 0 && !data.count) {
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

    .decision__count {
      margin: 0;
      font: var(--mat-sys-title-small);
    }

    .decision__replace {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border-radius: 8px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
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
  readonly template = new FormControl<number | null>(null);
  readonly templates = toSignal(
    inject(ReviewsService)
      .templates(this.data.decision)
      .pipe(catchError(() => of<ReviewTemplate[]>([]))),
    { initialValue: [] },
  );
  /** A template waiting for the reviewer to confirm that it may replace their edited text. */
  readonly pending = signal<ReviewTemplate | null>(null);
  private filled: { id: number; body: string } | null = null;

  /** Fills the comment with a template, asking first when that would replace text the reviewer wrote. */
  pick(id: number): void {
    const chosen = this.templates().find((option) => option.id === id);
    if (!chosen) {
      return;
    }
    const text = this.comment.value.trim();
    if (text && text !== this.filled?.body.trim()) {
      this.pending.set(chosen);
    } else {
      this.fill(chosen);
    }
  }

  fill(chosen: ReviewTemplate): void {
    this.comment.setValue(chosen.body);
    this.comment.markAsDirty();
    this.filled = { id: chosen.id, body: chosen.body };
    this.template.setValue(chosen.id);
    this.pending.set(null);
  }

  keep(): void {
    this.template.setValue(this.filled?.id ?? null);
    this.pending.set(null);
  }

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
