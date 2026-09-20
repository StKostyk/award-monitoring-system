import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { RegistrationService } from '../registration.service';

export type VerificationState = 'checking' | 'verified' | 'invalid' | 'failed';

@Component({
  selector: 'app-verify-email',
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatButton, MatProgressSpinner, RouterLink, TranslocoPipe],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(RegistrationService);
  private readonly auth = inject(AuthService);

  readonly state = signal<VerificationState>('checking');
  readonly email = signal('');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('invalid');
      return;
    }
    this.api.verifyEmail(token).subscribe({
      next: (response) => {
        this.email.set(response.email);
        this.state.set('verified');
      },
      error: (err) => this.state.set(problemType(err) === 'token-invalid' ? 'invalid' : 'failed'),
    });
  }

  signIn(): void {
    this.auth.login('/');
  }
}
