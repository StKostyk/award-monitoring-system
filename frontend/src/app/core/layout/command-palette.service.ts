import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, Injector, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { MatDialogRef } from '@angular/material/dialog';
import { filter, fromEvent } from 'rxjs';

import { AuthService } from '../auth/auth.service';

/** Opens the command palette from the toolbar or from Ctrl K (Cmd K on macOS). */
@Injectable({ providedIn: 'root' })
export class CommandPaletteService {
  private readonly injector = inject(Injector);
  private readonly auth = inject(AuthService);
  private readonly document = inject(DOCUMENT);
  private opening = false;
  private palette: MatDialogRef<unknown> | null = null;

  /**
   * Opens the palette on Ctrl K or Cmd K until the caller is destroyed.
   *
   * @param destroyRef the lifetime of the listener
   */
  listen(destroyRef: DestroyRef): void {
    fromEvent<KeyboardEvent>(this.document, 'keydown')
      .pipe(
        filter((event) =>
          isShortcut(event, APPLE.test(this.document.defaultView?.navigator.userAgent ?? '')),
        ),
        filter(() => this.mayOpen() && !this.document.querySelector('mat-dialog-container')),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((event) => {
        event.preventDefault();
        void this.open();
      });
  }

  /**
   * Opens the palette for a signed-in person, unless it or another dialog is already open; the palette code loads on
   * the first use.
   *
   * @return resolves once the palette is open or the request is ignored
   */
  async open(): Promise<void> {
    if (!this.mayOpen()) {
      return;
    }
    this.opening = true;
    try {
      const [{ MatDialog }, { CommandPaletteComponent }] = await Promise.all([
        import('@angular/material/dialog'),
        import('./command-palette/command-palette.component'),
      ]);
      const dialog = this.injector.get(MatDialog);
      if (dialog.openDialogs.length > 0) {
        return;
      }
      this.palette = dialog.open(CommandPaletteComponent, {
        panelClass: 'command-palette',
        width: 'min(640px, calc(100vw - 32px))',
        maxWidth: 'calc(100vw - 32px)',
        position: { top: '12vh' },
        autoFocus: 'first-tabbable',
        restoreFocus: true,
      });
      this.palette.afterClosed().subscribe(() => (this.palette = null));
    } catch {
      this.palette = null;
    } finally {
      this.opening = false;
    }
  }

  private mayOpen(): boolean {
    return this.auth.isAuthenticated() && !this.opening && !this.palette;
  }
}

/** Apple devices, where the shortcut is Cmd K and Ctrl K stays the text-editing key. */
export const APPLE = /Mac|iPhone|iPad/;

function isShortcut(event: KeyboardEvent, apple: boolean): boolean {
  const key = event.code === 'KeyK' || event.key?.toLowerCase() === 'k';
  const modifier = apple ? event.metaKey && !event.ctrlKey : event.ctrlKey && !event.metaKey;
  return key && modifier && !event.altKey && !event.shiftKey;
}
