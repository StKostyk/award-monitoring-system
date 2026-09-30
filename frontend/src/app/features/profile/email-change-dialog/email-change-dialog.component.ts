import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { ProfileService } from '../profile.service';

const KNOWN_PROBLEMS = [
  'password-mismatch',
  'institutional-email-required',
  'email-taken',
  'too-many-requests',
  'network',
];

@Component({
  selector: 'app-email-change-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './email-change-dialog.component.html',
  styles: `
    .email-change__form { display: flex; flex-direction: column; min-width: 280px; }
    .email-change__problem { color: var(--mat-sys-error, #b3261e); margin: 0; }
  `,
})
export class EmailChangeDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProfileService);

  protected readonly dialog = inject<MatDialogRef<EmailChangeDialogComponent, string>>(MatDialogRef);
  readonly current = inject<{ email: string }>(MAT_DIALOG_DATA).email;
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    newEmail: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    currentPassword: ['', [Validators.required, Validators.maxLength(72)]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const newEmail = this.form.controls.newEmail.value.trim().toLowerCase();
    if (newEmail === this.current.toLowerCase()) {
      this.error.set('emailChange.errors.unchanged');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.requestEmailChange(newEmail, this.form.controls.currentPassword.value).subscribe({
      next: () => this.dialog.close(newEmail),
      error: (err: unknown) => {
        const type = problemType(err);
        this.error.set(
          type === 'validation-failed'
            ? 'emailChange.errors.unchanged'
            : `emailChange.errors.${KNOWN_PROBLEMS.includes(type) ? type : 'failed'}`,
        );
        this.form.controls.currentPassword.reset();
        this.submitting.set(false);
      },
    });
  }
}
