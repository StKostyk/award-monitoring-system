import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { vi } from 'vitest';

import { LanguageService } from '../../core/i18n/language.service';
import { Page } from '../../shared/page';
import { ComplianceComponent, readView } from './compliance.component';
import { ErasureEntry, HoldEntry, RetentionRun } from './compliance.service';
import { ComplianceActions } from './store/compliance.actions';
import { complianceFeature } from './store/compliance.feature';

const request = { page: 0, size: 20 };
const officer = { id: 5, name: 'Ольга Офіцер', email: 'gdpr@chnu.edu.ua' };

const translations = {
  uk: {
    compliance: {
      title: 'Відповідність',
      tabs: { retention: 'Зберігання', erasures: 'Запити на видалення', holds: 'Утримання' },
      policies: { 'audit-logs': 'Журнал аудиту' },
      units: { years: '{{count}} р.' },
      duration: { seconds: '{{seconds}} с' },
      runStatus: { PARTIAL: 'Частково' },
      origin: { RETENTION: 'Строк зберігання' },
      erasureStatus: { PENDING: 'Очікує', COMPLETED: 'Виконано' },
      erasures: { postponed: 'Відкладено', empty: 'Запитів немає.' },
      holds: { empty: 'Утримань немає.', inForce: 'Діє' },
      retention: { failed: 'Помилка', empty: 'Запусків ще не було.' },
      problems: { failed: 'Не вдалося завантажити список.' },
      retry: 'Спробувати ще раз',
    },
  },
};

function page<T>(content: T[]): Page<T> {
  return { content, totalElements: content.length, totalPages: 1, size: 20, number: 0 };
}

const partial: RetentionRun = {
  id: 7,
  status: 'PARTIAL',
  startedAt: '2026-10-18T01:00:00Z',
  finishedAt: '2026-10-18T01:00:42Z',
  results: [
    { policy: 'audit-logs', affected: 2, held: 1, failed: false, error: null },
    { policy: 'consent-records', affected: 0, held: 0, failed: true, error: 'timeout' },
  ],
};

const erased: ErasureEntry = {
  id: 11,
  person: { id: 41, name: 'Erased #41', email: 'erased-41@erased.invalid' },
  origin: 'RETENTION',
  status: 'COMPLETED',
  requestedAt: '2026-10-01T03:30:00Z',
  scheduledFor: '2026-10-01T03:30:00Z',
  postponedAt: null,
  cancelledAt: null,
  completedAt: '2026-10-01T03:31:00Z',
};

const postponed: ErasureEntry = {
  ...erased,
  id: 12,
  person: { id: 42, name: 'Петро Пенсіонер', email: 'retired@chnu.edu.ua' },
  status: 'PENDING',
  postponedAt: '2026-10-02T03:30:00Z',
  completedAt: null,
};

const lifted: HoldEntry = {
  id: 3,
  person: { id: 42, name: 'Петро Пенсіонер', email: 'retired@chnu.edu.ua' },
  reason: 'Судовий запит щодо нагород',
  placedAt: '2026-10-02T09:00:00Z',
  placedBy: officer,
  liftedAt: '2026-10-05T09:00:00Z',
  liftedBy: officer,
  liftReason: 'Справу закрито судом',
};

function text(element: Element | null): string {
  return element?.textContent?.trim() ?? '';
}

