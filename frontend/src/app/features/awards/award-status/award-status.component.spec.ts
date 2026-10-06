import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { AwardStatusView, AwardsService } from '../awards.service';
import { AwardStatusComponent } from './award-status.component';

const submitted: AwardStatusView = {
  awardId: 5,
  status: 'PENDING',
  requestStatus: 'SUBMITTED',
  currentLevel: 'FACULTY_SECRETARY',
  submittedAt: '2026-09-28T09:00:00Z',
  deadline: '2026-10-01T09:00:00Z',
  estimatedCompletion: '2026-10-04',
  overdue: false,
  completedAt: null,
  rejectionReason: null,
  delay: null,
  path: [
    { level: 'FACULTY_SECRETARY', state: 'CURRENT', dueDate: '2026-10-01', completedAt: null },
    { level: 'DEAN', state: 'UPCOMING', dueDate: '2026-10-04', completedAt: null },
  ],
  decisions: [],
};

const returned: AwardStatusView = {
  ...submitted,
  requestStatus: 'RETURNED',
  estimatedCompletion: null,
  path: submitted.path.map((step) => ({ ...step, dueDate: null })),
  decisions: [
    {
      id: 1,
      decision: 'RETURNED',
      level: 'FACULTY_SECRETARY',
      reviewerId: 30,
      reviewerName: 'Аліна Мартинюк',
      comments: 'Додайте номер наказу',
      decidedAt: '2026-09-30T10:15:00Z',
      delegatorId: null,
      delegatorName: null,
    },
  ],
};

const approved: AwardStatusView = {
  ...submitted,
  status: 'APPROVED',
  requestStatus: 'APPROVED',
  currentLevel: 'DEAN',
  estimatedCompletion: null,
  completedAt: '2026-10-02T12:00:00Z',
  path: [
    {
      level: 'FACULTY_SECRETARY',
      state: 'DONE',
      dueDate: null,
      completedAt: '2026-09-29T10:00:00Z',
    },
    { level: 'DEAN', state: 'DONE', dueDate: null, completedAt: '2026-10-02T12:00:00Z' },
  ],
};

const translations = {
  uk: {
    awards: {
      statusPanel: {
        title: 'Статус розгляду',
        submitted: 'Подано',
        upcomingBy: 'до {{date}}',
        estimate: 'Орієнтовне завершення: {{date}}',
        overdue: 'Розгляд триває довше, ніж зазвичай (з {{since}}). Нова орієнтовна дата: {{date}}',
        noReviewer: 'Зараз немає працівника на посаді «{{level}}» для вашого підрозділу.',
        returned: 'Очікує ваших виправлень',
        completed: 'Розгляд завершено: {{date}}',
        rejectionReason: 'Причина відхилення: {{reason}}',
        decisions: 'Рішення',
        skipped: 'пропущено',
        onBehalf: '(за дорученням {{name}})',
        decision: {
          RETURNED: 'Повернуто на доопрацювання',
          APPROVED: 'Схвалено',
          ESCALATED: 'Передано на вищий рівень',
        },
        updated: 'Статус розгляду оновлено',
        retry: 'Спробувати ще раз',
        problems: {
          failed: 'Не вдалося оновити статус розгляду.',
          gone: 'Ця нагорода вам більше не доступна.',
          denied: 'Ви більше не маєте доступу до статусу розгляду цієї нагороди.',
        },
      },
      timing: { expected: 'Очікується до {{date}}' },
    },
    roles: {
      FACULTY_SECRETARY: 'Секретар факультету',
      DEAN: 'Декан',
      RECTOR_SECRETARY: 'Секретар ректора',
    },
  },
};

function failure(status: number): HttpErrorResponse {
  return new HttpErrorResponse({ status });
}

