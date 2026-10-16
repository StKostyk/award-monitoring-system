import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LegalHold } from '../legal-holds.service';
import { LegalHoldDialogComponent, LegalHoldDialogData } from './legal-hold-dialog.component';

const translations = {
  uk: {
    admin: {
      detail: {
        legalHold: {
          dialog: {
            title: { place: 'Встановити юридичне утримання', lift: 'Зняти юридичне утримання' },
            text: { place: 'Обліковий запис {{name}}', lift: 'Зняти для {{name}}' },
            reason: 'Причина',
            hint: '{{min}}–{{max}} символів',
            required: 'Вкажіть причину',
            tooShort: 'Щонайменше {{min}} символів',
            tooLong: 'Щонайбільше {{max}} символів',
            confirm: { place: 'Встановити', lift: 'Зняти' },
            cancel: 'Скасувати',
            close: 'Закрити',
          },
          problems: {
            'legal-hold-active': 'Уже діє утримання',
            'legal-hold-not-found': 'Утримання немає',
            network: 'Сервер недоступний',
            unknown: 'Не вдалося',
          },
        },
      },
    },
  },
};

const HOLD: LegalHold = {
  id: 5,
  reason: 'Inspection 2026/14 by the ministry',
  placedAt: '2026-10-16T10:00:00Z',
  placedBy: { id: 2, name: 'Галина Захист', email: 'gdpr@chnu.edu.ua' },
  liftedAt: null,
  liftedBy: null,
  liftReason: null,
};

function problem(status: number, type?: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: type ? { type: `urn:awards:problem:${type}` } : null,
  });
}

describe('LegalHoldDialogComponent', () => {
  let fixture: ComponentFixture<LegalHoldDialogComponent>;
  const ref = { close: vi.fn(), disableClose: false };
  const submit = vi.fn();

  async function create(mode: LegalHoldDialogData['mode']): Promise<HTMLElement> {
    ref.close.mockReset();
    ref.disableClose = false;
    submit.mockReset().mockReturnValue(of(HOLD));
    await TestBed.configureTestingModule({
      imports: [
        LegalHoldDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { mode, name: 'Анастасія Працівник', submit } },
        { provide: MatDialogRef, useValue: ref },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LegalHoldDialogComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function find(element: HTMLElement, id: string): HTMLElement | null {
    return element.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  }

  function type(element: HTMLElement, value: string): void {
    const field = find(element, 'hold-reason') as HTMLTextAreaElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function confirm(element: HTMLElement): void {
    find(element, 'hold-dialog-confirm')?.click();
    fixture.detectChanges();
  }

  it('ac3_6_requires_a_reason_of_at_least_ten_characters_after_trimming', async () => {
    const element = await create('place');

    expect(find(element, 'hold-dialog-title')?.textContent).toContain(
      'Встановити юридичне утримання',
    );

    type(element, '    ');
    confirm(element);

    expect(element.textContent).toContain('Вкажіть причину');

    type(element, '  short    ');
    confirm(element);

    expect(element.textContent).toContain('Щонайменше 10 символів');
    expect(submit).not.toHaveBeenCalled();

    type(element, '  Inspection 2026/14 by the ministry ');
    confirm(element);

    expect(submit).toHaveBeenCalledWith('Inspection 2026/14 by the ministry');
    expect(ref.close).toHaveBeenCalledWith(HOLD);
  });

  it('ac3_6_caps_the_reason_at_five_hundred_characters', async () => {
    const element = await create('lift');

    type(element, 'x'.repeat(501));
    confirm(element);

    expect(element.textContent).toContain('Щонайбільше 500 символів');
    expect(submit).not.toHaveBeenCalled();
  });

  it('ac3_2_an_active_hold_conflict_leaves_only_closing_and_asks_for_a_reload', async () => {
    const element = await create('place');
    submit.mockReturnValue(throwError(() => problem(HttpStatusCode.Conflict, 'legal-hold-active')));

    type(element, 'Inspection 2026/14 by the ministry');
    confirm(element);

    expect(find(element, 'hold-dialog-error')?.textContent).toContain('Уже діє утримання');
    expect(find(element, 'hold-dialog-confirm')).toBeNull();
    expect(ref.close).not.toHaveBeenCalled();

    find(element, 'hold-dialog-close')?.click();

    expect(ref.close).toHaveBeenCalledWith('stale');
  });

  it('ac3_3_a_missing_hold_also_asks_for_a_reload', async () => {
    const element = await create('lift');
    submit.mockReturnValue(
      throwError(() => problem(HttpStatusCode.NotFound, 'legal-hold-not-found')),
    );

    type(element, 'Inspection closed without findings');
    confirm(element);
    find(element, 'hold-dialog-close')?.click();

    expect(ref.close).toHaveBeenCalledWith('stale');
  });

  it('ac3_6_a_network_failure_keeps_the_dialog_open_for_a_retry', async () => {
    const element = await create('place');
    submit.mockReturnValue(throwError(() => problem(0)));

    type(element, 'Inspection 2026/14 by the ministry');
    confirm(element);

    expect(find(element, 'hold-dialog-error')?.textContent).toContain('Сервер недоступний');
    expect(ref.disableClose).toBe(false);

    find(element, 'hold-dialog-cancel')?.click();

    expect(ref.close).toHaveBeenCalledWith(undefined);
  });

  it('ac3_6_shows_an_unexpected_problem_as_unknown', async () => {
    const element = await create('place');
    submit.mockReturnValue(throwError(() => problem(HttpStatusCode.BadRequest, 'bad-request')));

    type(element, 'Inspection 2026/14 by the ministry');
    confirm(element);

    expect(find(element, 'hold-dialog-error')?.textContent).toContain('Не вдалося');
    expect(find(element, 'hold-dialog-confirm')).not.toBeNull();
  });
});
