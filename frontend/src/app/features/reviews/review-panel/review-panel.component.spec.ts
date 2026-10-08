import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { ReviewItem, ReviewsService } from '../reviews.service';
import { ReviewPanelComponent } from './review-panel.component';

const SELF = { id: 31, name: 'Ірина Секретар', email: 'secretary.fmi@chnu.edu.ua' };
const PEER = { id: 32, name: 'Олена Петрук', email: 'secretary2.fmi@chnu.edu.ua' };

function item(overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    awardId: 5,
    requestId: 8,
    requestVersion: 3,
    title: null,
    titleUk: 'Грамота',
    recipient: { type: 'PERSON', organization: null },
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    organization: null,
    category: null,
    level: 'FACULTY_SECRETARY',
    status: 'SUBMITTED',
    reviewer: null,
    submittedAt: '2026-10-01T08:00:00Z',
    deadline: '2026-10-06T20:59:59Z',
    overdue: true,
    overdueNoticedAt: null,
    documentCount: 0,
    delegatedFrom: null,
    ...overrides,
  };
}

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

function conflict(type: string, body: Record<string, unknown> = {}): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 409,
    error: { type: `urn:awards:problem:${type}`, ...body },
  });
}

const translations = {
  uk: {
    reviews: {
      overdue: 'Прострочено',
      panel: {
        title: 'Розгляд',
        nobody: 'Ще ніхто не взяв у роботу',
        claim: 'Взяти в роботу',
        release: 'Звільнити',
        handOver: 'Передати колезі…',
        takeOver: 'Взяти на себе',
        approve: 'Затвердити',
        return: 'Повернути на доопрацювання',
        reject: 'Відхилити',
        escalate: 'Передати {{level}}',
      },
      decide: { to: { DEAN: 'декану', RECTOR_SECRETARY: 'секретарю ректора' } },
      messages: { claimed: 'Нагороду взято в роботу.', released: 'Нагороду повернуто до черги.' },
      problems: {
        'request-claimed': 'Нагороду вже взяв у роботу {{name}}',
        'request-stale': 'Дані застаріли, сторінку оновлено',
        'no-higher-level': 'Вищого рівня розгляду немає',
        unknown: 'Не вдалося виконати дію.',
      },
    },
  },
};

