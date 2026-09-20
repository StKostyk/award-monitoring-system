import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { ResetPasswordComponent } from './reset-password.component';

describe('ResetPasswordComponent', () => {
  const auth = { login: vi.fn() };
  let http: HttpTestingController;

  async function setup(token: string | null) {
    await TestBed.configureTestingModule({
      imports: [
        ResetPasswordComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: { reset: { done: 'Пароль змінено', errors: { 'password-too-common': 'Надто простий' } } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('ac32 ac34 sends the token with the new password and offers to sign in', async () => {
    const fixture = await setup('raw');

    fixture.componentInstance.password.setValue('new-horse-battery');
    fixture.componentInstance.submit();
    const request = http.expectOne(`${environment.apiUrl}/auth/password-reset/confirm`);
    expect(request.request.body).toEqual({ token: 'raw', password: 'new-horse-battery' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('done');
    (fixture.nativeElement.querySelector('[data-testid="reset-sign-in"]') as HTMLButtonElement).click();
    expect(auth.login).toHaveBeenCalledWith('/');
  });

  it('ac32 shows the policy reason and keeps the form for a refused password', async () => {
    const fixture = await setup('raw');

    fixture.componentInstance.password.setValue('password123');
    fixture.componentInstance.submit();
    http
      .expectOne(`${environment.apiUrl}/auth/password-reset/confirm`)
      .flush({ type: 'urn:awards:problem:password-too-common' }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('form');
    expect(fixture.nativeElement.querySelector('[data-testid="reset-error"]').textContent).toContain('простий');
  });

  it('ac32 treats an expired link as invalid and links to a new request', async () => {
    const fixture = await setup('old');

    fixture.componentInstance.password.setValue('new-horse-battery');
    fixture.componentInstance.submit();
    http
      .expectOne(`${environment.apiUrl}/auth/password-reset/confirm`)
      .flush({ type: 'urn:awards:problem:token-invalid' }, { status: 410, statusText: 'Gone' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('invalid');
    expect(fixture.nativeElement.querySelector('[data-testid="reset-go-forgot"]')).not.toBeNull();
  });

  it('rejects short passwords on the client and a missing token without a request', async () => {
    const fixture = await setup(null);
    expect(fixture.componentInstance.state()).toBe('invalid');

    fixture.componentInstance.password.setValue('short');
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.password.hasError('minlength')).toBe(true);
    http.expectNone(`${environment.apiUrl}/auth/password-reset/confirm`);
  });
});