describe('ComplianceComponent', () => {
  let harness: RouterTestingHarness;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([{ path: 'compliance', component: ComplianceComponent }]),
        provideStore(),
        provideState(complianceFeature),
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    harness = await RouterTestingHarness.create();
  });

  function query(selector: string): HTMLElement | null {
    return harness.routeNativeElement?.querySelector(selector) ?? null;
  }

  function queryAll(selector: string): HTMLElement[] {
    return Array.from(harness.routeNativeElement?.querySelectorAll(selector) ?? []);
  }

  it('ac2_5_reads_the_tab_filter_and_page_from_the_url_with_safe_defaults', () => {
    expect(readView(convertToParamMap({}))).toEqual({
      tab: 'retention',
      status: null,
      active: true,
      page: 0,
      size: 20,
    });
    expect(
      readView(
        convertToParamMap({ tab: 'holds', status: 'PENDING', holds: 'all', page: '2', size: '50' }),
      ),
    ).toEqual({ tab: 'holds', status: 'PENDING', active: false, page: 2, size: 50 });
    expect(
      readView(convertToParamMap({ tab: 'x', status: 'GONE', page: '-1', size: '7' })),
    ).toEqual({ tab: 'retention', status: null, active: true, page: 0, size: 20 });
  });

  it('ac2_5_lists_the_policies_and_the_runs_with_status_duration_and_failed_policies', async () => {
    await harness.navigateByUrl('/compliance');

    expect(dispatch).toHaveBeenCalledWith(ComplianceActions.retentionOpened({ request }));

    store.dispatch(
      ComplianceActions.policiesLoaded({
        policies: [{ key: 'audit-logs', period: 'P7Y', action: 'drop-partitions', order: 9 }],
      }),
    );
    store.dispatch(ComplianceActions.runsLoaded({ page: page([partial]) }));
    harness.detectChanges();

    expect(text(query('[data-testid="policies-table"] tbody'))).toContain('Журнал аудиту');
    expect(text(query('[data-testid="policy-period"]'))).toBe('7 р.');
    expect(text(query('[data-testid="run-status"]'))).toBe('Частково');
    expect(text(query('[data-testid="run-duration"]'))).toBe('42 с');

    query('[data-testid="run"] mat-expansion-panel-header')?.click();
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(queryAll('[data-testid="result-failed"]')).toHaveLength(1);
    expect(text(query('[data-testid="run-results"]'))).toContain('timeout');
  });

  it('ac2_5_lists_erasure_requests_with_origin_postponement_and_a_link_to_the_person', async () => {
    await harness.navigateByUrl('/compliance?tab=erasures&status=PENDING');

    expect(dispatch).toHaveBeenCalledWith(
      ComplianceActions.erasuresOpened({ request, status: 'PENDING' }),
    );

    store.dispatch(ComplianceActions.erasuresLoaded({ page: page([erased, postponed]) }));
    harness.detectChanges();
    const rows = queryAll('[data-testid="erasure-row"]');
    const links = queryAll('[data-testid="person-link"]');

    expect(rows).toHaveLength(2);
    expect(text(rows[0])).toContain('Строк зберігання');
    expect(text(rows[0])).not.toContain('erased.invalid');
    expect(links[0].getAttribute('href')).toBe('/admin/users/41');
    expect(text(rows[1])).toContain('retired@chnu.edu.ua');
    expect(queryAll('[data-testid="erasure-postponed"]')).toHaveLength(1);
  });

  it('ac2_5_keeps_a_changed_filter_in_the_url_and_reads_the_list_again', async () => {
    await harness.navigateByUrl('/compliance?tab=erasures');
    dispatch.mockClear();

    query('[data-testid="erasure-filter-COMPLETED"] button')?.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/compliance?tab=erasures&status=COMPLETED');
    expect(dispatch).toHaveBeenCalledWith(
      ComplianceActions.erasuresOpened({ request, status: 'COMPLETED' }),
    );
  });

  it('ac2_5_shows_active_holds_first_and_every_hold_with_who_placed_and_lifted_it', async () => {
    await harness.navigateByUrl('/compliance?tab=holds');

    expect(dispatch).toHaveBeenCalledWith(ComplianceActions.holdsOpened({ request, active: true }));

    query('[data-testid="hold-filter-all"] button')?.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/compliance?tab=holds&holds=all');
    expect(dispatch).toHaveBeenCalledWith(
      ComplianceActions.holdsOpened({ request, active: false }),
    );

    store.dispatch(ComplianceActions.holdsLoaded({ page: page([lifted]) }));
    harness.detectChanges();
    const row = text(query('[data-testid="hold-row"]'));

    expect(row).toContain('Судовий запит щодо нагород');
    expect(row).toContain('Справу закрито судом');
    expect(row).toContain('Ольга Офіцер');
  });

  it('ac2_6_shows_a_loading_bar_an_empty_state_and_a_retry_after_a_failure', async () => {
    await harness.navigateByUrl('/compliance?tab=holds');

    expect(query('[data-testid="holds-loading"]')).not.toBeNull();

    store.dispatch(ComplianceActions.holdsLoaded({ page: page([]) }));
    harness.detectChanges();

    expect(query('[data-testid="holds-loading"]')).toBeNull();
    expect(text(query('[data-testid="holds-empty"]'))).toBe('Утримань немає.');

    store.dispatch(ComplianceActions.holdsLoadFailed({ problem: 'failed' }));
    harness.detectChanges();

    expect(query('[data-testid="holds-empty"]')).toBeNull();
    expect(text(query('[data-testid="holds-error"]'))).toContain('Не вдалося завантажити список.');

    dispatch.mockClear();
    query('[data-testid="compliance-retry"]')?.click();

    expect(dispatch).toHaveBeenCalledWith(ComplianceActions.holdsOpened({ request, active: true }));
  });

  it('ac2_6_says_so_when_no_retention_run_exists_yet', async () => {
    await harness.navigateByUrl('/compliance');
    store.dispatch(ComplianceActions.policiesLoaded({ policies: [] }));
    store.dispatch(ComplianceActions.runsLoaded({ page: page([]) }));
    harness.detectChanges();

    expect(text(query('[data-testid="runs-empty"]'))).toBe('Запусків ще не було.');
    expect(query('[data-testid="runs-paginator"]')).toBeNull();
  });
});
