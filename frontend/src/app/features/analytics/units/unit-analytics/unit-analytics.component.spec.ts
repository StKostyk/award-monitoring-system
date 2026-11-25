import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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
import { reduceMotion } from '../../../../shared/effects/effects.testing';
import { authStub, languageStub, translations } from '../../analytics.testing';
import { UnitAnalyticsActions } from '../store/unit-analytics.actions';
import { UNIT_NOT_FOUND } from '../store/unit-analytics.effects';
import { unitAnalyticsFeature } from '../store/unit-analytics.feature';
import {
  ALGEBRA,
  ANALYSIS,
  FACULTY,
  departmentAnalytics,
  facultyAnalytics,
} from '../unit-analytics.testing';
import { UnitAnalyticsComponent } from './unit-analytics.component';

describe('UnitAnalyticsComponent', () => {
  let fixture: ComponentFixture<UnitAnalyticsComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const params = new BehaviorSubject<ParamMap>(convertToParamMap({ id: '9' }));
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap({}));

  async function setUp(language: 'en' | 'uk' = 'en'): Promise<void> {
    reduceMotion();
    params.next(convertToParamMap({ id: '9' }));
    queryParams.next(convertToParamMap({}));
    const stub = chartStub();
    await TestBed.configureTestingModule({
      imports: [UnitAnalyticsComponent, NoopAnimationsModule, translations(language)],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideStore({}),
        provideState(unitAnalyticsFeature),
        stub.provider,
        languageStub(language).provider,
        authStub(['analytics:read:department']),
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

  it('ac2_2_ac11_3_loads_the_unit_of_the_address_and_keeps_the_figures_on_later_switches', async () => {
    await setUp();

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({
        unitId: 9,
        period: { from: null, to: null },
        keepFigures: false,
      }),
    );

    queryParams.next(convertToParamMap({ from: '2024', to: '2026' }));
    params.next(convertToParamMap({ id: '64' }));

    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({
        unitId: 64,
        period: { from: 2024, to: 2026 },
        keepFigures: true,
      }),
    );
  });

  it('ac6_1_links_the_whole_faculty_and_each_department_in_the_side_column', async () => {
    await setUp();
    load();

    const list = element('unit-picker')!;
    expect(list.closest('.split__side')).not.toBeNull();
    expect(element('unit-option-9')!.getAttribute('href')).toBe('/analytics/units/9');
    expect(element('unit-option-64')!.getAttribute('href')).toBe('/analytics/units/64');
    expect(element('unit-option-9')!.textContent).toContain(
      'Faculty of Mathematics and Informatics',
    );
    expect(element('unit-option-university')).toBeNull();
  });

  it('ac6_1_counts_the_approved_awards_of_the_faculty_and_its_departments', async () => {
    await setUp();
    load();

    expect(element('unit-option-9')!.textContent).toContain('15');
    expect(element('unit-option-64')!.textContent).toContain('11');
    TestBed.inject(HttpTestingController).expectNone((req) =>
      req.url.endsWith('/analytics/units/9'),
    );
  });

  it('ac6_1_reads_the_department_counts_from_the_faculty_report_on_a_department_page', async () => {
    await setUp();
    params.next(convertToParamMap({ id: '64' }));
    load(departmentAnalytics());

    const request = TestBed.inject(HttpTestingController).expectOne((req) =>
      req.url.endsWith('/analytics/units/9'),
    );
    expect(request.request.params.has('from')).toBe(false);
    expect(request.request.params.has('to')).toBe(false);
    request.flush(facultyAnalytics());
    fixture.detectChanges();

    expect(element('unit-option-9')!.textContent).toContain('15');
    expect(element('unit-option-64')!.textContent).toContain('11');
    expect(element('unit-option-64')!.getAttribute('aria-current')).toBe('true');
  });

  it('ac6_1_ac11_3_keeps_the_list_the_counts_and_the_figures_while_a_sibling_department_loads', async () => {
    await setUp();
    const http = TestBed.inject(HttpTestingController);
    params.next(convertToParamMap({ id: '64' }));
    load(departmentAnalytics());
    http.expectOne((req) => req.url.endsWith('/analytics/units/9')).flush(facultyAnalytics());

    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [FACULTY, ALGEBRA, ANALYSIS] }));
    params.next(convertToParamMap({ id: '66' }));
    fixture.detectChanges();

    expect(element('section-totals')).not.toBeNull();
    expect(element('section-trend')).not.toBeNull();
    expect(element('unit-analytics-body')!.getAttribute('aria-busy')).toBe('true');
    expect(element('unit-option-66')!.getAttribute('aria-current')).toBe('true');
    expect(element('unit-option-64')!.textContent).toContain('11');
    expect(element('page-context')!.textContent).toContain('Analysis');
    http.expectNone((req) => req.url.endsWith('/analytics/units/9'));
  });

  it('ac6_1_shows_the_own_count_when_the_faculty_report_fails', async () => {
    await setUp();
    params.next(convertToParamMap({ id: '64' }));
    load(departmentAnalytics());

    TestBed.inject(HttpTestingController)
      .expectOne((req) => req.url.endsWith('/analytics/units/9'))
      .flush(null, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(element('unit-option-64')!.textContent).toContain('15');
    expect(element('unit-option-9')!.querySelector('.picker__count')).toBeNull();
    expect(element('unit-analytics-error')).toBeNull();
  });

  it('ac6_2_period_chips_set_the_current_year_or_the_whole_period', async () => {
    await setUp();
    load();
    const year = currentKyivYear();

    element('period-chip-year')!.click();
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: { from: year, to: year },
    });

    element('period-chip-all')!.click();
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {},
    });
    expect(element('unit-export')).not.toBeNull();
  });

  it('ac4_2_captions_each_change_with_the_previous_period_instead_of_a_paragraph', async () => {
    await setUp();
    load();

    expect(element('unit-analytics-compared')).toBeNull();
    const captions = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="tile-against"]'),
    ];
    expect(captions.map((caption) => caption.textContent!.trim())).toEqual([
      'vs 2021–2023',
      'vs 2021–2023',
    ]);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('not over');
  });

  it('ac4_1_keeps_the_unit_rail_sticky_beside_the_widgets', async () => {
    await setUp();
    load();

    const split = (fixture.nativeElement as HTMLElement).querySelector('app-split')!;
    expect(split.classList).toContain('split--sticky');
    expect(split.querySelector('.split__side [data-testid="section-totals"]')).not.toBeNull();
    expect(element('page-context')!.closest('app-split')).toBeNull();
  });

  it('ac2_3_ac6_1_shows_the_faculty_sections_in_order_with_the_comparison', async () => {
    await setUp();
    load();

    expect(sections()).toEqual([
      'totals',
      'units',
      'trend',
      'top-performers',
      'highlights',
      'comparison',
      'by-category',
      'by-level',
      'by-source',
    ]);
    const side = fixture.nativeElement.querySelector('.split__side') as HTMLElement;
    const main = fixture.nativeElement.querySelector('.split__main') as HTMLElement;
    expect(side.querySelector('[data-testid="section-totals"]')).not.toBeNull();
    for (const section of ['trend', 'top-performers', 'highlights']) {
      expect(main.querySelector(`[data-testid="section-${section}"]`)).not.toBeNull();
    }
    expect(fixture.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(element('page-context')!.textContent).toContain('Faculty of Mathematics');
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
    expect(element('unit-not-found')!.tagName).toBe('APP-STATUS-PAGE');
    expect(element('unit-not-found')!.querySelector('h1')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('h1')).toHaveLength(1);
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

  it('ac2_7_retry_also_reloads_a_unit_list_that_failed', async () => {
    await setUp();
    store.dispatch(UnitAnalyticsActions.unitsLoadFailed({ problem: 'network' }));
    store.dispatch(UnitAnalyticsActions.analyticsLoadFailed({ problem: 'network' }));
    fixture.detectChanges();
    dispatch.mockClear();

    element('unit-analytics-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
    expect(dispatch).toHaveBeenCalledWith(
      UnitAnalyticsActions.unitSelected({ unitId: 9, period: { from: null, to: null } }),
    );
  });

  it('ac2_7_retry_keeps_a_unit_list_that_loaded', async () => {
    await setUp();
    store.dispatch(UnitAnalyticsActions.unitsLoaded({ units: [FACULTY, ALGEBRA] }));
    store.dispatch(UnitAnalyticsActions.analyticsLoadFailed({ problem: 'network' }));
    fixture.detectChanges();
    dispatch.mockClear();

    element('unit-analytics-error')!.querySelector('button')!.click();

    expect(dispatch).not.toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
  });

  it('ac2_7_reopening_the_page_requests_a_unit_list_that_failed', async () => {
    await setUp();
    store.dispatch(UnitAnalyticsActions.unitsLoadFailed({ problem: 'network' }));
    dispatch.mockClear();

    TestBed.createComponent(UnitAnalyticsComponent).detectChanges();

    expect(dispatch).toHaveBeenCalledWith(UnitAnalyticsActions.unitsRequested());
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

  it('ac3_6_offers_the_export_only_while_a_report_is_shown', async () => {
    await setUp();
    const exportButton = (): HTMLButtonElement => element('unit-export') as HTMLButtonElement;
    expect(exportButton().disabled).toBe(true);

    load();
    expect(exportButton().disabled).toBe(false);

    store.dispatch(UnitAnalyticsActions.analyticsLoadFailed({ problem: 'network' }));
    fixture.detectChanges();
    expect(exportButton().disabled).toBe(true);

    load();
    queryParams.next(convertToParamMap({ from: '2026', to: '2021' }));
    fixture.detectChanges();
    expect(exportButton().disabled).toBe(true);
  });

  it('ac3_6_exports_the_years_of_the_shown_figures_on_the_default_period', async () => {
    await setUp();
    load();

    (element('unit-export') as HTMLButtonElement).click();
    fixture.detectChanges();
    (document.querySelector('[data-testid="unit-export-csv"]') as HTMLButtonElement).click();

    const request = TestBed.inject(HttpTestingController).expectOne((req) =>
      req.url.endsWith('/9/export'),
    ).request;
    expect(request.params.get('from')).toBe('2024');
    expect(request.params.get('to')).toBe('2026');
  });

  it('ac2_4_opens_a_department_with_the_years_of_the_shown_figures_on_the_default_period', async () => {
    await setUp();
    load();

    const link = element('section-comparison')!.querySelector('a[href*="/analytics/units/64"]');

    expect(link!.getAttribute('href')).toBe('/analytics/units/64?from=2024&to=2026');
  });

  it('ac3_6_leaves_out_the_export_for_an_unknown_unit', async () => {
    await setUp();
    store.dispatch(UnitAnalyticsActions.analyticsLoadFailed({ problem: UNIT_NOT_FOUND }));
    fixture.detectChanges();

    expect(element('unit-export')).toBeNull();
  });
});
