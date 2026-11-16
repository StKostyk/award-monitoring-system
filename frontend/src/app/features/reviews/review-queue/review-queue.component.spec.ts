import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { DocumentsService } from '../../awards/award-documents/documents.service';
import { AwardsService } from '../../awards/awards.service';
import { DecisionDialogData, DecisionInput } from '../decision-dialog/decision-dialog.component';
import { HeldDecisionService } from '../held-decision.service';
import { ReviewItem } from '../reviews.service';
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
      },
    },
  },
};

describe('ReviewQueueComponent', () => {
  let fixture: ComponentFixture<ReviewQueueComponent>;
  let element: HTMLElement;
  let confirmed: DecisionInput | undefined;
  const held = { hold: vi.fn() };
  const awards = { get: vi.fn() };
  const documents = { list: vi.fn(() => of([])) };
  const dialog = { open: vi.fn() };
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
    awards.get.mockReset();
    awards.get.mockReturnValue(
      of({ awardingOrganization: 'МОН України', awardDate: '2025-05-01' }),
    );
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
        { provide: DocumentsService, useValue: documents },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { userId: signal('21') } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReviewQueueComponent);
    fixture.componentRef.setInput('items', items);
    fixture.componentRef.setInput('left', 3);
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

  it('ac6_1_shows_the_number_left_and_the_selected_request_with_document_fields_and_checklist', () => {
    expect(text('queue-left')).toBe('Залишилось: 3');
    expect(text('queue-open')).toBe('Грамота МОН');
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
    expect(text('queue-open')).toBe('Подяка ректора');
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
    fixture.componentRef.setInput('left', 0);

    expect(text('reviews-empty')).toBe('Черга порожня');
    expect(element.querySelector('[data-testid="reviews-empty"] app-icon')).not.toBeNull();
  });
});
