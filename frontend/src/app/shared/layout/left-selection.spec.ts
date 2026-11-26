import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { watchLeftSelection } from './left-selection';

function failing(status: number): Observable<never> {
  return throwError(() => new HttpErrorResponse({ status }));
}

describe('watchLeftSelection', () => {
  function setUp(initial: { id: number | null; ids: number[] }) {
    const selectedId = signal<number | null>(initial.id);
    const ids = signal<readonly number[]>(initial.ids);
    const settled = signal(true);
    const check = vi.fn((): Observable<unknown> => failing(HttpStatusCode.NotFound));
    const gone = vi.fn();
    TestBed.runInInjectionContext(() =>
      watchLeftSelection({ selectedId, ids, settled, check }, gone),
    );
    TestBed.tick();
    return { selectedId, ids, settled, check, gone };
  }

  function reload(state: ReturnType<typeof setUp>, ids: number[]): void {
    state.settled.set(false);
    TestBed.tick();
    state.ids.set(ids);
    state.settled.set(true);
    TestBed.tick();
  }

  it('reports a selection that a reload no longer holds and the server no longer shows', () => {
    const state = setUp({ id: 5, ids: [4, 5] });

    reload(state, [4]);

    expect(state.check).toHaveBeenCalledWith(5);
    expect(state.gone).toHaveBeenCalledTimes(1);
  });

  it('reports a selection the caller may no longer see', () => {
    const state = setUp({ id: 5, ids: [4, 5] });
    state.check.mockReturnValue(failing(HttpStatusCode.Forbidden));

    reload(state, [4]);

    expect(state.gone).toHaveBeenCalledTimes(1);
  });

  it('keeps a selection that only moved to another page or out of the filter', () => {
    const state = setUp({ id: 5, ids: [4, 5] });
    state.check.mockReturnValue(of({}));

    reload(state, [4]);

    expect(state.gone).not.toHaveBeenCalled();
  });

  it('keeps the selection when the check fails for another reason', () => {
    const state = setUp({ id: 5, ids: [4, 5] });
    state.check.mockReturnValue(failing(HttpStatusCode.ServiceUnavailable));

    reload(state, [4]);

    expect(state.gone).not.toHaveBeenCalled();
  });

  it('keeps quiet while the selection stays in the list', () => {
    const state = setUp({ id: 5, ids: [4, 5] });

    reload(state, [5, 6]);

    expect(state.check).not.toHaveBeenCalled();
  });

  it('ignores a selection the list never held, e.g. a link to another page', () => {
    const state = setUp({ id: 9, ids: [4, 5] });

    reload(state, [4]);

    expect(state.check).not.toHaveBeenCalled();
  });

  it('ignores a row removed locally without a reload, e.g. decided by the reviewer', () => {
    const state = setUp({ id: 5, ids: [4, 5] });

    state.ids.set([4]);
    TestBed.tick();

    expect(state.check).not.toHaveBeenCalled();
  });

  it('waits until the list has settled', () => {
    const state = setUp({ id: 5, ids: [4, 5] });

    state.settled.set(false);
    state.ids.set([]);
    TestBed.tick();

    expect(state.check).not.toHaveBeenCalled();
  });

  it('stays quiet when the selection changed before the answer came', () => {
    const state = setUp({ id: 5, ids: [4, 5] });
    state.check.mockImplementation(() => {
      state.selectedId.set(4);
      return failing(HttpStatusCode.NotFound);
    });

    reload(state, [4]);

    expect(state.gone).not.toHaveBeenCalled();
  });

  it('starts over after the selection changed', () => {
    const state = setUp({ id: 5, ids: [4, 5] });

    state.selectedId.set(4);
    TestBed.tick();
    reload(state, [5]);

    expect(state.check).toHaveBeenCalledWith(4);
    expect(state.gone).toHaveBeenCalledTimes(1);
  });
});
