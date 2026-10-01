import { HttpStatusCode } from '@angular/common/http';
import { computed, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { problemStatus } from '../core/api/problem';

/** Why a page could not be shown: a retry may help (`failed`) or cannot (`gone`, `denied`). */
export type PageProblem = 'failed' | 'gone' | 'denied';

/** The part of a page answer the list needs. */
export interface PageOf<T> {
  content: T[];
  totalPages: number;
}

/** Options of a paged list. */
export interface PagedListOptions<T, K> {
  /** Fetches one page by its index. */
  fetch: (page: number) => Observable<PageOf<T>>;
  /** Identifies an item, so a row pushed onto the next page by a new one is shown once. */
  key: (item: T) => K;
  /** Called with the new items of every page. */
  loaded?: (items: T[]) => void;
  /** A 404 means "nothing there" (an empty list) instead of "no longer available". */
  notFoundIsEmpty?: boolean;
}

/** A newest-first list read page by page with «show more», for history and audit views. */
export class PagedList<T, K = unknown> {
  readonly items = signal<T[]>([]);
  readonly loading = signal(false);
  readonly problem = signal<PageProblem | null>(null);
  private readonly loadedPages = signal(0);
  private readonly totalPages = signal(0);

  readonly hasMore = computed(
    () => this.problem() === null && this.loadedPages() < this.totalPages(),
  );

  constructor(private readonly options: PagedListOptions<T, K>) {}

  /** Loads the next page, or the same page again after a failure. */
  load(): void {
    this.loading.set(true);
    this.problem.set(null);
    this.options.fetch(this.loadedPages()).subscribe({
      next: (page) => {
        const known = new Set(this.items().map(this.options.key));
        const fresh = page.content.filter((item) => !known.has(this.options.key(item)));
        this.items.update((shown) => [...shown, ...fresh]);
        this.loadedPages.update((pages) => pages + 1);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
        this.options.loaded?.(fresh);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.problem.set(this.problemOf(problemStatus(error)));
      },
    });
  }

  /** Drops what is shown and loads the first page again. */
  reload(): void {
    this.items.set([]);
    this.loadedPages.set(0);
    this.totalPages.set(0);
    this.load();
  }

  private problemOf(status: number): PageProblem | null {
    if (status === HttpStatusCode.Forbidden) {
      return 'denied';
    }
    if (status === HttpStatusCode.NotFound) {
      return this.options.notFoundIsEmpty && this.loadedPages() === 0 ? null : 'gone';
    }
    return 'failed';
  }
}
