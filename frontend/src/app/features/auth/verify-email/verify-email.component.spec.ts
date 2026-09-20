import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent', () => {
  const auth = { login: vi.fn() };
  let http: HttpTestingController;

  async function setup(token: string | null) {
    await TestBed.configureTestingModule({
      imports: [
        VerifyEmailComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              verify: {
                success: 'Підтверджено {{email}}',
                invalid: 'Недійсне',
                errors: { 'password-mismatch': 'Пароль не збігається' },
              },
            },
          },
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
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('ac25 sends the token with the registration password and offers to sign in', async () => {
    const fixture = await setup('raw');
    expect(fixture.componentInstance.state()).toBe('form');

    fixture.componentInstance.password.setValue('correct-horse-battery');
    fixture.componentInstance.submit();
    const request = http.expectOne(`${environment.apiUrl}/auth/verify-email`);
    expect(request.request.body).toEqual({ token: 'raw', password: 'correct-horse-battery' });
    request.flush({ email: 'x@chnu.edu.ua', status: 'ACTIVE' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('verified');
    expect(fixture.nativeElement.querySelector('[data-testid="verify-success"]').textContent).toContain('x@chnu.edu.ua');
    (fixture.nativeElement.querySelector('[data-testid="verify-sign-in"]') as HTMLButtonElement).click();
    expect(auth.login).toHaveBeenCalledWith('/');
  });

  it('ac25 keeps the form open after a wrong password', async () => {
    const fixture = await setup('raw');

    fixture.componentInstance.password.setValue('wrong-password-1');
    fixture.componentInstance.submit();
    http
      .expectOne(`${environment.apiUrl}/auth/verify-email`)
      .flush({ type: 'urn:awards:problem:password-mismatch' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('form');
    expect(fixture.nativeElement.querySelector('[data-testid="verify-error"]').textContent).toContain('не збігається');
  });

  it('ac26 explains an expired or used link and offers to resend', async () => {
    const fixture = await setup('old');

    fixture.componentInstance.password.setValue('correct-horse-battery');
    fixture.componentInstance.submit();
    http
      .expectOne(`${environment.apiUrl}/auth/verify-email`)
      .flush({ type: 'urn:awards:problem:token-invalid', status: 410 }, { status: 410, statusText: 'Gone' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('invalid');
    expect(fixture.nativeElement.querySelector('[data-testid="verify-go-pending"]')).not.toBeNull();
  });

  it('treats a missing token as invalid and an empty password as not submittable', async () => {
    const fixture = await setup(null);
    expect(fixture.componentInstance.state()).toBe('invalid');

    fixture.componentInstance.submit();
    http.expectNone(`${environment.apiUrl}/auth/verify-email`);
  });
});
