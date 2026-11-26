import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Store, provideState, provideStore } from '@ngrx/store';
import { Subject, of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { readPermissions } from '../../core/auth/permissions';
import { OrganizationRef } from '../../core/auth/user-profile';
import { DashboardLayout } from '../../core/preferences/dashboard-preferences';
import { PreferencesActions } from '../../core/preferences/store/preferences.actions';
import { preferencesFeature } from '../../core/preferences/store/preferences.feature';
import { reduceMotion } from '../../shared/effects/effects.testing';
import { languageStub, tokenWith, translations } from '../analytics/analytics.testing';
import { UnitAnalytics } from '../analytics/units/unit-analytics.service';
import { facultyAnalytics, figure } from '../analytics/units/unit-analytics.testing';
import { universityAnalytics } from '../analytics/university/university-analytics.testing';
import { DecisionDialogComponent } from '../reviews/decision-dialog/decision-dialog.component';
import { HeldDecisionService, ReturnedDecision } from '../reviews/held-decision.service';
import { DecisionOutcome, ReviewItem } from '../reviews/reviews.service';
import { CommandCentreComponent, QUEUE_SIZE } from './command-centre.component';
import { ReminderDialogComponent } from './reminder-dialog/reminder-dialog.component';

const SECRETARY = { sub: '30', role_scopes: ['FACULTY_SECRETARY:9'] };
const RECTOR_SECRETARY = { sub: '31', role_scopes: ['RECTOR_SECRETARY:1'] };
const GEOMETRY: OrganizationRef = {
  id: 44,
  name: 'Department of Geometry',
  nameUk: 'Кафедра геометрії',
  code: 'GEOM',
  type: 'DEPARTMENT',
};
const TOPOLOGY: OrganizationRef = { ...GEOMETRY, id: 45, name: 'Department of Topology' };

function reviewItem(awardId: number, overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    awardId,
    requestId: awardId + 100,
    requestVersion: 2,
    title: `Award ${awardId}`,
    titleUk: null,
    recipient: { type: 'PERSON', organization: null },
    owner: { id: 21, name: 'Anastasia Koval', email: 'employee.fmi@chnu.edu.ua' },
    organization: null,
    category: null,
    level: 'FACULTY_SECRETARY',
    status: 'SUBMITTED',
    reviewer: null,
    submittedAt: '2026-11-10T08:00:00Z',
    deadline: null,
    overdue: false,
    overdueNoticedAt: null,
    documentCount: 1,
    delegatedFrom: null,
    canTakeOver: null,
    canEscalate: null,
    ...overrides,
  } as ReviewItem;
}

function faculty(): UnitAnalytics {
  const data = facultyAnalytics();
  const row = data.units[1];
  return {
    ...data,
    units: [
      ...data.units,
      { ...row, unit: GEOMETRY, approved: figure(0, 5), byYear: [6, 8, 0] },
      {
        ...row,
        unit: TOPOLOGY,
        approved: figure(0, 2),
        byYear: [1, 2, 0],
        lastReminderAt: '2026-11-16T09:00:00Z',
      },
    ],
  };
}

