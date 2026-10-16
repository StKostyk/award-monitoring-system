import { HttpStatusCode } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable } from 'rxjs';

import { knownProblem, problemStatus, problemType } from '../../../core/api/problem';
import { HOLD_REASON_MAX_LENGTH, HOLD_REASON_MIN_LENGTH, LegalHold } from '../legal-holds.service';

/** What the dialog does, whose account it concerns and how it sends the reason. */
export interface LegalHoldDialogData {
  mode: 'place' | 'lift';
  name: string;
  submit: (reason: string) => Observable<LegalHold>;
}

/** The answer of the dialog: the saved hold, `stale` when the shown holds are out of date, or nothing. */
export type LegalHoldDialogResult = LegalHold | 'stale' | undefined;

/** Problems with a message of their own; the rest read as `unknown`. */
const KNOWN_PROBLEMS = [
  'legal-hold-active',
  'legal-hold-lifted',
  'legal-hold-not-found',
  'account-erased',
  'user-not-found',
  'validation-failed',
  'access-denied',
  'network',
];

/** Problems after which the shown holds no longer match the server. */
const FINAL_PROBLEMS = ['legal-hold-active', 'legal-hold-lifted', 'account-erased'];

/** Places or lifts a legal hold with a required reason and keeps the dialog open on a failure. */
@Component({
  selector: 'app-legal-hold-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatButton,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatProgressBar,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title data-testid="hold-dialog-title">
      {{ 'admin.detail.legalHold.dialog.title.' + data.mode | transloco }}
    </h2>
    <mat-dialog-content class="hold">
      <p class="hold__text">
        {{ 'admin.detail.legalHold.dialog.text.' + data.mode | transloco: { name: data.name } }}
      </p>
      <mat-form-field class="hold__reason" subscriptSizing="dynamic">
        <mat-label>{{ 'admin.detail.legalHold.dialog.reason' | transloco }}</mat-label>
        <textarea
          matInput
          rows="4"
          required
          [formControl]="reason"
          [maxlength]="maxLength"
          data-testid="hold-reason"
        ></textarea>
        <mat-hint>
          {{ 'admin.detail.legalHold.dialog.hint' | transloco: { min: minLength, max: maxLength } }}
        </mat-hint>
        @if (reason.hasError('required')) {
          <mat-error>{{ 'admin.detail.legalHold.dialog.required' | transloco }}</mat-error>
        } @else if (reason.hasError('tooShort')) {
          <mat-error>
            {{ 'admin.detail.legalHold.dialog.tooShort' | transloco: { min: minLength } }}
          </mat-error>
        } @else if (reason.hasError('maxlength')) {
          <mat-error>
            {{ 'admin.detail.legalHold.dialog.tooLong' | transloco: { max: maxLength } }}
          </mat-error>
        }
      </mat-form-field>
      @if (problem(); as shown) {
        <p class="hold__problem" role="alert" data-testid="hold-dialog-error">
          {{ 'admin.detail.legalHold.problems.' + shown | transloco }}
        </p>
      }
      @if (sending()) {
        <mat-progress-bar
          mode="indeterminate"
          [attr.aria-label]="'admin.detail.legalHold.dialog.sending' | transloco"
        />
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (stale()) {
        <button mat-flat-button type="button" (click)="close()" data-testid="hold-dialog-close">
          {{ 'admin.detail.legalHold.dialog.close' | transloco }}
        </button>
      } @else {
        <button
          mat-button
          type="button"
          [disabled]="sending()"
          (click)="close()"
          data-testid="hold-dialog-cancel"
        >
          {{ 'admin.detail.legalHold.dialog.cancel' | transloco }}
        </button>
        <button
          mat-flat-button
          type="button"
          [disabled]="sending()"
          (click)="confirm()"
          data-testid="hold-dialog-confirm"
        >
          {{ 'admin.detail.legalHold.dialog.confirm.' + data.mode | transloco }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .hold {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .hold__text {
      margin: 0;
    }

    .hold__reason {
      width: 100%;
    }

    .hold__problem {
      margin: 0;
      color: var(--mat-sys-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LegalHoldDialogComponent {
  readonly data = inject<LegalHoldDialogData>(MAT_DIALOG_DATA);
  private readonly ref =
    inject<MatDialogRef<LegalHoldDialogComponent, LegalHoldDialogResult>>(MatDialogRef);

  readonly minLength = HOLD_REASON_MIN_LENGTH;
  readonly maxLength = HOLD_REASON_MAX_LENGTH;
  readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [notBlank, longEnough, Validators.maxLength(HOLD_REASON_MAX_LENGTH)],
  });
  readonly sending = signal(false);
  readonly problem = signal<string | null>(null);
  readonly stale = signal(false);

  /** Sends the reason and keeps the dialog open until the answer. */
  confirm(): void {
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    this.sending.set(true);
    this.problem.set(null);
    this.ref.disableClose = true;
    this.data.submit(this.reason.value.trim()).subscribe({
      next: (hold) => this.ref.close(hold),
      error: (error: unknown) => {
        const type = problemType(error);
        this.sending.set(false);
        this.ref.disableClose = false;
        this.problem.set(knownProblem(type, KNOWN_PROBLEMS));
        this.stale.set(
          FINAL_PROBLEMS.includes(type) || problemStatus(error) === HttpStatusCode.NotFound,
        );
      },
    });
  }

  /** Closes without saving; after a final problem the caller reloads the holds. */
  close(): void {
    this.ref.close(this.stale() ? 'stale' : undefined);
  }
}

function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() ? null : { required: true };
}

function longEnough(control: AbstractControl<string>): ValidationErrors | null {
  const length = control.value.trim().length;
  return length === 0 || length >= HOLD_REASON_MIN_LENGTH ? null : { tooShort: true };
}
