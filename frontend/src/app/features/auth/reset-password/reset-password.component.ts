import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { PASSWORD_VALIDATORS } from '../password-rules';
import { consumeLinkToken } from '../link-token';
import { RegistrationService } from '../registration.service';

export type ResetState = 'form' | 'done' | 'invalid';

@Component({
  selector: 'app-reset-password',
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
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent {
  private readonly api = inject(RegistrationService);
  private readonly auth = inject(AuthService);
  private readonly token = consumeLinkToken();

  readonly password = new FormControl('', { nonNullable: true, validators: PASSWORD_VALIDATORS });
  readonly state = signal<ResetState>(this.token ? 'form' : 'invalid');
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  submit(): void {
    if (this.password.invalid || this.submitting() || !this.token) {
      this.password.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.confirmPasswordReset(this.token, this.password.value).subscribe({
      next: () => this.state.set('done'),
      error: (err) => {
        const type = problemType(err);
        if (type === 'token-invalid') {
          this.state.set('invalid');
        } else {
          this.error.set(`reset.errors.${type}`);
        }
        this.submitting.set(false);
      },
    });
  }

  signIn(): void {
    this.auth.login('/');
  }
}
