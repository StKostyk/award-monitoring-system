import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
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
import { languageStub, translations } from '../../analytics.testing';
import { UniversityAnalyticsActions } from '../store/university-analytics.actions';
import { universityAnalyticsFeature } from '../store/university-analytics.feature';
import { complianceSummary, universityAnalytics } from '../university-analytics.testing';
import { UniversityAnalyticsComponent } from './university-analytics.component';

describe('UniversityAnalyticsComponent', () => {
  let fixture: ComponentFixture<UniversityAnalyticsComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap({}));

  async function setUp(
    language: 'en' | 'uk' = 'en',
    query: Record<string, string> = {},
  ): Promise<void> {
    queryParams.next(convertToParamMap(query));
    const stub = chartStub();
    await TestBed.configureTestingModule({
      imports: [UniversityAnalyticsComponent, NoopAnimationsModule, translations(language)],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideStore({}),
        provideState(universityAnalyticsFeature),
        stub.provider,
        languageStub(language).provider,
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(UniversityAnalyticsComponent);
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

  function load(): void {
    store.dispatch(
      UniversityAnalyticsActions.analyticsLoaded({ analytics: universityAnalytics() }),
    );
    store.dispatch(
      UniversityAnalyticsActions.complianceLoaded({ compliance: complianceSummary() }),
    );
    fixture.detectChanges();
  }

  it('ac2_2_loads_the_compliance_once_and_the_analytics_for_each_period', async () => {
    await setUp();

    expect(dispatch).toHaveBeenCalledWith(UniversityAnalyticsActions.complianceRequested());
    expect(dispatch).toHaveBeenCalledWith(
      UniversityAnalyticsActions.periodSelected({ period: { from: null, to: null } }),
    );
    dispatch.mockClear();

    queryParams.next(convertToParamMap({ from: '2023', to: '2025' }));

    expect(dispatch).toHaveBeenCalledWith(
      UniversityAnalyticsActions.periodSelected({ period: { from: 2023, to: 2025 } }),
    );
    expect(dispatch).not.toHaveBeenCalledWith(UniversityAnalyticsActions.complianceRequested());
  });

  it('ac2_2_keeps_the_chosen_period_in_the_address', async () => {
    await setUp();
    load();
    (fixture.componentInstance as unknown as { choose(change: object): void }).choose({
      from: 2024,
    });

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { from: 2024, to: null },
      }),
    );
  });

  it('ac2_3_shows_the_sections_in_order_with_the_previous_period_and_the_partial_year', async () => {
    await setUp();
    load();

    expect(sections()).toEqual([
      'compliance',
      'totals',
      'trend',
      'comparison',
      'by-category',
      'by-level',
      'by-source',
      'trends',
      'highlights',
    ]);
    expect(element('university-analytics-compared')!.textContent).toContain(
      '2022–2026, compared with 2017–2021.',
    );
    expect(element('university-analytics-compared')!.textContent).toContain('2026 is not over yet');
  });

  it('ac2_4_links_the_faculty_and_not_the_college', async () => {
    await setUp();
    load();

    expect(element('comparison-row-9')!.querySelector('a')!.getAttribute('href')).toBe(
      '/analytics/units/9?from=2022&to=2026',
    );
    expect(element('comparison-row-30')!.querySelector('a')).toBeNull();
    expect(element('comparison-row-1')!.textContent).toContain('University level');
  });

  it('ac2_7_refresh_reloads_the_compliance_panel_only', async () => {
    await setUp();
    load();
    dispatch.mockClear();

    element('compliance-refresh')!.click();

    expect(dispatch).toHaveBeenCalledTimes(1);
    expect(dispatch).toHaveBeenCalledWith(UniversityAnalyticsActions.complianceRequested());
  });

  it('ac2_7_a_failed_compliance_load_leaves_the_rest_of_the_page', async () => {
    await setUp();
    store.dispatch(
      UniversityAnalyticsActions.analyticsLoaded({ analytics: universityAnalytics() }),
    );
    store.dispatch(UniversityAnalyticsActions.complianceLoadFailed({ problem: 'unknown' }));
    fixture.detectChanges();

    expect(element('compliance-error')).not.toBeNull();
    expect(element('section-totals')).not.toBeNull();
  });

  it('ac2_8_a_failed_load_shows_the_error_with_retry_and_keeps_the_compliance_panel', async () => {
    await setUp('en', { from: '2023', to: '2025' });
    store.dispatch(
      UniversityAnalyticsActions.complianceLoaded({ compliance: complianceSummary() }),
    );
    store.dispatch(UniversityAnalyticsActions.analyticsLoadFailed({ problem: 'unknown' }));
    fixture.detectChanges();
    dispatch.mockClear();

    expect(element('university-analytics-error')!.textContent).toContain(
      'The university analytics could not be loaded.',
    );
    expect(element('compliance-status')).not.toBeNull();
    (element('university-analytics-error')!.querySelector('button') as HTMLButtonElement).click();

    expect(dispatch).toHaveBeenCalledWith(
      UniversityAnalyticsActions.periodSelected({ period: { from: 2023, to: 2025 } }),
    );
  });

  it('ac2_8_an_invalid_period_is_not_requested', async () => {
    await setUp('en', { from: '2026', to: '2021' });

    expect(element('university-analytics-invalid')!.textContent).toContain(
      'The period is not valid',
    );
    expect(dispatch).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: UniversityAnalyticsActions.periodSelected.type }),
    );
    expect(dispatch).toHaveBeenCalledWith(UniversityAnalyticsActions.complianceRequested());
  });

  it('ac2_9_reads_in_ukrainian', async () => {
    await setUp('uk');
    load();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain(
      'Аналітика університету',
    );
    expect(element('comparison-row-1')!.textContent).toContain('Рівень університету');
  });
});
