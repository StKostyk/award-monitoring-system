import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../core/auth/auth.service';
import { ConnectivityService } from '../../core/connectivity/connectivity.service';
import { localPath } from '../../shared/local-path';

/** Shown instead of the sign-in when there is no session and no connection; signs in once the connection returns. */
@Component({
  selector: 'app-offline',
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatButton, TranslocoPipe],
  templateUrl: './offline.component.html',
  styles: `
    .offline__card {
      max-width: 560px;
    }
    .offline__title {
      margin: 0;
      font: inherit;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OfflineComponent {
  private readonly auth = inject(AuthService);
  private readonly target = localPath(inject(ActivatedRoute).snapshot.queryParamMap.get('next'));

  constructor() {
    inject(ConnectivityService)
      .reconnected$.pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.retry());
  }

  retry(): void {
    this.auth.login(this.target);
  }
}
