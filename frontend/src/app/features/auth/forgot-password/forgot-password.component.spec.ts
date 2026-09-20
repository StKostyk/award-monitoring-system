import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { ForgotPasswordComponent } from './forgot-password.component';

describe('ForgotPasswordComponent', () => {
  const auth = { login: vi.fn() };
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ForgotPasswordComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: { forgot: { sent: 'Надіслано на {{email}}', errors: { network: 'Немає звʼязку' } } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('ac31 ac34 requests a reset and shows a neutral confirmation', () => {
    fixture.componentInstance.email.setValue('Olena@chnu.edu.ua');
    fixture.componentInstance.submit();

    const request = http.expectOne(`${environment.apiUrl}/auth/password-reset/request`);
    expect(request.request.body).toEqual({ email: 'Olena@chnu.edu.ua' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="forgot-sent"]').textContent).toContain('Olena@chnu.edu.ua');
    (fixture.nativeElement.querySelector('[data-testid="forgot-sign-in"]') as HTMLButtonElement).click();
    expect(auth.login).toHaveBeenCalledWith('/');
  });

  it('ac34 submits the form from the page without reloading it', () => {
    fixture.componentInstance.email.setValue('olena@chnu.edu.ua');
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    const event = new Event('submit', { cancelable: true });
    form.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
    http.expectOne(`${environment.apiUrl}/auth/password-reset/request`).flush(null, { status: 202, statusText: 'Accepted' });
    expect(fixture.componentInstance.sent()).toBe(true);
  });

  it('does not call the server with an invalid address and shows network errors', () => {
    fixture.componentInstance.email.setValue('not-an-address');
    fixture.componentInstance.submit();
    http.expectNone(`${environment.apiUrl}/auth/password-reset/request`);

    fixture.componentInstance.email.setValue('olena@chnu.edu.ua');
    fixture.componentInstance.submit();
    http.expectOne(`${environment.apiUrl}/auth/password-reset/request`).error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    expect(fixture.componentInstance.sent()).toBe(false);
    expect(fixture.nativeElement.querySelector('[data-testid="forgot-error"]').textContent).toContain('звʼязку');
  });
});
