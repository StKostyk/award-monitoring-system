import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../../core/api/problem';
import { consumeLinkToken } from '../../link-token';
import { RegistrationService } from '../../registration.service';

export type NotMeState = 'confirm' | 'done' | 'invalid';

@Component({
  selector: 'app-not-me',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatButton,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './not-me.component.html',
  styleUrl: './not-me.component.scss',
})
export class NotMeComponent {
  private readonly api = inject(RegistrationService);
  private readonly token = consumeLinkToken();

  readonly state = signal<NotMeState>(this.token ? 'confirm' : 'invalid');
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  confirm(): void {
    if (this.submitting() || !this.token) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api.revokeAccess(this.token).subscribe({
      next: () => this.state.set('done'),
      error: (err) => {
        const type = problemType(err);
        if (type === 'token-invalid') {
          this.state.set('invalid');
        } else {
          this.error.set(`notMe.errors.${type}`);
        }
        this.submitting.set(false);
      },
    });
  }
}
