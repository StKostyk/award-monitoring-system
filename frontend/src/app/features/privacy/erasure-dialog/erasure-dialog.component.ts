import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import {
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';

import { knownProblem, problemType } from '../../../core/api/problem';
import { ErasureRequest, PrivacyCentreService } from '../privacy-centre.service';

const PASSWORD_MAX_LENGTH = 72;

/** Refusals with a message of their own; the rest read as `unknown`. */
const KNOWN_PROBLEMS = [
  'password-mismatch',
  'account-locked',
  'erasure-pending',
  'legal-hold',
  'last-administrator',
  'network',
];

/** Asks for the erasure of the own account with the password and a confirmation box. */
@Component({
  selector: 'app-erasure-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatButton,
    MatCheckbox,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatProgressBar,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'privacy.erasure.dialog.title' | transloco }}</h2>
    <mat-dialog-content class="erasure">
      <p class="erasure__text">{{ 'privacy.erasure.dialog.text' | transloco }}</p>
      <mat-form-field class="erasure__password" subscriptSizing="dynamic">
        <mat-label>{{ 'privacy.erasure.dialog.password' | transloco }}</mat-label>
        <input
          matInput
          type="password"
          autocomplete="current-password"
          required
          [formControl]="password"
          [maxlength]="passwordMaxLength"
          data-testid="erasure-password"
        />
        @if (password.hasError('required')) {
          <mat-error>{{ 'privacy.erasure.dialog.passwordRequired' | transloco }}</mat-error>
        }
      </mat-form-field>
      <mat-checkbox [formControl]="understood" data-testid="erasure-understood">
        {{ 'privacy.erasure.dialog.understood' | transloco }}
      </mat-checkbox>
      @if (understood.touched && understood.invalid) {
        <p class="erasure__problem" role="alert" data-testid="erasure-understood-error">
          {{ 'privacy.erasure.dialog.understoodRequired' | transloco }}
        </p>
      }
      @if (problem(); as shown) {
        <p class="erasure__problem" role="alert" data-testid="erasure-dialog-error">
          {{ 'privacy.erasure.problems.' + shown | transloco }}
        </p>
      }
      @if (sending()) {
        <mat-progress-bar
          mode="indeterminate"
          [attr.aria-label]="'privacy.erasure.dialog.sending' | transloco"
        />
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button
        mat-button
        type="button"
        [disabled]="sending()"
        (click)="close()"
        data-testid="erasure-dialog-cancel"
      >
        {{ 'privacy.erasure.dialog.cancel' | transloco }}
      </button>
      <button
        mat-flat-button
        type="button"
        class="erasure__confirm"
        [disabled]="sending()"
        (click)="confirm()"
        data-testid="erasure-dialog-confirm"
      >
        {{ 'privacy.erasure.dialog.confirm' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .erasure {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .erasure__text {
      margin: 0;
    }

    .erasure__password {
      width: 100%;
    }

    .erasure__problem {
      margin: 0;
      color: var(--mat-sys-error);
    }

    .erasure__confirm {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErasureDialogComponent {
  private readonly centre = inject(PrivacyCentreService);
  private readonly ref = inject<MatDialogRef<ErasureDialogComponent, ErasureRequest>>(MatDialogRef);

  readonly passwordMaxLength = PASSWORD_MAX_LENGTH;
  readonly password = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(PASSWORD_MAX_LENGTH)],
  });
  readonly understood = new FormControl(false, {
    nonNullable: true,
    validators: [Validators.requiredTrue],
  });
  readonly sending = signal(false);
  readonly problem = signal<string | null>(null);

  /** Sends the request and keeps the dialog open until the answer. */
  confirm(): void {
    if (this.password.invalid || this.understood.invalid) {
      this.password.markAsTouched();
      this.understood.markAsTouched();
      return;
    }
    this.sending.set(true);
    this.problem.set(null);
    this.ref.disableClose = true;
    this.centre.requestErasure(this.password.value).subscribe({
      next: (request) => this.ref.close(request),
      error: (error: unknown) => {
        this.sending.set(false);
        this.ref.disableClose = false;
        this.password.reset();
        this.problem.set(knownProblem(problemType(error), KNOWN_PROBLEMS));
      },
    });
  }

  close(): void {
    this.ref.close();
  }
}
