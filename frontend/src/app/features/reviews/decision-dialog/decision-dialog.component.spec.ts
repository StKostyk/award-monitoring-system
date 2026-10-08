import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { ReviewTemplate, ReviewsService } from '../reviews.service';
import {
  COMMENT_MAX_LENGTH,
  DecisionDialogComponent,
  DecisionDialogData,
} from './decision-dialog.component';

const translations = {
  uk: {
    reviews: {
      decide: {
        title: {
          APPROVE: 'Затвердити нагороду',
          RETURN: 'Повернути на доопрацювання',
          REJECT: 'Відхилити нагороду',
          ESCALATE: 'Передати {{level}}',
        },
        comment: 'Коментар',
        commentRequired: 'Коментар для автора (обов’язковий)',
        required: 'Вкажіть, що потрібно виправити',
        tooLong: 'Не більше 2000 символів',
        verified: 'Документи перевірено',
        confirm: { APPROVE: 'Затвердити', RETURN: 'Повернути', REJECT: 'Відхилити' },
        to: { DEAN: 'декану', higher: 'на вищий рівень' },
        template: 'Шаблон відповіді',
        replace: 'Замінити змінений текст коментаря?',
        close: 'Закрити',
        errors: {
          claimed: 'Нагороду вже взяв у роботу {{name}}',
          stale: 'Дані змінилися — сторінку оновлено, перевірте та підтвердіть ще раз',
          closed: 'Рішення вже ухвалено',
          gone: 'Розгляд цієї нагороди вам більше не доступний',
          network: 'Не вдалося надіслати, спробуйте ще раз',
        },
      },
      problems: { 'validation-failed': 'Перевірте коментар', unknown: 'Не вдалося виконати дію' },
      batch: { count: 'Нагород: {{count}}' },
    },
  },
};

const RETURN_TEMPLATES: ReviewTemplate[] = [
  { id: 1, decision: 'RETURN', title: 'Немає скану', body: 'Додайте скан сертифіката.' },
  { id: 2, decision: 'RETURN', title: 'Невірна категорія', body: 'Оберіть правильну категорію.' },
];

const CLAIMER = { id: 7, name: 'Олена Коваль' };
const SELF_ID = 31;

function problem(status: number, type?: string, extra: object = {}): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: type ? { type: `urn:awards:problem:${type}`, ...extra } : null,
  });
}

