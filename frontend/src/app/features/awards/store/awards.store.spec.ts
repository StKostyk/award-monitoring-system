import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { provideMockStore } from '@ngrx/store/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AwardFilters, AwardsService, NO_FILTERS } from '../awards.service';
import { AwardsActions } from './awards.actions';
import { AwardsEffects } from './awards.effects';
import { awardsFeature, initialState } from './awards.feature';

const page = { content: [], totalElements: 3, totalPages: 1, size: 100, number: 0 };

describe('awards feature', () => {
  it('ac1_7_keeps_the_filters_and_the_loaded_page', () => {
    const filters: AwardFilters = { ...NO_FILTERS, status: 'PENDING' };
    let state = awardsFeature.reducer(initialState, AwardsActions.filtersChanged({ filters }));
    expect(state).toMatchObject({ filters, loading: true });

    state = awardsFeature.reducer(state, AwardsActions.awardsLoaded({ page }));
    expect(state).toMatchObject({ total: 3, loading: false, problem: null });

    state = awardsFeature.reducer(state, AwardsActions.awardsLoadFailed({ problem: 'network' }));
    expect(state).toMatchObject({ awards: [], total: 0, problem: 'network' });
  });

  it('ac8_keeps_the_page_and_returns_to_the_first_one_when_the_filters_change', () => {
    expect(initialState).toMatchObject({ pageIndex: 0, pageSize: 20 });

    let state = awardsFeature.reducer(
      initialState,
      AwardsActions.pageChanged({ pageIndex: 2, pageSize: 50 }),
    );
    expect(state).toMatchObject({ pageIndex: 2, pageSize: 50, loading: true });

    state = awardsFeature.reducer(state, AwardsActions.filtersChanged({ filters: NO_FILTERS }));
    expect(state).toMatchObject({ pageIndex: 0, pageSize: 50 });
  });
});

describe('AwardsEffects', () => {
  let actions$: Observable<Action>;
  const service = { list: vi.fn() };

  function effects(filters: AwardFilters): AwardsEffects {
    TestBed.configureTestingModule({
      providers: [
        AwardsEffects,
        provideMockActions(() => actions$),
        provideMockStore({ initialState: { awards: { ...initialState, filters } } }),
        { provide: AwardsService, useValue: service },
      ],
    });
    return TestBed.inject(AwardsEffects);
  }

  beforeEach(() => service.list.mockReset());

  it('ac1_7_loads_the_list_with_the_current_filters', () => {
    const filters: AwardFilters = { ...NO_FILTERS, category: 13 };
    service.list.mockReturnValue(of(page));
    actions$ = of(AwardsActions.opened());
    const emitted: Action[] = [];

    effects(filters).load$.subscribe((action) => emitted.push(action));

    expect(service.list).toHaveBeenCalledWith(filters, 0, 20);
    expect(emitted).toEqual([AwardsActions.awardsLoaded({ page })]);
  });

  it('ac8_loads_the_requested_page', () => {
    service.list.mockReturnValue(of(page));
    actions$ = of(AwardsActions.pageChanged({ pageIndex: 2, pageSize: 50 }));
    TestBed.configureTestingModule({
      providers: [
        AwardsEffects,
        provideMockActions(() => actions$),
        provideMockStore({
          initialState: { awards: { ...initialState, pageIndex: 2, pageSize: 50 } },
        }),
        { provide: AwardsService, useValue: service },
      ],
    });

    TestBed.inject(AwardsEffects).load$.subscribe();

    expect(service.list).toHaveBeenCalledWith(NO_FILTERS, 2, 50);
  });

  it('ac1_7_reports_a_failed_load', () => {
    service.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    actions$ = of(AwardsActions.opened());
    const emitted: Action[] = [];

    effects(NO_FILTERS).load$.subscribe((action) => emitted.push(action));

    expect(emitted).toEqual([AwardsActions.awardsLoadFailed({ problem: 'network' })]);
  });
});
