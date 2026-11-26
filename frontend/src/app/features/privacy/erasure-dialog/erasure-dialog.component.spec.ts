import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ErasureRequest, PrivacyCentreService } from '../privacy-centre.service';
import { ErasureDialogComponent } from './erasure-dialog.component';

const translations = {
  uk: {
    privacy: {
      erasure: {
        dialog: {
          title: 'Видалити мій обліковий запис',
          text: 'Підтвердіть паролем',
          password: 'Поточний пароль',
          passwordRequired: 'Введіть пароль',
          understood: 'Я розумію',
          understoodRequired: 'Позначте поле',
          confirm: 'Надіслати запит',
          cancel: 'Назад',
        },
        problems: {
          'password-mismatch': 'Неправильний пароль',
          'account-locked': 'Обліковий запис заблоковано',
          'erasure-pending': 'Видалення вже заплановано',
          'legal-hold': 'Через юридичне зобов’язання',
          'last-administrator': 'Ви єдиний системний адміністратор',
          network: 'Сервер недоступний',
          unknown: 'Не вдалося',
        },
      },
    },
  },
};

const REQUEST: ErasureRequest = {
  id: 7,
  requestedAt: '2026-10-16T10:00:00Z',
  scheduledFor: '2026-11-15T10:00:00Z',
  postponed: false,
};

function problem(status: number, type?: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: type ? { type: `urn:awards:problem:${type}` } : null,
  });
}

describe('ErasureDialogComponent', () => {
  let fixture: ComponentFixture<ErasureDialogComponent>;
  const ref = { close: vi.fn(), disableClose: false };
  const centre = { requestErasure: vi.fn() };

  beforeEach(async () => {
    ref.close.mockReset();
    ref.disableClose = false;
    centre.requestErasure.mockReset().mockReturnValue(of(REQUEST));
    await TestBed.configureTestingModule({
      imports: [
        ErasureDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MatDialogRef, useValue: ref },
        { provide: PrivacyCentreService, useValue: centre },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ErasureDialogComponent);
    fixture.detectChanges();
  });

  function query(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function fill(password: string, understood: boolean): void {
    fixture.componentInstance.password.setValue(password);
    fixture.componentInstance.understood.setValue(understood);
  }

  function confirm(): void {
    query('erasure-dialog-confirm')?.click();
    fixture.detectChanges();
  }

  it('ac4_7_sends_the_password_and_closes_with_the_pending_request', () => {
    fill('Secret#2026', true);

    confirm();

    expect(centre.requestErasure).toHaveBeenCalledWith('Secret#2026');
    expect(ref.close).toHaveBeenCalledWith(REQUEST);
  });

  it('ac4_7_requires_the_password_and_the_ticked_box', () => {
    fill('', false);

    confirm();

    expect(centre.requestErasure).not.toHaveBeenCalled();
    expect(query('erasure-understood-error')?.textContent).toContain('Позначте поле');
    expect(fixture.nativeElement.textContent).toContain('Введіть пароль');

    fill('Secret#2026', false);
    confirm();

    expect(centre.requestErasure).not.toHaveBeenCalled();
  });

  it.each([
    [HttpStatusCode.Forbidden, 'password-mismatch', 'Неправильний пароль'],
    [HttpStatusCode.Locked, 'account-locked', 'Обліковий запис заблоковано'],
    [HttpStatusCode.Conflict, 'erasure-pending', 'Видалення вже заплановано'],
    [HttpStatusCode.Conflict, 'legal-hold', 'Через юридичне зобов’язання'],
    [HttpStatusCode.Conflict, 'last-administrator', 'Ви єдиний системний адміністратор'],
    [0, undefined, 'Сервер недоступний'],
    [HttpStatusCode.InternalServerError, undefined, 'Не вдалося'],
  ])('ac4_3_shows_its_own_message_for_status_%s_%s', (status, type, message) => {
    centre.requestErasure.mockReturnValue(throwError(() => problem(status, type)));
    fill('Secret#2026', true);

    confirm();

    expect(query('erasure-dialog-error')?.textContent).toContain(message);
    expect(ref.close).not.toHaveBeenCalled();
    expect(ref.disableClose).toBe(false);
    expect(fixture.componentInstance.password.value).toBe('');
    expect(fixture.componentInstance.sending()).toBe(false);
  });

  it('closes without a request on Back', () => {
    query('erasure-dialog-cancel')?.click();

    expect(ref.close).toHaveBeenCalledWith();
    expect(centre.requestErasure).not.toHaveBeenCalled();
  });
});
