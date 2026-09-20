import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
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
        TranslocoTestingModule.forRoot({
          langs: { uk: { verify: { success: 'Підтверджено {{email}}', invalid: 'Недійсне' } } },
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

  it('ac25 verifies the token from the link and offers to sign in', async () => {
    const fixture = await setup('raw');

    http.expectOne(`${environment.apiUrl}/auth/verify-email`).flush({ email: 'x@chnu.edu.ua', status: 'ACTIVE' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('verified');
    expect(fixture.nativeElement.querySelector('[data-testid="verify-success"]').textContent).toContain('x@chnu.edu.ua');
    (fixture.nativeElement.querySelector('[data-testid="verify-sign-in"]') as HTMLButtonElement).click();
    expect(auth.login).toHaveBeenCalledWith('/');
  });

  it('ac26 explains an expired or used link and offers to resend', async () => {
    const fixture = await setup('old');

    http
      .expectOne(`${environment.apiUrl}/auth/verify-email`)
      .flush({ type: 'urn:awards:problem:token-invalid', status: 410 }, { status: 410, statusText: 'Gone' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('invalid');
    expect(fixture.nativeElement.querySelector('[data-testid="verify-go-pending"]')).not.toBeNull();
  });

  it('treats a missing token as invalid without calling the server', async () => {
    const fixture = await setup(null);

    expect(fixture.componentInstance.state()).toBe('invalid');
    http.expectNone(`${environment.apiUrl}/auth/verify-email`);
  });
});
