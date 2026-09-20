import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { RegistrationService } from '../registration.service';

@Component({
  selector: 'app-forgot-password',
  imports: [
    ReactiveFormsModule,
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class ForgotPasswordComponent {
  private readonly api = inject(RegistrationService);
  private readonly auth = inject(AuthService);

  readonly email = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] });
  readonly sent = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  submit(): void {
    if (this.email.invalid || this.submitting()) {
      this.email.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.requestPasswordReset(this.email.value.trim()).subscribe({
      next: () => this.sent.set(true),
      error: (err) => {
        this.error.set(`forgot.errors.${problemType(err)}`);
        this.submitting.set(false);
      },
    });
  }

  signIn(): void {
    this.auth.login('/');
  }
}
