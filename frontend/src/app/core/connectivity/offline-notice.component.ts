import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

import { ConnectivityService } from './connectivity.service';

/** The notice above every page while the server cannot be reached. */
@Component({
  selector: 'app-offline-notice',
  imports: [MatIcon, TranslocoPipe],
  template: `
    @if (!connectivity.online()) {
      <div class="offline-notice" role="status" data-testid="offline-notice">
        <mat-icon aria-hidden="true">cloud_off</mat-icon>
        <span>{{ 'app.offlineNotice' | transloco }}</span>
      </div>
    }
  `,
  styles: `
    .offline-notice {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);

      mat-icon {
        flex: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OfflineNoticeComponent {
  protected readonly connectivity = inject(ConnectivityService);
}
