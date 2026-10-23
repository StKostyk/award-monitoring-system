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

import { chartStub } from '../../../../shared/charts/chart.testing';
import { currentKyivYear } from '../../../../shared/date-format';
import { languageStub, translations } from '../../analytics.testing';
import { UnitAnalyticsActions } from '../store/unit-analytics.actions';
import { UNIT_NOT_FOUND } from '../store/unit-analytics.effects';
import { unitAnalyticsFeature } from '../store/unit-analytics.feature';
import { ALGEBRA, FACULTY, departmentAnalytics, facultyAnalytics } from '../unit-analytics.testing';
import { UnitAnalyticsComponent } from './unit-analytics.component';

describe('UnitAnalyticsComponent', () => {
  let fixture: ComponentFixture<UnitAnalyticsComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const params = new BehaviorSubject<ParamMap>(convertToParamMap({ id: '9' }));
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap({}));

  async function setUp(language: 'en' | 'uk' = 'en'): Promise<void> {
    params.next(convertToParamMap({ id: '9' }));
    queryParams.next(convertToParamMap({}));
    const stub = chartStub();
    await TestBed.configureTestingModule({
      imports: [UnitAnalyticsComponent, NoopAnimationsModule, translations(language)],
      providers: [
        provideRouter([]),
        provideStore({}),
        provideState(unitAnalyticsFeature),
        stub.provider,
        languageStub(language).provider,
        { provide: ActivatedRoute, useValue: { paramMap: params, queryParamMap: queryParams } },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(UnitAnalyticsComponent);
    fixture.detectChanges();
  }

  afterEach(() => vi.unstubAllGlobals());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function sections(): string[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="section-"]')].map((node) =>
      (node as HTMLElement).dataset['testid']!.replace('section-', ''),
    );
  }

  function load(analytics = facultyAnalytics()): void {
    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [FACULTY, ALGEBRA] }));
    store.dispatch(UnitAnalyticsActions.analyticsLoaded({ analytics }));
    fixture.detectChanges();
  }

  it('ac2_2_loads_the_units_and_the_unit_of_the_address_for_its_period', async () => {
    await setUp();

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: { from: null, to: null } }),
    );

    queryParams.next(convertToParamMap({ from: '2024', to: '2026' }));
    params.next(convertToParamMap({ id: '64' }));

    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 64, period: { from: 2024, to: 2026 } }),
    );
  });

  it('ac2_2_opens_the_chosen_unit_with_the_same_period', async () => {
    await setUp();
    load();

    expect(element('unit-picker')).not.toBeNull();
    (fixture.componentInstance as unknown as { chooseUnit(id: number): void }).chooseUnit(64);

    expect(navigate).toHaveBeenCalledWith(['/analytics/units', 64], {
      queryParamsHandling: 'preserve',
    });
  });

  it('ac2_2_states_the_previous_period_and_that_the_year_is_not_over', async () => {
    await setUp();
    load();

    expect(element('unit-analytics-compared')!.textContent).toContain(
      '2024–2026, compared with 2021–2023.',
    );
    expect(element('unit-analytics-compared')!.textContent).toContain('2026 is not over yet');

    load(departmentAnalytics());

    expect(element('unit-analytics-compared')!.textContent).not.toContain('not over');
  });

  it('ac2_3_shows_the_faculty_sections_in_order_with_the_comparison', async () => {
    await setUp();
    load();

    expect(sections()).toEqual([
      'totals',
      'trend',
      'comparison',
      'by-category',
      'by-level',
      'by-source',
      'top-performers',
      'highlights',
    ]);
    expect(element('tile-approved')!.textContent).toContain('15');
    expect(element('tile-approved')!.textContent).toContain('+5 (+50 %)');
    expect(element('tile-awarded-people')!.textContent).toContain('new');
  });

  it('ac2_3_leaves_out_the_comparison_for_a_department', async () => {
    await setUp();
    load(departmentAnalytics());

    expect(sections()).not.toContain('comparison');
    expect(sections()).toContain('trend');
  });

  it('ac2_1_shows_an_unknown_unit_with_a_way_back', async () => {
    await setUp();
    store.dispatch(UnitAnalyticsActions.analyticsLoadFailed({ problem: UNIT_NOT_FOUND }));
    fixture.detectChanges();

    expect(element('unit-not-found')!.textContent).toContain('This unit is not available.');
    expect(element('unit-not-found')!.querySelector('a')!.getAttribute('href')).toBe('/analytics');
  });

  it('ac2_1_does_not_ask_the_api_for_an_id_that_is_not_a_number', async () => {
    await setUp();
    dispatch.mockClear();
    params.next(convertToParamMap({ id: 'abc' }));
    fixture.detectChanges();

    expect(element('unit-not-found')).not.toBeNull();
    expect(dispatch).not.toHaveBeenCalled();
  });

  it('ac2_7_shows_a_failed_load_with_retry', async () => {
    await setUp();
    store.dispatch(UnitAnalyticsActions.analyticsLoadFailed({ problem: 'network' }));
    fixture.detectChanges();
    dispatch.mockClear();

    element('unit-analytics-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: { from: null, to: null } }),
    );
  });

  it('ac2_7_refuses_a_period_that_is_not_valid', async () => {
    await setUp('uk');
    dispatch.mockClear();
    queryParams.next(convertToParamMap({ from: String(currentKyivYear() + 1) }));
    fixture.detectChanges();

    expect(element('unit-analytics-invalid')!.textContent).toContain('Період некоректний');
    expect(dispatch).not.toHaveBeenCalled();

    element('unit-analytics-invalid')!.querySelector('button')!.click();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {} }));
  });
});
