import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SwUpdate } from '@angular/service-worker';
import { TranslocoService } from '@jsverse/transloco';
import { filter, interval } from 'rxjs';

/** How often an open app asks the server for a new version, besides at start and when the tab becomes visible. */
export const UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;

/** Set before a reload forced by a broken worker state, so the reloaded page still tells the user why. */
export const RELOADED_KEY = 'awards.update-reloaded';

/**
 * Offers a new release of the application once the service worker has downloaded it; the user decides when to
 * reload, so a form being filled in is never interrupted.
 */
@Injectable({ providedIn: 'root' })
export class UpdatePromptService {
  private readonly updates = inject(SwUpdate);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  /** Reloads the page; replaced in tests. */
  reload = (): void => this.document.location.reload();

  init(): void {
    if (!this.updates.isEnabled) {
      return;
    }
    if (this.takeReloadedFlag()) {
      this.snackBar.open(this.transloco.translate('app.update.available'), undefined, {
        duration: 6000,
      });
    }
    this.updates.versionUpdates
      .pipe(
        filter((event) => event.type === 'VERSION_READY'),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.offer());
    this.updates.unrecoverable.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.setReloadedFlag();
      this.reload();
    });
    this.check();
    interval(UPDATE_CHECK_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.check());
    this.document.addEventListener('visibilitychange', () => {
      if (this.document.visibilityState === 'visible') {
        this.check();
      }
    });
  }

  private offer(): void {
    this.snackBar
      .open(
        this.transloco.translate('app.update.available'),
        this.transloco.translate('app.update.action'),
      )
      .onAction()
      .subscribe(() => {
        this.updates.activateUpdate().finally(() => this.reload());
      });
  }

  private check(): void {
    this.updates.checkForUpdate().catch(() => undefined);
  }

  private takeReloadedFlag(): boolean {
    try {
      const reloaded = sessionStorage.getItem(RELOADED_KEY) !== null;
      sessionStorage.removeItem(RELOADED_KEY);
      return reloaded;
    } catch {
      return false;
    }
  }

  private setReloadedFlag(): void {
    try {
      sessionStorage.setItem(RELOADED_KEY, '1');
    } catch {
      // storage disabled: the reload happens without the notice
    }
  }
}