describe('ReviewPanelComponent', () => {
  let fixture: ComponentFixture<ReviewPanelComponent>;
  const permissions = signal(readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] })));
  const service = {
    item: vi.fn(),
    claim: vi.fn(),
    release: vi.fn(),
    handOver: vi.fn(),
    decide: vi.fn(),
  };
  const dialog = { open: vi.fn() };

  async function create(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [
        ReviewPanelComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: ReviewsService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { permissions, userId: () => String(SELF.id) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReviewPanelComponent);
    fixture.componentRef.setInput('awardId', 5);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(element: HTMLElement, id: string): string {
    return element.querySelector(`[data-testid="${id}"]`)?.textContent?.trim() ?? '';
  }

  function click(element: HTMLElement, id: string): void {
    element.querySelector<HTMLElement>(`[data-testid="${id}"]`)?.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    Object.values(service).forEach((mock) => mock.mockReset());
    dialog.open.mockReset();
    permissions.set(readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] })));
  });

  it('ac1_11_shows_nothing_when_the_caller_may_not_review_the_request', async () => {
    service.item.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    const element = await create();

    expect(element.querySelector('[data-testid="review-panel"]')).toBeNull();
  });

  it('ac1_11_claims_an_unclaimed_request_with_its_version', async () => {
    service.item.mockReturnValue(of(item()));
    service.claim.mockReturnValue(of(item({ reviewer: SELF, requestVersion: 4 })));
    const changed = vi.fn();
    const element = await create();
    fixture.componentInstance.changed.subscribe(changed);

    expect(text(element, 'review-panel-reviewer')).toBe('Ще ніхто не взяв у роботу');
    expect(text(element, 'review-panel-deadline')).toContain('Прострочено');
    click(element, 'review-claim');

    expect(service.claim).toHaveBeenCalledWith(5, 3);
    expect(text(element, 'review-panel-reviewer')).toBe(SELF.name);
    expect(element.querySelector('[data-testid="review-release"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="review-hand-over"]')).not.toBeNull();
    expect(changed).toHaveBeenCalled();
  });

  it('ac1_11_releases_an_own_request_and_reads_it_again', async () => {
    service.item.mockReturnValueOnce(of(item({ reviewer: SELF }))).mockReturnValue(of(item()));
    service.release.mockReturnValue(of(undefined));
    const element = await create();

    click(element, 'review-release');

    expect(service.release).toHaveBeenCalledWith(5, 3);
    expect(text(element, 'review-panel-notice')).toBe('Нагороду повернуто до черги.');
    expect(element.querySelector('[data-testid="review-claim"]')).not.toBeNull();
  });

  it('ac1_11_hands_an_own_request_to_the_chosen_colleague', async () => {
    service.item.mockReturnValue(of(item({ reviewer: SELF })));
    service.handOver.mockReturnValue(of(item({ reviewer: PEER })));
    dialog.open.mockReturnValue({ afterClosed: () => of({ ...PEER, delegated: false }) });
    const element = await create();

    click(element, 'review-hand-over');

    expect(service.handOver).toHaveBeenCalledWith(5, 3, PEER.id);
    expect(text(element, 'review-panel-reviewer')).toBe(PEER.name);
  });

  it('ac1_11_a_peer_cannot_take_over_but_a_dean_can_after_confirming', async () => {
    service.item.mockReturnValue(of(item({ reviewer: PEER })));
    let element = await create();
    expect(element.querySelector('[data-testid="review-take-over"]')).toBeNull();

    TestBed.resetTestingModule();
    permissions.set(readPermissions(token({ role_scopes: ['DEAN:9'] })));
    service.claim.mockReturnValue(of(item({ reviewer: SELF })));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    element = await create();
    click(element, 'review-take-over');

    expect(dialog.open.mock.calls[0][1].data.params).toEqual({ name: PEER.name });
    expect(service.claim).toHaveBeenCalledWith(5, 3, true);
  });

  it('ac1_11_a_claim_conflict_names_the_reviewer_and_reloads', async () => {
    service.item
      .mockReturnValueOnce(of(item()))
      .mockReturnValue(of(item({ reviewer: PEER, requestVersion: 4 })));
    service.claim.mockReturnValue(
      throwError(() => conflict('request-claimed', { reviewer: PEER })),
    );
    const element = await create();

    click(element, 'review-claim');

    expect(text(element, 'review-panel-notice')).toBe(`Нагороду вже взяв у роботу ${PEER.name}`);
    expect(text(element, 'review-panel-reviewer')).toBe(PEER.name);
    expect(service.item).toHaveBeenCalledTimes(2);
  });

  it('ac1_11_a_stale_version_or_a_vanished_holder_says_the_page_was_refreshed', async () => {
    service.item.mockReturnValue(of(item({ reviewer: SELF })));
    service.release
      .mockReturnValueOnce(throwError(() => conflict('request-stale', { currentVersion: 5 })))
      .mockReturnValueOnce(throwError(() => conflict('request-claimed', { reviewer: null })));
    const element = await create();

    click(element, 'review-release');
    expect(text(element, 'review-panel-notice')).toBe('Дані застаріли, сторінку оновлено');

    click(element, 'review-release');
    expect(text(element, 'review-panel-notice')).toBe('Дані застаріли, сторінку оновлено');
  });

  it('ac1_11_any_other_failure_keeps_the_panel_and_reports_it', async () => {
    service.item.mockReturnValue(of(item()));
    service.claim.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const element = await create();

    click(element, 'review-claim');

    expect(text(element, 'review-panel-notice')).toBe('Не вдалося виконати дію.');
    expect(service.item).toHaveBeenCalledTimes(1);
  });

  it('ac2_11_offers_the_four_decisions_on_an_own_or_unclaimed_request_only', async () => {
    service.item.mockReturnValue(of(item({ reviewer: PEER })));
    let element = await create();
    expect(element.querySelector('[data-testid="review-decisions"]')).toBeNull();

    TestBed.resetTestingModule();
    service.item.mockReturnValue(of(item()));
    element = await create();

    expect(text(element, 'review-approve')).toBe('Затвердити');
    expect(text(element, 'review-return')).toBe('Повернути на доопрацювання');
    expect(text(element, 'review-reject')).toBe('Відхилити');
    expect(text(element, 'review-escalate')).toBe('Передати декану');
  });

  it('ac2_11_sends_the_dialog_input_with_the_version_and_emits_the_outcome', async () => {
    const outcome = {
      awardId: 5,
      status: 'PENDING',
      requestStatus: 'ESCALATED',
      level: 'DEAN',
      requestVersion: 4,
    };
    service.item.mockReturnValue(of(item({ reviewer: SELF, documentCount: 2 })));
    service.decide.mockReturnValue(of(outcome));
    dialog.open.mockReturnValue({ afterClosed: () => of({ comment: 'Варто декану' }) });
    const decided = vi.fn();
    const element = await create();
    fixture.componentInstance.decided.subscribe(decided);

    click(element, 'review-escalate');

    expect(dialog.open.mock.calls[0][1].data).toEqual({
      decision: 'ESCALATE',
      target: 'DEAN',
      documents: 2,
    });
    expect(service.decide).toHaveBeenCalledWith(5, {
      decision: 'ESCALATE',
      requestVersion: 3,
      comment: 'Варто декану',
    });
    expect(decided).toHaveBeenCalledWith(outcome);
    expect(element.querySelector('[data-testid="review-panel"]')).toBeNull();
  });

  it('ac2_11_a_cancelled_dialog_decides_nothing', async () => {
    service.item.mockReturnValue(of(item({ reviewer: SELF })));
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });
    const element = await create();

    click(element, 'review-reject');

    expect(service.decide).not.toHaveBeenCalled();
  });

  it('ac2_5_the_top_level_offers_no_escalation_and_a_refusal_is_reported', async () => {
    permissions.set(readPermissions(token({ role_scopes: ['RECTOR:1'] })));
    service.item.mockReturnValue(of(item({ reviewer: SELF, level: 'RECTOR' })));
    service.decide.mockReturnValue(throwError(() => conflict('no-higher-level')));
    dialog.open.mockReturnValue({ afterClosed: () => of({}) });
    const element = await create();

    expect(element.querySelector('[data-testid="review-escalate"]')).toBeNull();
    click(element, 'review-approve');

    expect(text(element, 'review-panel-notice')).toBe('Вищого рівня розгляду немає');
  });
});
