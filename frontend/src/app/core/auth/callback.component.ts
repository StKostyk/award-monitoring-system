import { Component, OnInit, inject } from '@angular/core';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from './auth.service';

@Component({
  selector: 'app-callback',
  imports: [MatProgressSpinner, TranslocoPipe],
  template: `
    <div class="callback">
      <mat-spinner diameter="40" />
      <p>{{ 'app.loading' | transloco }}</p>
    </div>
  `,
  styles: `
    .callback {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      padding-top: 20vh;
    }
  `,
})
export class CallbackComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    const target = this.auth.isAuthenticated() ? this.auth.targetUrl() : '/';
    void this.router.navigateByUrl(target);
  }
}
