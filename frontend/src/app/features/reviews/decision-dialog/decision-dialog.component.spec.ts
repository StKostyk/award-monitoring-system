import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

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
        to: { DEAN: 'декану' },
      },
    },
  },
};

describe('DecisionDialogComponent', () => {
  let fixture: ComponentFixture<DecisionDialogComponent>;
  const ref = { close: vi.fn() };

  async function create(data: DecisionDialogData): Promise<HTMLElement> {
    ref.close.mockReset();
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
});
