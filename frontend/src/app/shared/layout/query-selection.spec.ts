import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';

import { positiveIdOf, querySelection } from './query-selection';

describe('positiveIdOf', () => {
  it('reads a positive whole number', () => {
    expect(positiveIdOf('42')).toBe(42);
  });

  it.each([null, '', '0', '-3', '1.5', '1e3', ' 7', 'abc', '1234567890123456'])(
    'ignores %s',
    (value) => {
      expect(positiveIdOf(value)).toBeNull();
    },
  );
});

describe('querySelection', () => {
  function setUp(initial: Record<string, string>) {
    const params = new BehaviorSubject<ParamMap>(convertToParamMap(initial));
    const route = { queryParamMap: params, snapshot: { queryParamMap: params.value } };
    const router = { navigate: vi.fn().mockResolvedValue(true) };
    TestBed.configureTestingModule({
      providers: [
        { provide: ActivatedRoute, useValue: route },
        { provide: Router, useValue: router },
      ],
    });
    const selection = TestBed.runInInjectionContext(() => querySelection('request'));
    return { params, route, router, selection };
  }

  it('starts from the id in the URL and follows its changes', () => {
    const { params, selection } = setUp({ request: '5', page: '2' });
    expect(selection.id()).toBe(5);

    params.next(convertToParamMap({ request: 'x' }));
    expect(selection.id()).toBeNull();

    params.next(convertToParamMap({ request: '9' }));
    expect(selection.id()).toBe(9);
  });

  it('writes the id into its parameter and keeps the others', () => {
    const { route, router, selection } = setUp({});

    selection.select(7);
    selection.select(null);

    expect(router.navigate).toHaveBeenNthCalledWith(1, [], {
      relativeTo: route,
      queryParams: { request: 7 },
      queryParamsHandling: 'merge',
    });
    expect(router.navigate).toHaveBeenNthCalledWith(2, [], {
      relativeTo: route,
      queryParams: { request: null },
      queryParamsHandling: 'merge',
    });
  });
});
