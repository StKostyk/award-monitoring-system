import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { beforeEach, vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { DocumentsService } from '../../awards/award-documents/documents.service';
import { AwardStatusComponent } from '../../awards/award-status/award-status.component';
import { AwardsService } from '../../awards/awards.service';
import { DecisionDialogData, DecisionInput } from '../decision-dialog/decision-dialog.component';
import { HeldDecisionService } from '../held-decision.service';
import { ReviewItem, ReviewsService } from '../reviews.service';
import { ReviewQueueComponent } from './review-queue.component';

function reviewItem(overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    awardId: 5,
    requestId: 8,
    requestVersion: 2,
    title: null,
    titleUk: 'Грамота МОН',
    recipient: { type: 'PERSON', organization: null },
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    organization: null,
    category: { id: 13, name: 'Ministry', nameUk: 'Відзнака міністерства', level: 'NATIONAL' },
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

@Component({ selector: 'app-award-status', template: '<p data-testid="route">{{ awardId() }}</p>' })
class AwardStatusStubComponent {
  readonly awardId = input.required<number>();
}

const notFound = new HttpErrorResponse({ status: HttpStatusCode.NotFound });

const translations = {
  uk: {
    reviews: {
      queue: {
        left: 'Залишилось: {{count}}',
        empty: 'Черга порожня',
        checks: {
          readable: 'Документ читається',
          match: 'Назва й дата збігаються',
          level: 'Рівень визначено правильно',
        },
        shortcuts: 'J / K — наступна / попередня · A — затвердити · R — повернути',
        heldBy: 'Розглядає {{name}}',
        notWaiting: 'Цей запит уже не чекає на вашому рівні',
        notFound: 'Запит не знайдено',
        loadFailed: 'Не вдалося завантажити запит',
        handOver: 'Передати колезі…',
      },
      panel: { escalate: 'Передати {{level}}' },
      decide: { to: { DEAN: 'декану' } },
      messages: { handedOver: 'Передано' },
    },
    awards: { status: { APPROVED: 'Затверджено' } },
  },
};

describe('ReviewQueueComponent', () => {
  let fixture: ComponentFixture<ReviewQueueComponent>;
  let element: HTMLElement;
  let confirmed: DecisionInput | undefined;
  const held = { hold: vi.fn(), heldIds: signal<ReadonlySet<number>>(new Set()) };
  const awards = { get: vi.fn() };
  const documents = { list: vi.fn(() => of([])) };
  const dialog = { open: vi.fn() };
  const reviews = { item: vi.fn(), claim: vi.fn(), handOver: vi.fn() };
  const snackBar = { open: vi.fn() };
  const items = [
    reviewItem(),
    reviewItem({ awardId: 6, requestId: 9, titleUk: 'Подяка ректора' }),
    reviewItem({
      awardId: 7,
      requestId: 10,
      titleUk: 'Диплом',
      reviewer: { id: 30, name: 'Олена', email: 'olena@chnu.edu.ua' },
    }),
  ];

  beforeEach(async () => {
    held.hold.mockReset();
    held.heldIds.set(new Set());
    awards.get.mockReset();
    awards.get.mockImplementation((id: number) =>
      of({
        id,
        status: 'APPROVED',
        titleUk: 'Старий запит',
        awardingOrganization: 'МОН України',
        awardDate: '2025-05-01',
      }),
    );
    reviews.item.mockReset();
    reviews.item.mockImplementation((id: number) => {
      const found = items.find((item) => item.awardId === id);
      return found ? of(found) : throwError(() => notFound);
    });
    reviews.claim.mockReset();
    reviews.handOver.mockReset();
    snackBar.open.mockReset();
    dialog.open.mockReset();
    confirmed = { comment: 'Добре', verified: true };
    dialog.open.mockImplementation(
      (_component, config: { data: DecisionDialogData<DecisionInput> }) => ({
        afterClosed: () => (confirmed ? config.data.submit(confirmed) : of(undefined)),
      }),
    );
    await TestBed.configureTestingModule({
      imports: [
        ReviewQueueComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: HeldDecisionService, useValue: held },
        { provide: AwardsService, useValue: awards },
        { provide: ReviewsService, useValue: reviews },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: DocumentsService, useValue: documents },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { userId: signal('21') } },
      ],
    })
      .overrideComponent(ReviewQueueComponent, {
        remove: { imports: [AwardStatusComponent] },
        add: { imports: [AwardStatusStubComponent] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(ReviewQueueComponent);
    fixture.componentRef.setInput('items', items);
    fixture.componentRef.setInput('selectedId', 5);
    fixture.componentInstance.selectedChange.subscribe((id) =>
      fixture.componentRef.setInput('selectedId', id),
    );
    fixture.detectChanges();
    element = fixture.nativeElement;
  });

  function text(testId: string): string {
    fixture.detectChanges();
    return element.querySelector(`[data-testid="${testId}"]`)?.textContent?.trim() ?? '';
  }

  function press(
    code: string,
    init: KeyboardEventInit = {},
    target: EventTarget = document.body,
  ): void {
    target.dispatchEvent(new KeyboardEvent('keydown', { code, bubbles: true, ...init }));
    fixture.detectChanges();
  }

  it('ac5_1_shows_the_selected_request_with_document_fields_checklist_and_route', () => {
    expect(text('queue-open')).toBe('Грамота МОН');
    expect(text('route')).toBe('5');
    expect(text('queue-recipient')).toBe('Анастасія Коваль');
    expect(text('queue-organization')).toBe('МОН України');
    expect(text('queue-date')).toBe('01.05.2025');
    expect(documents.list).toHaveBeenCalledWith(5);
    expect(text('queue-checklist')).toContain('Документ читається');
    expect(text('queue-checklist')).toContain('Назва й дата збігаються');
    expect(text('queue-checklist')).toContain('Рівень визначено правильно');
    expect(text('queue-shortcuts')).toContain('J / K');
  });

  it('ac6_2_ticks_send_nothing_and_approval_needs_none', () => {
    const calls = awards.get.mock.calls.length;
    const check = element.querySelector(
      '[data-testid="queue-check-readable"] input',
    ) as HTMLInputElement;
    check.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.ticked().has('readable')).toBe(true);
    expect(awards.get.mock.calls.length).toBe(calls);
    expect(held.hold).not.toHaveBeenCalled();

    fixture.componentInstance.move(1);
    expect(fixture.componentInstance.ticked().size).toBe(0);
    fixture.componentInstance.decide('APPROVE');
    expect(held.hold).toHaveBeenCalled();
  });

  it('ac6_3_j_and_k_move_through_the_queue_a_and_r_open_the_dialogs', () => {
    press('KeyJ');
    expect(text('queue-open')).toBe('Подяка ректора');
    press('KeyK');
    expect(text('queue-open')).toBe('Грамота МОН');

    confirmed = undefined;
    press('KeyA');
    expect(dialog.open.mock.calls[0][1].data.decision).toBe('APPROVE');
    press('KeyR');
    expect(dialog.open.mock.calls[1][1].data.decision).toBe('RETURN');
  });

  it('ac14_shortcuts_keep_working_after_a_checklist_item_is_ticked', () => {
    const check = element.querySelector(
      '[data-testid="queue-check-readable"] input',
    ) as HTMLInputElement;
    check.click();
    check.focus();
    fixture.detectChanges();

    press('KeyJ', {}, check);
    expect(text('queue-open')).toBe('Подяка ректора');

    for (const type of ['radio', 'button', 'submit', 'reset', 'range', 'color', 'file', 'image']) {
      const control = document.createElement('input');
      control.type = type;
      document.body.appendChild(control);
      press('KeyK', {}, control);
      expect(text('queue-open')).toBe('Грамота МОН');
      press('KeyJ', {}, control);
      expect(text('queue-open')).toBe('Подяка ректора');
      control.remove();
    }
  });

  it('edge_shortcuts_are_ignored_while_typing_with_modifiers_and_over_a_dialog', () => {
    const input = document.createElement('textarea');
    document.body.appendChild(input);
    press('KeyJ', {}, input);
    for (const type of ['text', 'search', 'number']) {
      const field = document.createElement('input');
      field.type = type;
      document.body.appendChild(field);
      press('KeyJ', {}, field);
      field.remove();
    }
    press('KeyJ', { ctrlKey: true });
    const container = document.createElement('mat-dialog-container');
    document.body.appendChild(container);
    press('KeyJ');

    expect(text('queue-open')).toBe('Грамота МОН');
    input.remove();
    container.remove();
  });

  it('ac6_4_a_confirmed_decision_is_held_and_the_next_request_is_selected', () => {
    const decided: number[] = [];
    fixture.componentInstance.decided.subscribe((id) => decided.push(id));

    (element.querySelector('[data-testid="queue-approve"]') as HTMLButtonElement).click();

    expect(held.hold).toHaveBeenCalledWith(5, {
      decision: 'APPROVE',
      requestVersion: 2,
      comment: 'Добре',
      verified: true,
    });
    expect(decided).toEqual([5]);
    fixture.componentRef.setInput('items', items.slice(1));
    expect(fixture.componentInstance.selectedId()).toBe(6);
    expect(text('queue-open')).toBe('Подяка ректора');
  });

  it('ac5_2_the_last_decision_of_the_page_clears_the_selection', () => {
    const shown: (number | null)[] = [];
    fixture.componentRef.setInput('items', [items[0]]);
    fixture.componentInstance.selectedChange.subscribe((id) => shown.push(id));

    fixture.componentInstance.decide('APPROVE');

    expect(shown).toEqual([null]);
  });

  it('ac5_2_escalation_names_the_next_level_and_is_held_like_the_other_decisions', () => {
    expect(text('queue-escalate')).toBe('Передати декану');

    (element.querySelector('[data-testid="queue-escalate"]') as HTMLButtonElement).click();

    expect(dialog.open.mock.calls[0][1].data.target).toBe('DEAN');
    expect(held.hold).toHaveBeenCalledWith(5, expect.objectContaining({ decision: 'ESCALATE' }));
  });

  it('ac5_2_no_escalation_where_the_callers_role_already_covers_the_next_level', () => {
    reviews.item.mockReturnValue(of({ ...items[0], canEscalate: false }));
    fixture.componentRef.setInput('selectedId', 6);
    fixture.detectChanges();
    fixture.componentRef.setInput('selectedId', 5);

    expect(text('queue-open')).toBe('Грамота МОН');
    expect(element.querySelector('[data-testid="queue-escalate"]')).toBeNull();
  });

  it('ac5_2_hand_over_claims_an_unassigned_request_first', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of({ id: 40, name: 'Ігор' }) });
    reviews.claim.mockReturnValue(of({ ...items[0], requestVersion: 3 }));
    reviews.handOver.mockReturnValue(of(items[0]));
    const changed: boolean[] = [];
    fixture.componentInstance.changed.subscribe(() => changed.push(true));

    (element.querySelector('[data-testid="queue-hand-over"]') as HTMLButtonElement).click();

    expect(reviews.claim).toHaveBeenCalledWith(5, 2);
    expect(reviews.handOver).toHaveBeenCalledWith(5, 3, 40);
    expect(snackBar.open).toHaveBeenCalledWith('Передано', undefined, { duration: 5000 });
    expect(changed).toHaveLength(1);
  });

  it('ac5_3_a_request_no_longer_waiting_shows_its_state_without_actions', () => {
    fixture.componentRef.setInput('selectedId', 99);

    expect(text('queue-state')).toBe('Затверджено');
    expect(text('queue-not-waiting')).toContain('не чекає');
    expect(text('route')).toBe('99');
    expect(element.querySelector('[data-testid="queue-approve"]')).toBeNull();
    press('KeyA');
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('ac5_3_an_unknown_request_shows_not_found_in_the_pane', () => {
    awards.get.mockReturnValue(throwError(() => notFound));
    fixture.componentRef.setInput('selectedId', 98);

    expect(text('queue-not-found')).toContain('Запит не знайдено');
  });

  it('edge_a_failed_load_is_not_reported_as_a_missing_request', () => {
    awards.get.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: HttpStatusCode.ServiceUnavailable })),
    );
    fixture.componentRef.setInput('selectedId', 97);

    expect(text('queue-load-failed')).toContain('Не вдалося завантажити запит');
    expect(element.querySelector('[data-testid="queue-not-found"]')).toBeNull();
  });

  it('edge_a_request_whose_decision_is_held_cannot_be_decided_again', () => {
    held.heldIds.set(new Set([6]));
    fixture.componentRef.setInput('selectedId', 6);
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="queue-approve"]')?.hasAttribute('disabled')).toBe(
      true,
    );
    press('KeyA');
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('edge_a_list_refresh_replaces_the_shown_request', () => {
    const claimed = { ...items[0], reviewer: { id: 30, name: 'Олена', email: 'o@chnu.edu.ua' } };
    reviews.item.mockReturnValue(of(claimed));
    fixture.componentRef.setInput('items', [claimed, ...items.slice(1)]);

    expect(text('queue-held-by')).toBe('Розглядає Олена');
  });

  it('edge_k_without_a_selection_selects_nothing', () => {
    fixture.componentRef.setInput('selectedId', null);
    press('KeyK');

    expect(fixture.componentInstance.selectedId()).toBeNull();
  });

  it('ac5_4_j_from_a_request_outside_the_page_goes_to_the_first_one', () => {
    fixture.componentRef.setInput('selectedId', 99);
    press('KeyJ');

    expect(fixture.componentInstance.selectedId()).toBe(5);
  });

  it('edge_a_request_held_by_a_colleague_cannot_be_decided_here', () => {
    press('KeyJ');
    press('KeyJ');

    expect(text('queue-held-by')).toBe('Розглядає Олена');
    expect(element.querySelector('[data-testid="queue-approve"]')?.hasAttribute('disabled')).toBe(
      true,
    );
    press('KeyA');
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('ac6_7_an_empty_queue_says_so_with_a_check_mark', () => {
    fixture.componentRef.setInput('items', []);
    fixture.componentRef.setInput('selectedId', null);

    expect(text('reviews-empty')).toBe('Черга порожня');
    expect(element.querySelector('[data-testid="reviews-empty"] app-icon')).not.toBeNull();
  });
});
