import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { DestroyRef, effect, inject, Signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';

/** What a split page shows the watch: its selection and the list beside it. */
export interface SelectionSource {
  /** The selected id, null when none is. */
  readonly selectedId: Signal<number | null>;
  /** The ids of the loaded list. */
  readonly ids: Signal<readonly number[]>;
  /** False while the list loads or after it failed. */
  readonly settled: Signal<boolean>;
  /** Reads the selected item on its own; a 404 or 403 means it is gone for the caller. */
  readonly check: (id: number) => Observable<unknown>;
}

const GONE: readonly number[] = [HttpStatusCode.NotFound, HttpStatusCode.Forbidden];

/**
 * Reports a selection that left its list: shown in the list once, missing after a later load of the list, and no
 * longer readable on its own, e.g. a request a colleague decided or a person moved out of scope. A selection the list
 * never held (a link to another page), a row only removed locally, and a row that moved to another page or out of the
 * filter stay unreported. Call it in an injection context.
 *
 * @param source the selection and the list
 * @param gone called once when the selection left the list
 */
export function watchLeftSelection(source: SelectionSource, gone: () => void): void {
  const destroyRef = inject(DestroyRef);
  let seen: number | null = null;
  let wasSettled = untracked(source.settled);
  effect(() => {
    const id = source.selectedId();
    const settled = source.settled();
    const reloaded = settled && !wasSettled;
    wasSettled = settled;
    if (id === null || !settled) {
      seen = id === seen ? seen : null;
      return;
    }
    if (source.ids().includes(id)) {
      seen = id;
    } else if (reloaded && seen === id) {
      untracked(() => confirmGone(source, id, destroyRef, gone));
    }
  });
}

function confirmGone(
  source: SelectionSource,
  id: number,
  destroyRef: DestroyRef,
  gone: () => void,
): void {
  source
    .check(id)
    .pipe(takeUntilDestroyed(destroyRef))
    .subscribe({
      error: (error: unknown) => {
        if (
          error instanceof HttpErrorResponse &&
          GONE.includes(error.status) &&
          source.selectedId() === id
        ) {
          gone();
        }
      },
    });
}
