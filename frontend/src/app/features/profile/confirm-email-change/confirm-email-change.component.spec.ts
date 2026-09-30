import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmEmailChangeComponent } from './confirm-email-change.component';

describe('ConfirmEmailChangeComponent', () => {
  const auth = { login: vi.fn(), signedOutElsewhere: vi.fn(), userId: vi.fn(() => '12' as string | null) };
  let http: HttpTestingController;

  async function setup(token: string | null) {
    await TestBed.configureTestingModule({
      imports: [
        ConfirmEmailChangeComponent,
        TranslocoTestingModule.forRoot({ langs: { uk: {} }, translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' } }),
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
    const fixture = TestBed.createComponent(ConfirmEmailChangeComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    http.verify();
    vi.clearAllMocks();
    auth.userId.mockReturnValue('12');
  });

  it('ac15 confirms on load, drops the local session and offers to sign in', async () => {
    const fixture = await setup('raw');
    const request = http.expectOne(`${environment.apiUrl}/auth/email-change/confirm`);
    expect(request.request.body).toEqual({ token: 'raw' });
    request.flush({ userId: 12, email: 'mover.new@chnu.edu.ua' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('changed');
    expect(auth.signedOutElsewhere).toHaveBeenCalled();
    (fixture.nativeElement.querySelector('[data-testid="confirm-email-sign-in"]') as HTMLButtonElement).click();
    expect(auth.login).toHaveBeenCalledWith('/profile');
  });

  it.each([
    ['another user', '7'],
    ['nobody', null],
  ])('f3 keeps the local session when %s is signed in here', async (_who, signedIn) => {
    auth.userId.mockReturnValue(signedIn);
    const fixture = await setup('raw');
    http.expectOne(`${environment.apiUrl}/auth/email-change/confirm`).flush({ userId: 12, email: 'mover.new@chnu.edu.ua' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('changed');
    expect(auth.signedOutElsewhere).not.toHaveBeenCalled();
  });

  it.each([
    [410, 'token-invalid', 'invalid'],
    [409, 'email-taken', 'taken'],
    [500, 'unknown', 'failed'],
  ])('ac17 shows the outcome of %s %s', async (status, type, state) => {
    const fixture = await setup('raw');
    http
      .expectOne(`${environment.apiUrl}/auth/email-change/confirm`)
      .flush({ type: `urn:awards:problem:${type}` }, { status, statusText: 'Refused' });

    expect(fixture.componentInstance.state()).toBe(state);
    expect(auth.signedOutElsewhere).not.toHaveBeenCalled();
  });

  it('ac17 a link without a token is invalid without a request', async () => {
    const fixture = await setup(null);
    expect(fixture.componentInstance.state()).toBe('invalid');
  });
});
