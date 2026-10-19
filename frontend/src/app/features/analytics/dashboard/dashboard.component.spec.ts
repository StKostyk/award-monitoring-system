import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
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
import { chartStub } from '../../../shared/charts/chart.testing';
import { currentKyivYear } from '../../../shared/date-format';
import { DashboardWidget } from '../analytics.service';
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
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    queryParams.next(convertToParamMap({}));
    dialog.open.mockReset();
    const stub = chartStub();
    await TestBed.configureTestingModule({
      imports: [DashboardComponent, NoopAnimationsModule, translations('en')],
      providers: [
        provideRouter([]),
        provideStore({}),
        provideState(analyticsFeature),
        stub.provider,
        languageStub('en').provider,
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
        { provide: MatDialog, useValue: dialog },
        {
          provide: AuthService,
          useValue: {
            permissions: signal(readPermissions(tokenWith({ permissions: ['award:create'] }))),
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

  function load(hidden: DashboardWidget[] = [], analytics = sampleAnalytics()): void {
    store.dispatch(AnalyticsActions.preferencesLoaded({ hidden }));
    store.dispatch(AnalyticsActions.analyticsLoaded({ analytics }));
    fixture.detectChanges();
  }

  it('ac2_5_reads_the_period_from_the_url_and_reloads_on_change', () => {
    expect(dispatch).toHaveBeenCalledWith(AnalyticsActions.opened());
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

  it('ac4_2_keeps_the_dialog_closed_while_the_widget_choice_is_not_loaded', () => {
    store.dispatch(AnalyticsActions.preferencesLoadFailed());
    store.dispatch(AnalyticsActions.analyticsLoaded({ analytics: sampleAnalytics() }));
    fixture.detectChanges();

    expect(element('dashboard-preferences-error')?.textContent).toContain(
      'The widget choice could not be loaded.',
    );
    expect((element('dashboard-customise') as HTMLButtonElement).disabled).toBe(true);
    element('dashboard-customise')!.click();

    expect(dialog.open).not.toHaveBeenCalled();

    dispatch.mockClear();
    element('dashboard-preferences-error')!.querySelector('button')!.click();

    expect(dispatch).toHaveBeenCalledWith(AnalyticsActions.preferencesRetried());

    store.dispatch(AnalyticsActions.preferencesLoaded({ hidden: ['RANK'] }));
    fixture.detectChanges();

    expect(element('dashboard-preferences-error')).toBeNull();
    expect((element('dashboard-customise') as HTMLButtonElement).disabled).toBe(false);
  });

  it('ac2_5_shows_the_widgets_in_order_and_keeps_the_choice_in_the_url', () => {
    load();

    expect(widgets()).toEqual([
      'SUMMARY',
      'TIMELINE',
      'YEAR_TREND',
      'BY_CATEGORY',
      'BY_LEVEL',
      'RANK',
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

  it('ac2_9_leaves_out_hidden_widgets_and_hints_when_all_are_hidden', () => {
    load(['TIMELINE', 'RANK']);

    expect(widgets()).toEqual(['SUMMARY', 'YEAR_TREND', 'BY_CATEGORY', 'BY_LEVEL']);

    load(['SUMMARY', 'TIMELINE', 'YEAR_TREND', 'BY_CATEGORY', 'BY_LEVEL', 'RANK']);

    expect(widgets()).toEqual([]);
    expect(element('dashboard-all-hidden')?.textContent).toContain('All widgets are hidden');
  });

  it('ac2_9_saves_the_choice_of_the_dialog', () => {
    load(['RANK']);
    dialog.open.mockReturnValueOnce({ afterClosed: () => of(['TIMELINE']) });
    element('dashboard-customise')!.click();

    expect(dialog.open.mock.calls[0][1]).toMatchObject({ data: ['RANK'] });
    expect(dispatch).toHaveBeenCalledWith(AnalyticsActions.saveRequested({ hidden: ['TIMELINE'] }));

    dispatch.mockClear();
    dialog.open.mockReturnValueOnce({ afterClosed: () => of(undefined) });
    element('dashboard-customise')!.click();

    expect(dispatch).not.toHaveBeenCalled();

    store.dispatch(AnalyticsActions.saveFailed({ problem: 'unknown' }));
    fixture.detectChanges();

    expect(element('dashboard-save-error')).not.toBeNull();
  });

  it('ac2_8_gives_guidance_instead_of_charts_without_counted_awards', () => {
    load([], emptyAnalytics(1, 2));

    expect(widgets()).toEqual([]);
    expect(element('dashboard-empty-first')).not.toBeNull();
    expect(element('empty-new-award')).not.toBeNull();
    expect(element('empty-in-progress')?.textContent).toContain('Awards in review: 2.');

    queryParams.next(convertToParamMap({ from: '2010', to: '2012' }));
    load([], emptyAnalytics());

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
});
