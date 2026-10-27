import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

const HEADING_WAIT_MS = 3000;
const HEADINGS = 'h1, h2, mat-card-title';
const OPEN_DIALOG = '.cdk-overlay-container .mat-mdc-dialog-surface';

/**
 * Moves the focus to the page heading after every navigation to another path, so that keyboard and screen
 * reader users start on the new page instead of the link they used. The first load, changes of the query only,
 * an open dialog and a focus the user has already moved are left alone.
 */
@Injectable({ providedIn: 'root' })
export class NavigationFocusService {
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private path: string | null = null;
  private watching: MutationObserver | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;

  /**
   * Follows the navigations of the router while the caller lives.
   *
   * @param main the element that holds the pages
   * @param destroyRef the lifetime of the caller
   */
  follow(main: () => HTMLElement | undefined, destroyRef: DestroyRef): void {
    destroyRef.onDestroy(() => this.stopWatching());
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((event) => {
        const path = event.urlAfterRedirects.split(/[?#]/)[0];
        const first = this.path === null;
        const moved = path !== this.path;
        this.path = path;
        const target = main();
        if (!first && moved && target && !this.document.querySelector(OPEN_DIALOG)) {
          this.focusPage(target);
        }
      });
  }

  /**
   * Gives an element the focus without making it a stop of the Tab order.
   *
   * @param element the element to focus
   */
  focusWithoutTabStop(element: HTMLElement): void {
    if (!element.hasAttribute('tabindex')) {
      element.setAttribute('tabindex', '-1');
    }
    element.focus();
  }

  private focusPage(main: HTMLElement): void {
    this.stopWatching();
    const before = this.document.activeElement;
    let focused: HTMLElement | null = null;
    const userMoved = (): boolean => {
      const active = this.document.activeElement;
      return active !== before && active !== focused && active !== this.document.body;
    };
    const place = (): void => {
      const heading = main.querySelector<HTMLElement>(HEADINGS);
      if (heading && heading !== focused) {
        focused = heading;
        this.focusWithoutTabStop(heading);
      }
    };
    place();
    this.watching = new MutationObserver(() => {
      if (userMoved()) {
        this.stopWatching();
      } else if (!focused?.isConnected || this.document.activeElement === this.document.body) {
        focused = null;
        place();
      }
    });
    this.watching.observe(main, { childList: true, subtree: true });
    this.timer = setTimeout(() => {
      this.stopWatching();
      if (!focused?.isConnected && !userMoved()) {
        this.focusWithoutTabStop(main);
      }
    }, HEADING_WAIT_MS);
  }

  private stopWatching(): void {
    this.watching?.disconnect();
    this.watching = null;
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
