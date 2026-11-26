import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/**
 * Whether the server can be reached: the browser's connection events combined with the outcome of backend
 * requests, so a connection that is up but does not reach the server counts as offline as well.
 */
@Injectable({ providedIn: 'root' })
export class ConnectivityService {
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly restored = new Subject<void>();

  readonly online = signal(this.window?.navigator.onLine ?? true);

  /** Emits each time the connection returns after it was lost. */
  readonly reconnected$: Observable<void> = this.restored.asObservable();

  constructor() {
    this.window?.addEventListener('online', () => this.reachable());
    this.window?.addEventListener('offline', () => this.unreachable());
  }

  /** Whether the browser itself has a connection; a server that does not answer is not considered. */
  linked(): boolean {
    return this.window?.navigator.onLine ?? true;
  }

  /**
   * A backend request was answered: the server is reachable, unless the browser has lost its connection since the
   * request was sent.
   */
  reachable(): void {
    if (!this.online() && this.linked()) {
      this.online.set(true);
      this.restored.next();
    }
  }

  /** A backend request got no answer at all, or the browser lost its connection. */
  unreachable(): void {
    this.online.set(false);
  }
}
