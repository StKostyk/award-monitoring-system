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
import { BehaviorSubject, of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { DashboardLayout } from '../../../core/preferences/dashboard-preferences';
import { PreferencesActions } from '../../../core/preferences/store/preferences.actions';
import { preferencesFeature } from '../../../core/preferences/store/preferences.feature';
import { chartStub } from '../../../shared/charts/chart.testing';
import { currentKyivYear } from '../../../shared/date-format';
import { reduceMotion } from '../../../shared/effects/effects.testing';
import { AwardsService } from '../../awards/awards.service';
import { AnalyticsService } from '../analytics.service';
import {
  emptyAnalytics,
  languageStub,
  sampleAnalytics,
  tokenWith,
  translations,
} from '../analytics.testing';
import { AnalyticsActions } from '../store/analytics.actions';
import { analyticsFeature } from '../store/analytics.feature';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap({}));

  beforeEach(async () => {
    queryParams.next(convertToParamMap({}));
    reduceMotion();
    const stub = chartStub();
    await TestBed.configureTestingModule({
      imports: [DashboardComponent, NoopAnimationsModule, translations('en')],
      providers: [
        provideRouter([]),
        provideStore({}),
        provideState(analyticsFeature),
        provideState(preferencesFeature),
        stub.provider,
        languageStub('en').provider,
        {
          provide: AwardsService,
          useValue: { list: () => of({ content: [], totalElements: 0, totalPages: 0 }) },
        },
        { provide: AnalyticsService, useValue: { exportCv: vi.fn() } },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
        {
          provide: AuthService,
          useValue: {
            permissions: signal(readPermissions(tokenWith({ permissions: ['award:create'] }))),
            profile: signal(null),
            fullName: signal(''),
          },
        },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  afterEach(() => vi.unstubAllGlobals());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function widgets(): string[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="widget-"]')].map((node) =>
      (node as HTMLElement).dataset['testid']!.replace('widget-', ''),
    );
  }

  function load(layout?: DashboardLayout, analytics = sampleAnalytics()): void {
    store.dispatch(
      PreferencesActions.loaded({
        preferences: { colorScheme: 'SYSTEM', layouts: layout ? { MY_AWARDS: layout } : {} },
      }),
    );
    store.dispatch(AnalyticsActions.analyticsLoaded({ analytics }));
    fixture.detectChanges();
  }

  it('ac2_5_reads_the_period_from_the_url_and_reloads_on_change', () => {
    expect(dispatch).toHaveBeenCalledWith(
      AnalyticsActions.periodSelected({ period: { from: null, to: null } }),
    );

    queryParams.next(convertToParamMap({ from: '2020', to: '2024' }));

    expect(dispatch).toHaveBeenCalledWith(
      AnalyticsActions.periodSelected({ period: { from: 2020, to: 2024 } }),
    );
  });

  it('ac3_1_the_summary_link_carries_the_period', () => {
    queryParams.next(convertToParamMap({ from: '2024', to: '2025' }));
    fixture.detectChanges();

    expect(element('dashboard-summary')?.getAttribute('href')).toBe(
      '/dashboard/summary?from=2024&to=2025',
    );
  });

  it('ac4_1_refuses_a_period_that_is_not_valid_and_leads_back_to_all_years', () => {
    load();
    dispatch.mockClear();

    for (const params of [
      { from: String(currentKyivYear() + 1) },
      { from: '2024', to: '2020' },
      { from: 'abc' },
    ]) {
      queryParams.next(convertToParamMap(params));
      fixture.detectChanges();

      expect(element('dashboard-invalid')?.textContent).toContain('The period is not valid.');
      expect(element('dashboard-error')).toBeNull();
      expect(widgets()).toEqual([]);
    }
    expect(dispatch).not.toHaveBeenCalled();

    element('dashboard-invalid')!.querySelector('button')!.click();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {} }));

    queryParams.next(convertToParamMap({ from: '2020' }));
    fixture.detectChanges();

    expect(element('dashboard-invalid')).toBeNull();
    expect(dispatch).toHaveBeenCalledWith(
      AnalyticsActions.periodSelected({ period: { from: 2020, to: null } }),
    );
  });

  it('ac4_2_keeps_customising_off_while_the_layout_is_not_loaded', () => {
    store.dispatch(PreferencesActions.loadFailed());
    store.dispatch(AnalyticsActions.analyticsLoaded({ analytics: sampleAnalytics() }));
    fixture.detectChanges();

    expect(element('dashboard-preferences-error')?.textContent).toContain(
      'The dashboard settings could not be loaded.',
    );
    expect((element('dashboard-customise') as HTMLButtonElement).disabled).toBe(true);

    dispatch.mockClear();
    element('dashboard-preferences-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(PreferencesActions.loadRetried());

    load({ order: [], hidden: ['RANK'] });

    expect(element('dashboard-preferences-error')).toBeNull();
    expect((element('dashboard-customise') as HTMLButtonElement).disabled).toBe(false);
  });

  it('ac2_5_shows_the_widgets_in_order_and_keeps_the_choice_in_the_url', () => {
    load();

    expect(widgets()).toEqual([
      'KPI',
      'FACT',
      'TREND',
      'LEVEL_STAIRS',
      'IN_PROGRESS',
      'LATEST',
      'BY_CATEGORY',
      'RANK',
      'VISIBILITY',
      'TIMELINE',
    ]);
    expect(element('tile-approved')?.textContent).toContain('3');
    expect(element('period-all-years')).toBeNull();

    const component = fixture.componentInstance as unknown as {
      choose(change: { from?: number | null; to?: number | null }): void;
    };
    component.choose({ from: 2022 });

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { from: 2022, to: null } }),
    );
  });

  it('ac2_5_offers_all_years_once_a_period_is_chosen', () => {
    queryParams.next(convertToParamMap({ from: '2024' }));
    load();

    element('period-all-years')!.click();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {} }));
  });

  it('ac1_3_shows_the_stored_order_and_leaves_out_hidden_widgets', () => {
    load({ order: ['RANK', 'KPI'], hidden: ['TREND'] });

    expect(widgets()).toEqual([
      'KPI',
      'FACT',
      'RANK',
      'LEVEL_STAIRS',
      'IN_PROGRESS',
      'LATEST',
      'BY_CATEGORY',
      'VISIBILITY',
      'TIMELINE',
    ]);

    load({
      order: [],
      hidden: [
        'KPI',
        'FACT',
        'TREND',
        'LEVEL_STAIRS',
        'IN_PROGRESS',
        'LATEST',
        'BY_CATEGORY',
        'RANK',
        'VISIBILITY',
      ],
    });

    expect(widgets()).toEqual(['TIMELINE']);
    expect(element('dashboard-all-hidden')?.textContent).toContain('All widgets are hidden');
  });

  it('ac1_3_saves_the_layout_edited_on_the_dashboard', () => {
    load({ order: [], hidden: ['RANK'] });
    element('dashboard-customise')!.click();
    fixture.detectChanges();
    element('layout-up-BY_CATEGORY')!.click();
    element('layout-hide-RANK')!.click();
    fixture.detectChanges();
    dispatch.mockClear();
    element('layout-done')!.click();

    expect(dispatch).toHaveBeenCalledWith(
      PreferencesActions.layoutChanged({
        dashboard: 'MY_AWARDS',
        layout: {
          order: [
            'KPI',
            'FACT',
            'TREND',
            'LEVEL_STAIRS',
            'IN_PROGRESS',
            'BY_CATEGORY',
            'LATEST',
            'RANK',
            'VISIBILITY',
          ],
          hidden: [],
        },
      }),
    );

    store.dispatch(PreferencesActions.saveFailed({ problem: 'unknown' }));
    fixture.detectChanges();

    expect(element('dashboard-save-error')).not.toBeNull();
  });

  it('ac2_1_puts_the_profile_kpi_and_facts_beside_the_main_widgets_with_the_header_actions', () => {
    load();

    const side = fixture.nativeElement.querySelector('[data-testid="layout-side"]') as HTMLElement;
    expect(side.querySelector('app-profile-card')).not.toBeNull();
    expect(side.querySelector('[data-testid="widget-FACT"] [data-testid="fact"]')).not.toBeNull();
    expect(element('page-heading')?.textContent).toContain('My dashboard');
    expect(element('dashboard-new-award')?.getAttribute('href')).toBe('/awards/new');
    expect(element('widget-LATEST')?.querySelectorAll('[data-testid="latest-award"]').length).toBe(
      3,
    );
  });

  it('ac1_1_shows_the_approved_and_pending_counts_as_the_context_line', () => {
    expect(element('page-context')).toBeNull();

    load();

    expect(element('page-context')?.textContent).toBe('Approved: 3 · Under review now: 2');
  });

  it('ac1_1_hides_the_context_line_while_the_period_is_not_valid', () => {
    load();

    queryParams.next(convertToParamMap({ from: '2024', to: '2020' }));
    fixture.detectChanges();

    expect(element('dashboard-invalid')).not.toBeNull();
    expect(element('page-context')).toBeNull();
  });

  it('ac1_1_keeps_the_names_of_the_summary_and_cv_actions_shown_as_icons', () => {
    load();

    expect(element('dashboard-summary')?.getAttribute('aria-label')).toBe('Summary');
    expect(element('dashboard-summary')?.textContent?.trim()).toBe('');
    expect(element('cv-export')?.getAttribute('aria-label')).toBe('Export for a CV');
  });

  it('ac2_5_leaves_the_facts_out_with_fewer_than_two_approved_awards', () => {
    const analytics = sampleAnalytics();
    load(undefined, {
      ...analytics,
      totals: { ...analytics.totals, approved: 1 },
      byYear: [{ year: 2026, total: 1, byLevel: { NATIONAL: 1 } }],
      byLevel: [{ level: 'NATIONAL', count: 1 }],
      timeline: analytics.timeline.slice(0, 1),
    });

    expect(widgets()).not.toContain('FACT');
    expect(widgets()).toContain('LATEST');
  });

  it('ac2_8_gives_guidance_instead_of_charts_without_counted_awards', () => {
    load(undefined, emptyAnalytics(1, 2));

    expect(widgets()).toEqual([]);
    expect(element('dashboard-empty-first')).not.toBeNull();
    expect(element('empty-new-award')).not.toBeNull();
    expect(element('empty-in-progress')?.textContent).toContain('Awards in review: 2.');
    expect(element('dashboard-in-progress')).not.toBeNull();

    queryParams.next(convertToParamMap({ from: '2010', to: '2012' }));
    load(undefined, emptyAnalytics());

    expect(element('dashboard-empty-period')).not.toBeNull();
    element('empty-all-years')!.click();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {} }));
  });

  it('ac2_5_reports_a_failed_load_and_retries', () => {
    store.dispatch(AnalyticsActions.analyticsLoadFailed({ problem: 'unknown' }));
    fixture.detectChanges();

    expect(element('dashboard-error')?.textContent).toContain('could not be loaded');

    dispatch.mockClear();
    element('dashboard-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(
      AnalyticsActions.periodSelected({ period: { from: null, to: null } }),
    );
  });

  it('ac4_1_shows_this_year_the_streak_levels_in_progress_and_visibility', () => {
    const year = currentKyivYear();
    const analytics = sampleAnalytics();
    load(undefined, {
      ...analytics,
      byYear: [year - 3, year - 2, year - 1, year].map((each) => ({
        year: each,
        total: each === year ? 2 : 1,
        byLevel: {},
      })),
    });

    expect(element('widget-KPI')?.querySelector('[data-testid="kpi-value"]')).not.toBeNull();
    expect(element('dashboard-streak')?.textContent).toContain(`since ${year - 3}`);
    expect(
      element('widget-LEVEL_STAIRS')?.querySelector('[data-testid="level-stairs"]'),
    ).not.toBeNull();
    expect(element('in-progress-pending')?.textContent).toContain('2');
    expect(element('visibility-PUBLIC')?.textContent).toContain('1');
    expect(element('visibility-documents')?.textContent).toContain('2');
  });

  it('ac4_1_leaves_out_a_streak_shorter_than_three_years', () => {
    load();

    expect(element('dashboard-streak')).toBeNull();
  });

  it('ac4_5_disables_the_cv_export_without_approved_awards', () => {
    load();

    expect((element('cv-export') as HTMLButtonElement).disabled).toBe(false);

    load(undefined, emptyAnalytics());

    expect((element('cv-export') as HTMLButtonElement).disabled).toBe(true);
  });
});
