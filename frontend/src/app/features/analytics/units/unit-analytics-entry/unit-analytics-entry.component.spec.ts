import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Store, provideState, provideStore } from '@ngrx/store';
import { vi } from 'vitest';

import { translations } from '../../analytics.testing';
import { UnitAnalyticsActions } from '../store/unit-analytics.actions';
import { unitAnalyticsFeature } from '../store/unit-analytics.feature';
import { ALGEBRA, FACULTY } from '../unit-analytics.testing';
import { UnitAnalyticsEntryComponent } from './unit-analytics-entry.component';

describe('UnitAnalyticsEntryComponent', () => {
  let fixture: ComponentFixture<UnitAnalyticsEntryComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const route = {};

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UnitAnalyticsEntryComponent, translations('en')],
      providers: [
        provideRouter([]),
        provideStore({}),
        provideState(unitAnalyticsFeature),
        { provide: ActivatedRoute, useValue: route },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(UnitAnalyticsEntryComponent);
    fixture.detectChanges();
  });

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('ac2_1_opens_the_first_unit_keeping_the_period', () => {
    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());

    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [FACULTY, ALGEBRA] }));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['units', 9], {
      relativeTo: route,
      queryParamsHandling: 'preserve',
      replaceUrl: true,
    });
  });

  it('ac2_1_says_so_when_no_unit_is_open_to_the_caller', () => {
    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [] }));
    fixture.detectChanges();

    expect(element('unit-analytics-no-units')).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac2_7_retries_a_failed_unit_list', () => {
    store.dispatch(UnitAnalyticsActions.unitsLoadFailed({ problem: 'network' }));
    fixture.detectChanges();
    dispatch.mockClear();

    element('unit-analytics-units-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
  });
});
