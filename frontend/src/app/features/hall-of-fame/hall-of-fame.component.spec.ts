import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { Store, provideState, provideStore } from '@ngrx/store';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { readPermissions } from '../../core/auth/permissions';
import { DashboardLayout } from '../../core/preferences/dashboard-preferences';
import { PreferencesActions } from '../../core/preferences/store/preferences.actions';
import { preferencesFeature } from '../../core/preferences/store/preferences.feature';
import { chartStub } from '../../shared/charts/chart.testing';
import { reduceMotion } from '../../shared/effects/effects.testing';
import { languageStub, tokenWith, translations } from '../analytics/analytics.testing';
import { UnitAnalyticsActions } from '../analytics/units/store/unit-analytics.actions';
import { unitAnalyticsFeature } from '../analytics/units/store/unit-analytics.feature';
import { AnalysableUnit } from '../analytics/units/unit-analytics.service';
import { ALGEBRA, FACULTY, facultyAnalytics } from '../analytics/units/unit-analytics.testing';
import { UniversityAnalyticsActions } from '../analytics/university/store/university-analytics.actions';
import { universityAnalyticsFeature } from '../analytics/university/store/university-analytics.feature';
import { universityAnalytics } from '../analytics/university/university-analytics.testing';
import { HallOfFameComponent } from './hall-of-fame.component';

const PHYSICS: AnalysableUnit = {
  id: 12,
  name: 'Faculty of Physics',
  nameUk: 'Фізичний факультет',
  code: 'PHYS',
  type: 'FACULTY',
  parentId: null,
};

const ALL_YEARS = { from: null, to: null };
const RECTOR = { role_scopes: ['RECTOR:1'], permissions: ['analytics:read:university'] };
const DEAN = { role_scopes: ['DEAN:9'], permissions: ['analytics:read:department'] };

describe('HallOfFameComponent', () => {
  let fixture: ComponentFixture<HallOfFameComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap({}));

  async function setUp(claims: Record<string, unknown>, query: Record<string, string> = {}) {
    reduceMotion();
    queryParams.next(convertToParamMap(query));
    const stub = chartStub();
    await TestBed.configureTestingModule({
      imports: [HallOfFameComponent, NoopAnimationsModule, translations('en')],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideStore({}),
        provideState(universityAnalyticsFeature),
        provideState(unitAnalyticsFeature),
        provideState(preferencesFeature),
        stub.provider,
        languageStub('en').provider,
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
        {
          provide: AuthService,
          useValue: { permissions: signal(readPermissions(tokenWith(claims))) },
        },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(HallOfFameComponent);
    fixture.detectChanges();
  }

  afterEach(() => vi.unstubAllGlobals());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function widgets(): string[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="widget-"]')].map((node) =>
      (node as HTMLElement).dataset['testid']!.replace('widget-', ''),
    );
  }

  function preferences(layout?: DashboardLayout): void {
    store.dispatch(
      PreferencesActions.loaded({
        preferences: { colorScheme: 'SYSTEM', layouts: layout ? { HALL_OF_FAME: layout } : {} },
      }),
    );
  }

  function faculties(units: AnalysableUnit[]): void {
    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units }));
    fixture.detectChanges();
  }

  it('ac3_1_shows_the_seven_widgets_of_the_university_for_a_rector', async () => {
    await setUp(RECTOR);

    expect(dispatch).toHaveBeenCalledWith(
      UniversityAnalyticsActions.periodSelected({ period: ALL_YEARS }),
    );
    expect(dispatch).not.toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());

    preferences();
    store.dispatch(
      UniversityAnalyticsActions.analyticsLoaded({ analytics: universityAnalytics() }),
    );
    fixture.detectChanges();

    expect(widgets()).toEqual(['KPI', 'TREND', 'RACE', 'RECORDS', 'FACT', 'TOP', 'LEVEL_MAP']);
    expect(element('hall-unit')?.textContent).toContain('Chernivtsi National University');
    expect(element('hall-analytics')?.getAttribute('href')).toBe('/analytics/university');
    expect(element('board-report')).not.toBeNull();
    expect(element('widget-TOP')?.textContent).toContain('Leading faculties');
    expect(element('widget-TOP')?.textContent).toContain('Faculty of Mathematics and Informatics');
    expect(element('unit-picker')).toBeNull();
  });

  it('ac3_5_follows_the_hall_of_fame_layout_of_the_account', async () => {
    await setUp(RECTOR);
    preferences({ order: ['RACE', 'KPI'], hidden: ['LEVEL_MAP'] });
    store.dispatch(
      UniversityAnalyticsActions.analyticsLoaded({ analytics: universityAnalytics() }),
    );
    fixture.detectChanges();

    expect(widgets()).toEqual(['RACE', 'KPI', 'TREND', 'RECORDS', 'FACT', 'TOP']);
  });

  it('ac3_2_reads_the_faculty_of_the_dean_without_a_picker', async () => {
    await setUp(DEAN);

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());

    preferences();
    faculties([FACULTY, ALGEBRA]);

    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: ALL_YEARS }),
    );

    store.dispatch(UnitAnalyticsActions.analyticsLoaded({ analytics: facultyAnalytics() }));
    fixture.detectChanges();

    expect(element('unit-picker')).toBeNull();
    expect(element('board-report')).toBeNull();
    expect(element('hall-analytics')?.getAttribute('href')).toBe('/analytics/units/9');
    expect(element('widget-TOP')?.textContent).toContain('Olena Koval');
    expect(widgets().length).toBe(7);
  });

  it('ac3_2_switches_between_the_held_faculties_of_a_dean_through_the_address', async () => {
    const borrowed = { ...PHYSICS, id: 13, name: 'Faculty of Philology' };
    await setUp(
      { role_scopes: ['DEAN:9', 'DEAN:12', 'DEAN:13'], delegations: ['DEAN:13:4'] },
      { unit: '12' },
    );
    faculties([FACULTY, ALGEBRA, PHYSICS, borrowed]);

    expect(element('unit-picker')).not.toBeNull();
    expect(fixture.componentInstance.faculties().map((unit) => unit.id)).toEqual([9, 12]);
    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 12, period: ALL_YEARS }),
    );

    (fixture.componentInstance as unknown as { choose(id: number): void }).choose(9);

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { unit: 9 } }),
    );

    queryParams.next(convertToParamMap({ unit: '9' }));
    fixture.detectChanges();

    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: ALL_YEARS }),
    );
  });

  it('falls back to the first faculty when the address names one out of scope', async () => {
    await setUp(DEAN, { unit: '12' });
    faculties([FACULTY, PHYSICS]);

    expect(fixture.componentInstance.facultyId()).toBe(9);
    expect(dispatch).not.toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 12, period: ALL_YEARS }),
    );
  });

  it('says so when the dean has no faculty and offers a retry on errors', async () => {
    await setUp(DEAN);
    faculties([ALGEBRA]);

    expect(element('hall-no-faculty')).not.toBeNull();

    store.dispatch(UnitAnalyticsActions.unitsLoadFailed({ problem: 'unknown' }));
    fixture.detectChanges();
    dispatch.mockClear();
    element('hall-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
  });

  it('retries the university after a failure', async () => {
    await setUp(RECTOR);
    store.dispatch(UniversityAnalyticsActions.analyticsLoadFailed({ problem: 'unknown' }));
    fixture.detectChanges();
    dispatch.mockClear();

    element('hall-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(
      UniversityAnalyticsActions.periodSelected({ period: ALL_YEARS }),
    );
  });
});