describe('DecisionDialogComponent', () => {
  let fixture: ComponentFixture<DecisionDialogComponent>;
  const ref = { close: vi.fn(), disableClose: false };
  const reviews = { templates: vi.fn() };
  const submit = vi.fn();

  beforeEach(() => sessionStorage.clear());

  async function create(
    settings: Omit<DecisionDialogData, 'draft' | 'submit'>,
    templates: ReviewTemplate[] = [],
  ): Promise<HTMLElement> {
    const data: DecisionDialogData = { ...settings, draft: '42', submit };
    ref.close.mockReset();
    ref.disableClose = false;
    submit.mockReset().mockReturnValue(of('done'));
    reviews.templates.mockReset().mockReturnValue(of(templates));
    await TestBed.configureTestingModule({
      imports: [
        DecisionDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: ref },
        { provide: ReviewsService, useValue: reviews },
        { provide: AuthService, useValue: { userId: () => String(SELF_ID) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DecisionDialogComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function find(element: HTMLElement, id: string): HTMLElement | null {
    return element.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  }

  function type(element: HTMLElement, value: string): void {
    const field = find(element, 'decision-comment') as HTMLTextAreaElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('ac2_3_ac2_4_a_return_needs_a_non_blank_comment', async () => {
    const element = await create({ decision: 'RETURN', target: 'DEAN', documents: 0 });

    type(element, '   ');
    find(element, 'decision-confirm')?.click();
    fixture.detectChanges();

    expect(submit).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Вкажіть, що потрібно виправити');

    type(element, '  Додайте номер наказу ');
    find(element, 'decision-confirm')?.click();

    expect(submit).toHaveBeenCalledWith({ comment: 'Додайте номер наказу' });
  });

  it('ac2_3_a_comment_is_limited_to_the_server_maximum', async () => {
    const element = await create({ decision: 'REJECT', target: 'DEAN', documents: 0 });

    type(element, 'x'.repeat(COMMENT_MAX_LENGTH + 1));
    find(element, 'decision-confirm')?.click();

    expect(submit).not.toHaveBeenCalled();
  });

  it('ac2_6_an_approval_offers_the_documents_mark_only_when_documents_exist', async () => {
    let element = await create({ decision: 'APPROVE', target: 'DEAN', documents: 0 });
    expect(find(element, 'decision-verified')).toBeNull();

    TestBed.resetTestingModule();
    element = await create({ decision: 'APPROVE', target: 'DEAN', documents: 1 });
    find(element, 'decision-verified')?.querySelector('input')?.click();
    find(element, 'decision-confirm')?.click();

    expect(submit).toHaveBeenCalledWith({ verified: true });
  });

  it('ac2_5_an_escalation_names_the_next_level_and_needs_no_comment', async () => {
    const element = await create({ decision: 'ESCALATE', target: 'DEAN', documents: 0 });

    expect(find(element, 'decision-title')?.textContent?.trim()).toBe('Передати декану');
    find(element, 'decision-confirm')?.click();

    expect(submit).toHaveBeenCalledWith({});
  });

  it('ac4_5_ac4_6_a_template_fills_the_comment_which_stays_editable', async () => {
    const element = await create(
      { decision: 'RETURN', target: null, documents: 0 },
      RETURN_TEMPLATES,
    );

    expect(reviews.templates).toHaveBeenCalledWith('RETURN');
    expect(find(element, 'decision-template')).not.toBeNull();
    fixture.componentInstance.pick(1);
    fixture.detectChanges();
    const field = find(element, 'decision-comment') as HTMLTextAreaElement;
    expect(field.value).toBe('Додайте скан сертифіката.');

    type(element, 'Додайте скан сертифіката у PDF.');
    find(element, 'decision-confirm')?.click();

    expect(submit).toHaveBeenCalledWith({ comment: 'Додайте скан сертифіката у PDF.' });
  });

  it('ac4_6_another_template_replaces_unedited_text_at_once', async () => {
    const element = await create(
      { decision: 'RETURN', target: null, documents: 0 },
      RETURN_TEMPLATES,
    );

    fixture.componentInstance.pick(1);
    fixture.componentInstance.pick(2);
    fixture.detectChanges();

    expect(find(element, 'decision-replace')).toBeNull();
    expect((find(element, 'decision-comment') as HTMLTextAreaElement).value).toBe(
      'Оберіть правильну категорію.',
    );
  });

  it('ac4_6_the_dialog_asks_before_replacing_edited_text', async () => {
    const element = await create(
      { decision: 'RETURN', target: null, documents: 0 },
      RETURN_TEMPLATES,
    );
    fixture.componentInstance.pick(1);
    type(element, 'Мій власний коментар');

    fixture.componentInstance.pick(2);
    fixture.detectChanges();
    expect(find(element, 'decision-replace')?.textContent).toContain('Замінити змінений текст');
    find(element, 'decision-replace-no')?.click();
    fixture.detectChanges();

    expect(find(element, 'decision-replace')).toBeNull();
    expect((find(element, 'decision-comment') as HTMLTextAreaElement).value).toBe(
      'Мій власний коментар',
    );
    expect(fixture.componentInstance.template.value).toBe(1);

    fixture.componentInstance.pick(2);
    fixture.detectChanges();
    find(element, 'decision-replace-yes')?.click();
    fixture.detectChanges();

    expect((find(element, 'decision-comment') as HTMLTextAreaElement).value).toBe(
      'Оберіть правильну категорію.',
    );
  });

  it('ac4_6_a_batch_shows_its_count_and_no_documents_mark', async () => {
    const element = await create({ decision: 'APPROVE', target: null, documents: 3, count: 4 });

    expect(find(element, 'decision-count')?.textContent?.trim()).toBe('Нагород: 4');
    expect(find(element, 'decision-verified')).toBeNull();
    expect(find(element, 'decision-template')).toBeNull();
  });

  it('ac4_6_a_mixed_level_escalation_goes_to_the_next_level', async () => {
    const element = await create({ decision: 'ESCALATE', target: null, documents: 0, count: 2 });

    expect(find(element, 'decision-title')?.textContent?.trim()).toBe('Передати на вищий рівень');
  });

  it('ac4_1_the_dialog_stays_open_with_disabled_buttons_until_the_answer', async () => {
    const element = await create({ decision: 'RETURN', target: 'DEAN', documents: 0 });
    const answer = new Subject<string>();
    submit.mockReturnValue(answer);
    type(element, 'Додайте наказ');

    find(element, 'decision-confirm')?.click();
    fixture.detectChanges();

    expect(submit).toHaveBeenCalledWith({ comment: 'Додайте наказ' });
    expect(ref.close).not.toHaveBeenCalled();
    expect(ref.disableClose).toBe(true);
    expect(find(element, 'decision-progress')).not.toBeNull();
    expect((find(element, 'decision-confirm') as HTMLButtonElement).disabled).toBe(true);
    expect((find(element, 'decision-cancel') as HTMLButtonElement).disabled).toBe(true);

    answer.next('done');
    answer.complete();

    expect(ref.close).toHaveBeenCalledWith('done');
  });

  it.each([
    [
      'claimed',
      problem(HttpStatusCode.Conflict, 'request-claimed', { reviewer: CLAIMER }),
      'Нагороду вже взяв у роботу Олена Коваль',
      false,
    ],
    [
      'stale',
      problem(HttpStatusCode.Conflict, 'request-stale'),
      'Дані змінилися — сторінку оновлено',
      false,
    ],
    ['closed', problem(HttpStatusCode.Conflict, 'request-closed'), 'Рішення вже ухвалено', true],
    [
      'gone',
      problem(HttpStatusCode.NotFound, 'not-found'),
      'Розгляд цієї нагороди вам більше не доступний',
      true,
    ],
    ['network', problem(0), 'Не вдалося надіслати, спробуйте ще раз', false],
    [
      'server',
      problem(HttpStatusCode.InternalServerError, 'internal'),
      'Не вдалося надіслати',
      false,
    ],
    [
      'validation',
      problem(HttpStatusCode.UnprocessableEntity, 'validation-failed'),
      'Перевірте коментар',
      false,
    ],
  ])('ac4_2_a_%s_failure_keeps_the_comment_and_explains', async (_, error, text, final) => {
    const element = await create({ decision: 'REJECT', target: 'DEAN', documents: 0 });
    submit.mockReturnValue(throwError(() => error));
    type(element, 'Не відповідає положенню');

    find(element, 'decision-confirm')?.click();
    fixture.detectChanges();

    expect(ref.close).not.toHaveBeenCalled();
    expect(ref.disableClose).toBe(false);
    expect(find(element, 'decision-error')?.textContent).toContain(text);
    expect((find(element, 'decision-comment') as HTMLTextAreaElement).value).toBe(
      'Не відповідає положенню',
    );
    expect(find(element, 'decision-confirm') === null).toBe(final);
    expect(find(element, 'decision-close') !== null).toBe(final);
  });

  it('ac4_2_after_a_stale_answer_the_next_confirm_sends_again', async () => {
    const element = await create({ decision: 'APPROVE', target: 'DEAN', documents: 0 });
    submit.mockReturnValueOnce(throwError(() => problem(HttpStatusCode.Conflict, 'request-stale')));

    find(element, 'decision-confirm')?.click();
    fixture.detectChanges();
    find(element, 'decision-confirm')?.click();
    fixture.detectChanges();

    expect(submit).toHaveBeenCalledTimes(2);
    expect(ref.close).toHaveBeenCalledWith('done');
  });

  it('ac4_2_close_only_leaves_without_a_decision', async () => {
    const element = await create({ decision: 'APPROVE', target: 'DEAN', documents: 0 });
    submit.mockReturnValue(throwError(() => problem(HttpStatusCode.Conflict, 'request-closed')));
    find(element, 'decision-confirm')?.click();
    fixture.detectChanges();

    find(element, 'decision-close')?.click();

    expect(ref.close).toHaveBeenCalledWith();
  });

  it('ac4_2_a_claim_naming_the_caller_says_the_data_changed', async () => {
    const element = await create({ decision: 'APPROVE', target: 'DEAN', documents: 0 });
    submit.mockReturnValue(
      throwError(() =>
        problem(HttpStatusCode.Conflict, 'request-claimed', {
          reviewer: { id: SELF_ID, name: 'Ірина Секретар' },
        }),
      ),
    );

    find(element, 'decision-confirm')?.click();
    fixture.detectChanges();

    expect(find(element, 'decision-error')?.textContent).toContain('Дані змінилися');
  });

  it('ac4_3_the_typed_comment_is_kept_for_the_user_award_and_decision', async () => {
    const element = await create({ decision: 'RETURN', target: 'DEAN', documents: 0 });

    type(element, 'Додайте скан');

    expect(sessionStorage.getItem(`awards.decision-draft:${SELF_ID}:42:RETURN`)).toBe(
      'Додайте скан',
    );
  });

  it('ac4_3_a_kept_comment_is_offered_only_for_the_same_user_and_decision', async () => {
    sessionStorage.setItem(`awards.decision-draft:${SELF_ID}:42:RETURN`, 'Додайте скан');
    sessionStorage.setItem('awards.decision-draft:99:42:REJECT', 'Чужий коментар');

    let element = await create({ decision: 'RETURN', target: 'DEAN', documents: 0 });
    expect((find(element, 'decision-comment') as HTMLTextAreaElement).value).toBe('Додайте скан');

    TestBed.resetTestingModule();
    element = await create({ decision: 'REJECT', target: 'DEAN', documents: 0 });
    expect((find(element, 'decision-comment') as HTMLTextAreaElement).value).toBe('');
  });

  it('ac4_3_the_kept_comment_goes_with_the_dialog', async () => {
    const element = await create({ decision: 'RETURN', target: 'DEAN', documents: 0 });
    type(element, 'Додайте скан');

    fixture.destroy();

    expect(sessionStorage.getItem(`awards.decision-draft:${SELF_ID}:42:RETURN`)).toBeNull();
  });
});
