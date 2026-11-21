import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { NavigationStart, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { Subject, filter, firstValueFrom, fromEvent } from 'rxjs';

import { environment } from '../../../environments/environment';
import { problemStatus, problemType } from '../../core/api/problem';
import { AuthService } from '../../core/auth/auth.service';
import { SignInNoticeService } from '../../core/auth/sign-in-notice.service';
import { ConnectivityService } from '../../core/connectivity/connectivity.service';
import { reviewProblemKey, reviewerOf } from './review-problems';
import { DecisionOutcome, DecisionRequest, ReviewsService } from './reviews.service';

/** How long a confirmed decision waits for «Скасувати» before it is sent, in milliseconds. */
export const HOLD_MILLIS = 10_000;
const UNAUTHORIZED = 401;
const FORBIDDEN = 403;
const RELOADING = ['request-claimed', 'request-stale', 'request-closed'];

/** A held decision that came back to the queue: undone (no problem) or refused once sent. */
export interface ReturnedDecision {
  awardId: number;
  /** Translation key of the reason, null after «Скасувати». */
  problem: string | null;
  params: Record<string, string>;
}

interface Held {
  request: DecisionRequest;
  /** The account that decided; the decision is never sent with another account's token. */
  owner: string | null;
  timer: ReturnType<typeof setTimeout>;
  snackBar: MatSnackBarRef<TextOnlySnackBar>;
}

/**
 * Holds a decision confirmed in the review queue for ten seconds so that «Скасувати» can take it back, then sends
 * it. Held decisions go out at once when the person moves to another page or signs out, and with a keepalive request
 * when the tab closes; nothing is reverted on the server, because nothing reaches it before the hold ends.
 */
@Injectable({ providedIn: 'root' })
export class HeldDecisionService {
  private readonly reviews = inject(ReviewsService);
  private readonly auth = inject(AuthService);
  private readonly connectivity = inject(ConnectivityService);
  private readonly notice = inject(SignInNoticeService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly held = new Map<number, Held>();
  private readonly returnedSubject = new Subject<ReturnedDecision>();
  private readonly sentSubject = new Subject<DecisionOutcome>();
  private readonly sending = new Set<Promise<void>>();
  private readonly lost = new Set<number>();

  /** The awards whose decision is held now. */
  readonly heldIds = signal<ReadonlySet<number>>(new Set());
  /** Decisions that came back to the queue. */
  readonly returned = this.returnedSubject.asObservable();
  /** Decisions the server accepted. */
  readonly sent = this.sentSubject.asObservable();

  constructor() {
    const destroyRef = inject(DestroyRef);
    const router = inject(Router);
    router.events
      .pipe(
        filter((event) => event instanceof NavigationStart),
        filter((event) => pathOf(event.url) !== pathOf(router.url)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => void this.flush());
    fromEvent<PageTransitionEvent>(window, 'pagehide')
      .pipe(
        filter((event) => !event.persisted),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.sendOnUnload());
    this.auth.beforeSignOut(() => this.flush());
  }

  /**
   * Holds a confirmed decision and offers «Скасувати» for the hold.
   *
   * @param awardId the award decided
   * @param request the decision as it will be sent
   */
  hold(awardId: number, request: DecisionRequest): void {
    this.cancel(awardId);
    const snackBar = this.snackBar.open(
      this.transloco.translate('reviews.queue.held'),
      this.transloco.translate('reviews.queue.undo'),
      { duration: HOLD_MILLIS },
    );
    snackBar.onAction().subscribe(() => this.undo(awardId));
    const timer = setTimeout(() => void this.send(awardId), HOLD_MILLIS);
    this.held.set(awardId, { request, owner: this.auth.userId(), timer, snackBar });
    this.publish();
  }

  /**
   * Takes a held decision back; nothing is sent.
   *
   * @param awardId the award decided
   */
  undo(awardId: number): void {
    if (this.cancel(awardId)) {
      this.returnedSubject.next({ awardId, problem: null, params: {} });
    }
  }

  /** Sends every held decision now and waits for the answers of all decisions on their way. */
  async flush(): Promise<void> {
    [...this.held.keys()].forEach((awardId) => void this.send(awardId));
    await Promise.all([...this.sending]);
  }

  private send(awardId: number): Promise<void> {
    const held = this.held.get(awardId);
    if (!held) {
      return Promise.resolve();
    }
    this.cancel(awardId);
    const sending = this.deliver(awardId, held).finally(() => this.sending.delete(sending));
    this.sending.add(sending);
    return sending;
  }

  private async deliver(awardId: number, { request, owner }: Held): Promise<void> {
    if (!this.connectivity.online()) {
      const waiting = this.snackBar.open(this.transloco.translate('reviews.queue.waiting'));
      await firstValueFrom(this.connectivity.reconnected$);
      waiting.dismiss();
    }
    if (this.auth.userId() !== owner) {
      return;
    }
    try {
      this.sentSubject.next(await firstValueFrom(this.reviews.decide(awardId, request)));
    } catch (error: unknown) {
      if (owner && this.signedOut(error)) {
        this.lostWith(owner, awardId);
        return;
      }
      this.refused(awardId, error);
    }
  }

  /** The session ended before the decision reached the server: the next sign-in of the owner names the award. */
  private lostWith(owner: string, awardId: number): void {
    this.lost.add(awardId);
    this.notice.leave(owner, 'reviews.queue.signedOut', { id: [...this.lost].join(', ') });
  }

  /** The send was refused because the session ended (revoked role or delegation); the page goes to sign-in. */
  private signedOut(error: unknown): boolean {
    return problemStatus(error) === UNAUTHORIZED && !this.auth.isAuthenticated();
  }

  private refused(awardId: number, error: unknown): void {
    const type = problemType(error);
    let problem = reviewProblemKey(type);
    if (problemStatus(error) === FORBIDDEN) {
      problem = 'reviews.queue.denied';
    } else if (type === 'network') {
      problem = 'reviews.queue.offline';
    } else if (RELOADING.includes(type)) {
      problem = `reviews.problems.${type}`;
    }
    const params = { name: reviewerOf(error)?.name ?? '' };
    this.snackBar.open(this.transloco.translate(problem, params), undefined, {
      duration: HOLD_MILLIS,
    });
    this.returnedSubject.next({ awardId, problem, params });
  }

  /** The tab is closing: every held decision goes out with a request the browser finishes on its own. */
  private sendOnUnload(): void {
    const token = this.auth.accessToken();
    for (const [awardId, held] of this.held) {
      this.cancel(awardId);
      if (held.owner && !this.auth.isAuthenticated()) {
        this.lostWith(held.owner, awardId);
        continue;
      }
      if (this.auth.userId() !== held.owner) {
        continue;
      }
      void fetch(`${environment.apiUrl}/awards/${awardId}/decisions`, {
        method: 'POST',
        keepalive: true,
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(held.request),
      }).catch(() => undefined);
    }
  }

  private cancel(awardId: number): boolean {
    const held = this.held.get(awardId);
    if (!held) {
      return false;
    }
    clearTimeout(held.timer);
    held.snackBar.dismiss();
    this.held.delete(awardId);
    this.publish();
    return true;
  }

  private publish(): void {
    this.heldIds.set(new Set(this.held.keys()));
  }
}

function pathOf(url: string): string {
  return url.split(/[?#]/)[0];
}
