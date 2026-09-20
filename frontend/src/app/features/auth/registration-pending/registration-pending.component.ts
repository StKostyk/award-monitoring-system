import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { RegistrationService } from '../registration.service';

@Component({
  selector: 'app-registration-pending',
  imports: [
    ReactiveFormsModule,
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatFormField,
    MatLabel,
    MatInput,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './registration-pending.component.html',
  styleUrl: './registration-pending.component.scss',
})
export class RegistrationPendingComponent {
  private readonly api = inject(RegistrationService);
  private readonly auth = inject(AuthService);

  readonly email = new FormControl(inject(ActivatedRoute).snapshot.queryParamMap.get('email') ?? '', {
    nonNullable: true,
    validators: [Validators.required, Validators.email],
  });
  readonly notice = signal<string | null>(null);
  readonly sending = signal(false);

  resend(): void {
    if (this.email.invalid || this.sending()) {
      this.email.markAsTouched();
      return;
    }
    this.sending.set(true);
    this.api.resendVerification(this.email.value.trim()).subscribe({
      next: () => {
        this.notice.set('pending.resent');
        this.sending.set(false);
      },
      error: (err) => {
        this.notice.set(`pending.errors.${problemType(err)}`);
        this.sending.set(false);
      },
    });
  }

  signIn(): void {
    this.auth.login('/');
  }
}
