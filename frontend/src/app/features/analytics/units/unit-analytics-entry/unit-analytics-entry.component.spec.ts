import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Store, provideState, provideStore } from '@ngrx/store';
import { vi } from 'vitest';

import { AuthService } from '../../../../core/auth/auth.service';
import { TokenPermissions, readPermissions } from '../../../../core/auth/permissions';
import { tokenWith, translations } from '../../analytics.testing';
import { UnitAnalyticsActions } from '../store/unit-analytics.actions';
import { unitAnalyticsFeature } from '../store/unit-analytics.feature';
import { AnalysableUnit } from '../unit-analytics.service';
import { ALGEBRA, ANALYSIS, FACULTY } from '../unit-analytics.testing';
import { UnitAnalyticsEntryComponent } from './unit-analytics-entry.component';

describe('UnitAnalyticsEntryComponent', () => {
  let fixture: ComponentFixture<UnitAnalyticsEntryComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const route = {};
  const DEAN = ['analytics:read:department'];
  let permissions: WritableSignal<TokenPermissions>;

  function grant(granted: string[]): void {
    permissions.set(readPermissions(tokenWith({ permissions: granted })));
  }

  function create(): void {
    fixture = TestBed.createComponent(UnitAnalyticsEntryComponent);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    permissions = signal(readPermissions(tokenWith({ permissions: DEAN })));
    await TestBed.configureTestingModule({
      imports: [UnitAnalyticsEntryComponent, translations('en')],
      providers: [
        provideRouter([]),
        provideStore({}),
        provideState(unitAnalyticsFeature),
        { provide: ActivatedRoute, useValue: route },
        { provide: AuthService, useValue: { permissions } },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('ac12_5_opens_the_university_page_for_a_holder_of_the_university_view', () => {
    grant([...DEAN, 'analytics:read:university']);
    create();

    expect(navigate).toHaveBeenCalledWith(['university'], {
      relativeTo: route,
      queryParamsHandling: 'preserve',
      replaceUrl: true,
    });
  });

  it('ac12_5_opens_the_first_top_faculty_rather_than_a_department_listed_before_it', () => {
    const orphan: AnalysableUnit = { ...ANALYSIS, id: 80, parentId: 500 };
    create();

    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [orphan, ALGEBRA, FACULTY] }));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['units', 9], expect.anything());
  });

  it('ac12_5_opens_the_first_department_when_no_faculty_is_open_to_the_caller', () => {
    create();

    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [ALGEBRA, ANALYSIS] }));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['units', 64], expect.anything());
  });

  it('ac2_1_opens_the_first_unit_keeping_the_period', () => {
    create();
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
    create();
    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [] }));
    fixture.detectChanges();

    expect(element('unit-analytics-no-units')).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac2_7_retries_a_failed_unit_list', () => {
    create();
    store.dispatch(UnitAnalyticsActions.unitsLoadFailed({ problem: 'network' }));
    fixture.detectChanges();
    dispatch.mockClear();

    element('unit-analytics-units-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
  });
});
