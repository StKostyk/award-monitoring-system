import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { consumeLinkToken } from '../../auth/link-token';
import { ProfileService } from '../profile.service';

export type ConfirmationState = 'confirming' | 'changed' | 'taken' | 'invalid' | 'failed';

@Component({
  selector: 'app-confirm-email-change',
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatButton, TranslocoPipe],
  templateUrl: './confirm-email-change.component.html',
})
export class ConfirmEmailChangeComponent {
  private readonly api = inject(ProfileService);
  private readonly auth = inject(AuthService);
  private readonly token = consumeLinkToken();

  readonly state = signal<ConfirmationState>(this.token ? 'confirming' : 'invalid');
  readonly email = signal('');

  constructor() {
    if (!this.token) {
      return;
    }
    this.api.confirmEmailChange(this.token).subscribe({
      next: (response) => {
        this.email.set(response.email);
        this.state.set('changed');
        this.auth.signedOutElsewhere();
      },
      error: (err: unknown) => {
        const type = problemType(err);
        if (type === 'token-invalid') {
          this.state.set('invalid');
        } else {
          this.state.set(type === 'email-taken' ? 'taken' : 'failed');
        }
      },
    });
  }

  signIn(): void {
    this.auth.login('/profile');
  }
}