describe('CommandCentreComponent', () => {
  let fixture: ComponentFixture<CommandCentreComponent>;
  let http: HttpTestingController;
  let store: Store;
  let held: {
    heldIds: ReturnType<typeof signal<ReadonlySet<number>>>;
    sent: Subject<DecisionOutcome>;
    returned: Subject<ReturnedDecision>;
    hold: ReturnType<typeof vi.fn>;
  };
  let open: ReturnType<typeof vi.fn>;
  let dialogResult: unknown;
  let snack: ReturnType<typeof vi.fn>;

  async function setUp(claims: Record<string, unknown>) {
    reduceMotion();
    held = {
      heldIds: signal<ReadonlySet<number>>(new Set()),
      sent: new Subject(),
      returned: new Subject(),
      hold: vi.fn(),
    };
    dialogResult = undefined;
    snack = vi.fn();
    open = vi.fn(() => ({ afterClosed: () => of(dialogResult) }));
    const token = tokenWith(claims);
    await TestBed.configureTestingModule({
      imports: [CommandCentreComponent, NoopAnimationsModule, translations('en')],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideStore({}),
        provideState(preferencesFeature),
        languageStub('en').provider,
        { provide: HeldDecisionService, useValue: held },
        {
          provide: AuthService,
          useValue: {
            permissions: signal(readPermissions(token)),
            userId: signal(String(claims['sub'])),
          },
        },
      ],
    })
      .overrideProvider(MatDialog, { useValue: { open } })
      .overrideProvider(MatSnackBar, { useValue: { open: snack } })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(Store);
    store.dispatch(
      PreferencesActions.loaded({
        preferences: { colorScheme: 'SYSTEM', layouts: {} },
      }),
    );
    fixture = TestBed.createComponent(CommandCentreComponent);
    fixture.detectChanges();
  }

  afterEach(() => vi.unstubAllGlobals());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function all(testId: string): HTMLElement[] {
    return [...fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`)];
  }

  function widgets(): string[] {
    return [...fixture.nativeElement.querySelectorAll('[data-testid^="widget-"]')].map((node) =>
      (node as HTMLElement).dataset['testid']!.replace('widget-', ''),
    );
  }

  function flushQueue(items: ReviewItem[], total = items.length) {
    const request = http.expectOne((r) => r.url.endsWith('/reviews'));
    request.flush({
      content: items,
      totalElements: total,
      totalPages: 1,
      size: QUEUE_SIZE,
      number: 0,
    });
    return request.request;
  }

  function flushDay(decided: number) {
    http
      .expectOne((r) => r.url.endsWith('/reviews/today'))
      .flush({ decided, since: '2026-11-17T22:00:00Z' });
  }

  function flushFaculty(data: UnitAnalytics = faculty()) {
    const request = http.expectOne((r) => r.url.endsWith('/analytics/units/9'));
    request.flush(data);
    fixture.detectChanges();
    return request.request;
  }

  it('ac5_1_ac5_7_shows_the_four_widgets_the_waiting_count_the_decisions_of_today_and_three_requests', async () => {
    await setUp(SECRETARY);

    const queue = flushQueue([reviewItem(1), reviewItem(2), reviewItem(3)], 7);
    flushDay(4);
    const analytics = flushFaculty();

    expect(queue.params.get('level')).toBe('FACULTY_SECRETARY');
    expect(queue.params.get('size')).toBe(String(QUEUE_SIZE));
    expect(analytics.params.keys()).toEqual([]);
    expect(widgets()).toEqual(['TODAY', 'QUEUE', 'UNITS', 'TOP']);
    expect(element('today-waiting')?.textContent).toContain('7');
    expect(element('today-decided')?.textContent).toContain('4');
    expect(all('queue-item')).toHaveLength(3);
    expect(all('queue-approve').map((button) => button.textContent?.trim())).toEqual([
      'Approve',
      'Approve',
      'Approve',
    ]);
    expect(element('centre-unit')?.textContent).toContain('Faculty of Mathematics');
  });

  it('ac5_1_holds_a_confirmed_decision_hides_the_request_and_refreshes_after_the_send', async () => {
    await setUp(SECRETARY);
    flushQueue(
      [reviewItem(1), reviewItem(2, { reviewer: { id: 99, name: 'Other', email: 'o@x' } })],
      2,
    );
    flushDay(0);
    flushFaculty();
    dialogResult = { comment: undefined };

    all('queue-approve')[0].click();

    expect(open).toHaveBeenCalledWith(DecisionDialogComponent, expect.anything());
    expect(held.hold).toHaveBeenCalledWith(1, {
      decision: 'APPROVE',
      requestVersion: 2,
      comment: undefined,
    });
    expect((all('queue-return')[1] as HTMLButtonElement).disabled).toBe(true);

    held.heldIds.set(new Set([1]));
    fixture.detectChanges();
    expect(all('queue-item')).toHaveLength(1);
    expect(element('today-waiting')?.textContent).toContain('1');

    held.sent.next({} as DecisionOutcome);
    flushQueue([reviewItem(2)], 1);
    flushDay(1);
    fixture.detectChanges();
    expect(element('today-decided')?.textContent).toContain('1');
    http.verify();
  });

  it('ac5_2_ac5_4_lists_departments_without_awards_first_with_earlier_years_and_disables_a_recent_reminder', async () => {
    await setUp(SECRETARY);
    flushQueue([]);
    flushDay(0);
    flushFaculty();

    const geometry = element('focus-unit-44')!;
    expect(geometry.textContent).toContain('No award in 2026 · 2024–2025: 14');
    expect(geometry.querySelector('button')?.textContent?.trim()).toBe('Remind the head');
    const topology = element('focus-unit-45')!.querySelector('button')!;
    expect(topology.disabled).toBe(true);
    expect(topology.textContent?.trim()).toBe('Reminded 16/11/2026');
    expect(element('widget-TOP')?.textContent).toContain('Olena Koval');
  });

  it('ac5_3_opens_the_reminder_and_marks_the_card_reminded', async () => {
    await setUp(SECRETARY);
    flushQueue([]);
    flushDay(0);
    flushFaculty();
    dialogResult = { sentAt: '2026-11-18T10:00:00Z' };

    element('focus-unit-44')!.querySelector('button')!.click();
    fixture.detectChanges();

    expect(open).toHaveBeenCalledWith(
      ReminderDialogComponent,
      expect.objectContaining({ data: { unit: GEOMETRY, year: 2026 } }),
    );
    const button = element('focus-unit-44')!.querySelector('button')!;
    expect(button.disabled).toBe(true);
    expect(button.textContent?.trim()).toBe('Reminded 18/11/2026');
    expect(snack).toHaveBeenCalledWith('Reminded 18/11/2026', undefined, expect.anything());
  });

  it('ac5_6_lists_the_faculties_for_a_rector_secretary_without_a_reminder', async () => {
    await setUp(RECTOR_SECRETARY);

    const queue = flushQueue([]);
    flushDay(0);
    const data = universityAnalytics();
    data.units = [
      ...data.units,
      { ...data.units[0], unit: { ...data.units[0].unit, id: 77 }, byYear: [3, 4, 5, 6, 0] },
    ];
    http.expectOne((r) => r.url.endsWith('/analytics/university')).flush(data);
    fixture.detectChanges();

    expect(queue.params.get('level')).toBe('RECTOR_SECRETARY');
    expect(element('centre-units')?.textContent).toContain('Faculties that need attention');
    expect(element('focus-unit-77')?.querySelector('a, button')).toBeNull();
    expect(element('widget-TOP')?.textContent).toContain('Leading faculties');
  });

  it('ac5_7_follows_the_command_centre_layout_of_the_account', async () => {
    await setUp(SECRETARY);
    const layout: DashboardLayout = { order: ['QUEUE', 'TODAY'], hidden: ['TOP'] };
    store.dispatch(
      PreferencesActions.loaded({
        preferences: { colorScheme: 'SYSTEM', layouts: { COMMAND_CENTRE: layout } },
      }),
    );
    flushQueue([]);
    flushDay(0);
    flushFaculty();

    expect(widgets()).toEqual(['QUEUE', 'TODAY', 'UNITS']);
  });

  it('shows_a_retry_when_a_read_fails', async () => {
    await setUp(SECRETARY);
    flushQueue([]);
    flushDay(0);
    http
      .expectOne((r) => r.url.endsWith('/analytics/units/9'))
      .flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element('centre-error')?.textContent).toContain('could not be loaded');
    element('centre-error')!.querySelector('button')!.click();
    flushQueue([]);
    flushDay(0);
    flushFaculty();
    expect(element('centre-error')).toBeNull();
  });
});