describe('AwardStatusComponent', () => {
  const service = { status: vi.fn() };
  let visibility: DocumentVisibilityState;
  let fixture: ComponentFixture<AwardStatusComponent>;
  let changes: AwardStatusView[] = [];

  let lost = 0;

  async function create(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [
        AwardStatusComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: AwardsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AwardStatusComponent);
    fixture.componentRef.setInput('awardId', 5);
    changes = [];
    fixture.componentInstance.changed.subscribe((view) => changes.push(view));
    lost = 0;
    fixture.componentInstance.lost.subscribe(() => lost++);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function tick(ms = 60_000): void {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
  }

  function setVisibility(state: DocumentVisibilityState): void {
    visibility = state;
    document.dispatchEvent(new Event('visibilitychange'));
    fixture.detectChanges();
  }

  function text(element: HTMLElement, testId: string): string | undefined {
    return element.querySelector(`[data-testid="${testId}"]`)?.textContent?.replace(/\s+/g, ' ');
  }

  beforeEach(() => {
    vi.useFakeTimers();
    visibility = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    service.status.mockReset();
  });

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('ac1_12_shows_the_path_with_the_current_level_and_the_estimate', async () => {
    service.status.mockReturnValue(of(submitted));
    const element = await create();
    const steps = element.querySelectorAll('[data-testid="award-status-step"]');

    expect(service.status).toHaveBeenCalledWith(5);
    expect(text(element, 'award-status-path')).toContain('Подано');
    expect(steps.length).toBe(2);
    expect(steps[0].getAttribute('aria-current')).toBe('step');
    expect(steps[0].textContent).toContain('Очікується до 01.10.2026');
    expect(steps[1].textContent).toContain('Декан');
    expect(text(element, 'award-status-estimate')).toContain('Орієнтовне завершення: 04.10.2026');
    expect(element.querySelector('[data-testid="award-status-delay"]')).toBeNull();
  });

  it('edge_an_award_without_request_hides_the_panel_and_does_not_poll', async () => {
    service.status.mockReturnValue(
      of({ ...submitted, requestStatus: null, currentLevel: null, submittedAt: null, path: [] }),
    );
    await create();

    tick(300_000);

    expect((fixture.nativeElement as HTMLElement).hidden).toBe(true);
    expect(service.status).toHaveBeenCalledTimes(1);
  });

  it('ac1_13_explains_an_overdue_review_with_the_new_date', async () => {
    service.status.mockReturnValue(
      of({
        ...submitted,
        overdue: true,
        estimatedCompletion: '2026-10-09',
        delay: { reason: 'REVIEW_OVERDUE', since: '2026-10-01T09:00:00Z' },
      }),
    );
    const element = await create();

    expect(text(element, 'award-status-delay')).toContain(
      'Розгляд триває довше, ніж зазвичай (з 01.10.2026). Нова орієнтовна дата: 09.10.2026',
    );
  });

  it('ac1_13_names_the_level_without_a_reviewer', async () => {
    service.status.mockReturnValue(
      of({ ...submitted, delay: { reason: 'NO_REVIEWER', since: null } }),
    );
    const element = await create();

    expect(text(element, 'award-status-delay')).toContain(
      'Зараз немає працівника на посаді «Секретар факультету»',
    );
  });

  it('ac1_14_a_returned_request_shows_the_decision_and_waits_for_corrections', async () => {
    service.status.mockReturnValue(of(returned));
    const element = await create();

    expect(text(element, 'award-status-returned')).toContain('Очікує ваших виправлень');
    expect(element.querySelector('[data-testid="award-status-estimate"]')).toBeNull();
    expect(text(element, 'award-status-decision')).toContain('Повернуто на доопрацювання');
    expect(text(element, 'award-status-decision')).toContain('Аліна Мартинюк');
    expect(text(element, 'award-status-decision')).toContain('30.09.2026, 13:15');
    expect(text(element, 'award-status-comment')).toContain('Додайте номер наказу');
  });

  it('ac2_7_ac2_12_a_delegated_escalation_names_the_delegator_and_a_skipped_level', async () => {
    service.status.mockReturnValue(
      of({
        ...submitted,
        requestStatus: 'ESCALATED',
        currentLevel: 'RECTOR_SECRETARY',
        path: [
          {
            level: 'FACULTY_SECRETARY',
            state: 'DONE',
            dueDate: null,
            completedAt: '2026-09-29T10:00:00Z',
          },
          { level: 'DEAN', state: 'SKIPPED', dueDate: null, completedAt: null },
          { level: 'RECTOR_SECRETARY', state: 'CURRENT', dueDate: '2026-10-08', completedAt: null },
        ],
        decisions: [
          {
            ...returned.decisions[0],
            decision: 'ESCALATED',
            comments: 'Національна відзнака',
            delegatorId: 31,
            delegatorName: 'Ірина Секретар',
          },
        ],
      } satisfies AwardStatusView),
    );
    const element = await create();

    const steps = element.querySelectorAll('[data-testid="award-status-step"]');
    expect(steps[1].getAttribute('data-state')).toBe('SKIPPED');
    expect(text(element, 'award-status-skipped')).toContain('пропущено');
    expect(text(element, 'award-status-decision')).toContain(
      'Аліна Мартинюк (за дорученням Ірина Секретар)',
    );
    expect(text(element, 'award-status-comment')).toContain('Національна відзнака');
  });

  it('ac1_14_a_rejected_request_shows_its_reason_and_completion', async () => {
    service.status.mockReturnValue(
      of({
        ...approved,
        status: 'REJECTED',
        requestStatus: 'REJECTED',
        rejectionReason: 'Не нагорода університету',
      }),
    );
    const element = await create();

    expect(text(element, 'award-status-rejection')).toContain(
      'Причина відхилення: Не нагорода університету',
    );
    expect(text(element, 'award-status-completed')).toContain('02.10.2026');
  });

  it('ac1_14_the_level_that_rejected_is_not_marked_as_in_progress', async () => {
    service.status.mockReturnValue(
      of({
        ...submitted,
        status: 'REJECTED',
        requestStatus: 'REJECTED',
        estimatedCompletion: null,
        completedAt: '2026-10-02T12:00:00Z',
        path: submitted.path.map((step) => ({ ...step, dueDate: null })),
      }),
    );
    const element = await create();
    const steps = element.querySelectorAll('[data-testid="award-status-step"]');

    expect(steps[0].getAttribute('aria-current')).toBeNull();
    expect(steps[0].classList).toContain('award-status__step--STOPPED');
  });

  it('ac1_15_reloads_every_minute_and_announces_a_change', async () => {
    service.status.mockReturnValue(of(submitted));
    const element = await create();
    expect(text(element, 'award-status-announcement')?.trim()).toBe('');

    tick();
    expect(service.status).toHaveBeenCalledTimes(2);
    expect(text(element, 'award-status-announcement')?.trim()).toBe('');

    service.status.mockReturnValue(of(returned));
    tick();

    expect(service.status).toHaveBeenCalledTimes(3);
    const first = element.querySelector('[data-testid="award-status-announcement"]')?.textContent;
    expect(first).toContain('Статус розгляду оновлено');
    expect(text(element, 'award-status-returned')).toContain('Очікує ваших виправлень');
    expect(changes).toHaveLength(1);

    service.status.mockReturnValue(of(submitted));
    tick();

    const second = element.querySelector('[data-testid="award-status-announcement"]')?.textContent;
    expect(second).toContain('Статус розгляду оновлено');
    expect(second).not.toBe(first);
    expect(changes).toHaveLength(2);
  });

  it('ac1_15_pauses_while_the_tab_is_hidden_and_reloads_once_on_return', async () => {
    service.status.mockReturnValue(of(submitted));
    await create();

    setVisibility('hidden');
    tick(180_000);
    expect(service.status).toHaveBeenCalledTimes(1);

    setVisibility('visible');
    expect(service.status).toHaveBeenCalledTimes(2);
  });

  it('ac1_15_stops_on_a_final_status', async () => {
    service.status.mockReturnValue(of(approved));
    const element = await create();

    tick(300_000);
    setVisibility('hidden');
    setVisibility('visible');

    expect(service.status).toHaveBeenCalledTimes(1);
    expect(text(element, 'award-status-completed')).toContain('Розгляд завершено: 02.10.2026');
  });

  it('ac1_15_stops_when_the_award_is_no_longer_readable', async () => {
    service.status.mockReturnValue(of(returned));
    const element = await create();

    service.status.mockReturnValue(throwError(() => failure(404)));
    tick();
    tick(300_000);

    expect(service.status).toHaveBeenCalledTimes(2);
    expect(text(element, 'award-status-error')).toContain('Ця нагорода вам більше не доступна.');
    expect(element.querySelector('[data-testid="award-status-retry"]')).toBeNull();
    expect(element.querySelector('[data-testid="award-status-path"]')).toBeNull();
    expect(element.querySelector('[data-testid="award-status-decisions"]')).toBeNull();
    expect(element.hidden).toBe(false);
    expect(lost).toBe(1);
  });

  it('ac1_15_a_revoked_access_clears_the_timeline_and_says_so', async () => {
    service.status.mockReturnValue(of(returned));
    const element = await create();

    service.status.mockReturnValue(throwError(() => failure(403)));
    tick();
    tick(300_000);

    expect(service.status).toHaveBeenCalledTimes(2);
    expect(text(element, 'award-status-error')).toContain(
      'Ви більше не маєте доступу до статусу розгляду цієї нагороди.',
    );
    expect(element.querySelector('[data-testid="award-status-comment"]')).toBeNull();
    expect(element.hidden).toBe(false);
  });

  it('ac1_15_a_changed_delay_reason_is_announced', async () => {
    service.status.mockReturnValue(
      of({ ...submitted, overdue: true, delay: { reason: 'NO_REVIEWER', since: null } }),
    );
    const element = await create();

    service.status.mockReturnValue(
      of({
        ...submitted,
        overdue: true,
        delay: { reason: 'REVIEW_OVERDUE', since: '2026-10-01T09:00:00Z' },
      }),
    );
    tick();

    expect(text(element, 'award-status-announcement')).toContain('Статус розгляду оновлено');
    expect(changes).toHaveLength(1);
  });

  it('ac1_16_a_failure_offers_a_retry_and_polling_goes_on', async () => {
    service.status.mockReturnValue(throwError(() => failure(503)));
    const element = await create();

    expect(text(element, 'award-status-error')).toContain('Не вдалося оновити статус розгляду.');
    service.status.mockReturnValue(of(submitted));
    (element.querySelector('[data-testid="award-status-retry"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="award-status-error"]')).toBeNull();
    expect(text(element, 'award-status-estimate')).toContain('04.10.2026');

    service.status.mockReturnValue(throwError(() => failure(0)));
    tick();
    expect(text(element, 'award-status-error')).toContain('Не вдалося');
    expect(text(element, 'award-status-estimate')).toContain('04.10.2026');

    service.status.mockReturnValue(of(submitted));
    tick();
    expect(service.status).toHaveBeenCalledTimes(4);
    expect(element.querySelector('[data-testid="award-status-error"]')).toBeNull();
  });
});
