import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { vi } from 'vitest';

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
      },
      batch: { count: 'Нагород: {{count}}' },
    },
  },
};

const RETURN_TEMPLATES: ReviewTemplate[] = [
  { id: 1, decision: 'RETURN', title: 'Немає скану', body: 'Додайте скан сертифіката.' },
  { id: 2, decision: 'RETURN', title: 'Невірна категорія', body: 'Оберіть правильну категорію.' },
];

describe('DecisionDialogComponent', () => {
  let fixture: ComponentFixture<DecisionDialogComponent>;
  const ref = { close: vi.fn() };
  const reviews = { templates: vi.fn() };

  async function create(
    data: DecisionDialogData,
    templates: ReviewTemplate[] = [],
  ): Promise<HTMLElement> {
    ref.close.mockReset();
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

    expect(ref.close).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Вкажіть, що потрібно виправити');

    type(element, '  Додайте номер наказу ');
    find(element, 'decision-confirm')?.click();

    expect(ref.close).toHaveBeenCalledWith({ comment: 'Додайте номер наказу' });
  });

  it('ac2_3_a_comment_is_limited_to_the_server_maximum', async () => {
    const element = await create({ decision: 'REJECT', target: 'DEAN', documents: 0 });

    type(element, 'x'.repeat(COMMENT_MAX_LENGTH + 1));
    find(element, 'decision-confirm')?.click();

    expect(ref.close).not.toHaveBeenCalled();
  });

  it('ac2_6_an_approval_offers_the_documents_mark_only_when_documents_exist', async () => {
    let element = await create({ decision: 'APPROVE', target: 'DEAN', documents: 0 });
    expect(find(element, 'decision-verified')).toBeNull();

    TestBed.resetTestingModule();
    element = await create({ decision: 'APPROVE', target: 'DEAN', documents: 1 });
    find(element, 'decision-verified')?.querySelector('input')?.click();
    find(element, 'decision-confirm')?.click();

    expect(ref.close).toHaveBeenCalledWith({ verified: true });
  });

  it('ac2_5_an_escalation_names_the_next_level_and_needs_no_comment', async () => {
    const element = await create({ decision: 'ESCALATE', target: 'DEAN', documents: 0 });

    expect(find(element, 'decision-title')?.textContent?.trim()).toBe('Передати декану');
    find(element, 'decision-confirm')?.click();

    expect(ref.close).toHaveBeenCalledWith({});
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

    expect(ref.close).toHaveBeenCalledWith({ comment: 'Додайте скан сертифіката у PDF.' });
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
});
