import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { combineLatest, take } from 'rxjs';

import { AuthService } from './auth.service';

const STORAGE_KEY = 'award.signInNotice';

interface Notice {
  owner: string;
  key: string;
  params: Record<string, string>;
}

/**
 * Carries a message across the sign-in page, which the app does not render: a notice left while the session ends
 * shows once after the same account signs in again in this tab, and is dropped for any other account.
 */
@Injectable({ providedIn: 'root' })
export class SignInNoticeService {
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  /**
   * Leaves a notice for the next sign-in of an account.
   *
   * @param owner the account the notice is for
   * @param key translation key of the message
   * @param params values of the message
   */
  leave(owner: string, key: string, params: Record<string, string>): void {
    const notice: Notice = { owner, key, params };
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(notice));
    } catch {
      // storage may be unavailable in private mode; the notice is then lost
    }
  }

  /** Shows the notice left for the signed-in account, once. */
  showPending(): void {
    const notice = this.take();
    if (!notice || notice.owner !== this.auth.userId()) {
      return;
    }
    combineLatest([
      this.transloco.selectTranslate(notice.key, notice.params),
      this.transloco.selectTranslate('reviews.queue.gotIt'),
    ])
      .pipe(take(1))
      .subscribe(([message, action]) => this.snackBar.open(message, action));
  }

  private take(): Notice | null {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      sessionStorage.removeItem(STORAGE_KEY);
      return stored ? (JSON.parse(stored) as Notice) : null;
    } catch {
      return null;
    }
  }
}
