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
import { DashboardComponent, yearParameter } from './dashboard.component';

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

  it('ac2_5_ignores_years_outside_the_allowed_range', () => {
    expect(yearParameter(convertToParamMap({ from: '1949' }), 'from')).toBeNull();
    expect(yearParameter(convertToParamMap({ from: 'abc' }), 'from')).toBeNull();
    expect(yearParameter(convertToParamMap({ from: '2020.5' }), 'from')).toBeNull();
    expect(yearParameter(convertToParamMap({}), 'from')).toBeNull();
    expect(yearParameter(convertToParamMap({ to: '2100' }), 'to')).toBe(2100);
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
