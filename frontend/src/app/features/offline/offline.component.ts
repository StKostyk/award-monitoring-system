import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../core/auth/auth.service';
import { ConnectivityService } from '../../core/connectivity/connectivity.service';
import { IconComponent } from '../../shared/icons/icon.component';
import { localPath } from '../../shared/local-path';
import { StatusPageComponent } from '../../shared/status-page/status-page.component';

/** Shown instead of the sign-in when there is no session and no connection; signs in once the connection returns. */
@Component({
  selector: 'app-offline',
  imports: [MatButton, TranslocoPipe, StatusPageComponent, IconComponent],
  templateUrl: './offline.component.html',
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
