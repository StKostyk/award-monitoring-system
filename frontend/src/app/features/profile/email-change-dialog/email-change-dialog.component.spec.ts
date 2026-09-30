import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { EmailChangeDialogComponent } from './email-change-dialog.component';

describe('EmailChangeDialogComponent', () => {
  const dialogRef = { close: vi.fn() };
  let http: HttpTestingController;

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [
        EmailChangeDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({ langs: { uk: {} }, translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' } }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { email: 'mover@chnu.edu.ua' } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(EmailChangeDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  afterEach(() => {
    http.verify();
    vi.clearAllMocks();
  });

  it('ac14 sends the new address with the password and closes with the address', async () => {
    const dialog = await setup();
    dialog.form.setValue({ newEmail: 'Mover.New@chnu.edu.ua', currentPassword: 'Passw0rd-demo' });

    dialog.submit();
    const request = http.expectOne(`${environment.apiUrl}/users/me/email-change`);
    expect(request.request.body).toEqual({ newEmail: 'mover.new@chnu.edu.ua', currentPassword: 'Passw0rd-demo' });
    request.flush(null, { status: 202, statusText: 'Accepted' });

    expect(dialogRef.close).toHaveBeenCalledWith('mover.new@chnu.edu.ua');
  });

  it.each([
    [403, 'password-mismatch', 'emailChange.errors.password-mismatch'],
    [423, 'account-locked', 'emailChange.errors.account-locked'],
    [409, 'email-taken', 'emailChange.errors.email-taken'],
    [422, 'institutional-email-required', 'emailChange.errors.institutional-email-required'],
    [422, 'validation-failed', 'emailChange.errors.unchanged'],
    [429, 'too-many-requests', 'emailChange.errors.too-many-requests'],
    [500, 'something-else', 'emailChange.errors.failed'],
  ])('ac14 answers %s %s with its message and clears the password', async (status, type, message) => {
    const dialog = await setup();
    dialog.form.setValue({ newEmail: 'mover.new@chnu.edu.ua', currentPassword: 'wrong' });

    dialog.submit();
    http
      .expectOne(`${environment.apiUrl}/users/me/email-change`)
      .flush({ type: `urn:awards:problem:${type}` }, { status, statusText: 'Refused' });

    expect(dialog.error()).toBe(message);
    expect(dialog.form.controls.currentPassword.value).toBe('');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('ac14 refuses the current address and an incomplete form without a request', async () => {
    const dialog = await setup();
    dialog.submit();
    dialog.form.setValue({ newEmail: 'MOVER@chnu.edu.ua', currentPassword: 'x' });
    dialog.submit();

    expect(dialog.error()).toBe('emailChange.errors.unchanged');
  });
});
