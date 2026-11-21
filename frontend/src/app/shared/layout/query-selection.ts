import { inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

/** The item a split page shows, kept in one query parameter of the URL. */
export interface QuerySelection {
  /** The chosen id, null when none is or the parameter is not a positive whole number. */
  readonly id: Signal<number | null>;

  /**
   * Shows an item and keeps it in the URL; null goes back to the list. Other parameters stay.
   *
   * @param id the id to show
   */
  select(id: number | null): void;
}

/**
 * Reads a positive whole-number id from a query parameter value.
 *
 * @param value the parameter value
 * @returns the id, or null when absent or not a positive whole number
 */
export function positiveIdOf(value: string | null): number | null {
  const id = value && /^\d{1,15}$/.test(value) ? Number(value) : 0;
  return id > 0 ? id : null;
}

/**
 * Binds the selection of a split page to a query parameter of the current route.
 * Call it in an injection context, e.g. a field initialiser.
 *
 * @param key the query parameter, e.g. `request`
 * @returns the selected id and a way to change it
 */
export function querySelection(key: string): QuerySelection {
  const route = inject(ActivatedRoute);
  const router = inject(Router);
  const id = toSignal(route.queryParamMap.pipe(map((params) => positiveIdOf(params.get(key)))), {
    initialValue: positiveIdOf(route.snapshot.queryParamMap.get(key)),
  });
  return {
    id,
    select: (next) =>
      void router.navigate([], {
        relativeTo: route,
        queryParams: { [key]: next },
        queryParamsHandling: 'merge',
      }),
  };
}
