import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { consumeLinkToken } from '../link-token';
import { RegistrationService } from '../registration.service';

export type VerificationState = 'form' | 'verified' | 'invalid' | 'failed';

@Component({
  selector: 'app-verify-email',
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
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent {
  private readonly api = inject(RegistrationService);
  private readonly auth = inject(AuthService);
  private readonly token = consumeLinkToken();

  readonly password = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly state = signal<VerificationState>(this.token ? 'form' : 'invalid');
  readonly email = signal('');
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  submit(): void {
    if (this.password.invalid || this.submitting() || !this.token) {
      this.password.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.verifyEmail(this.token, this.password.value).subscribe({
      next: (response) => {
        this.email.set(response.email);
        this.state.set('verified');
      },
      error: (err) => {
        const type = problemType(err);
        if (type === 'password-mismatch') {
          this.error.set('verify.errors.password-mismatch');
        } else {
          this.state.set(type === 'token-invalid' ? 'invalid' : 'failed');
        }
        this.submitting.set(false);
      },
    });
  }

  signIn(): void {
    this.auth.login('/');
  }
}
