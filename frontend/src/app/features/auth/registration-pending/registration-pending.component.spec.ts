import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { RegistrationPendingComponent } from './registration-pending.component';

describe('RegistrationPendingComponent', () => {
  const auth = { login: vi.fn() };
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        RegistrationPendingComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: { pending: { resent: 'Надіслано', errors: { 'too-many-requests': 'Зачекайте' } } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ email: 'x@chnu.edu.ua' }) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac26 resends the link for the address from the query and shows the throttle message', () => {
    const fixture = TestBed.createComponent(RegistrationPendingComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.email.value).toBe('x@chnu.edu.ua');

    fixture.componentInstance.resend();
    const first = http.expectOne(`${environment.apiUrl}/auth/resend-verification`);
    expect(first.request.body).toEqual({ email: 'x@chnu.edu.ua' });
    first.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="pending-notice"]').textContent).toContain('Надіслано');

    fixture.componentInstance.resend();
    http
      .expectOne(`${environment.apiUrl}/auth/resend-verification`)
      .flush({ type: 'urn:awards:problem:too-many-requests' }, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="pending-notice"]').textContent).toContain('Зачекайте');
  });

  it('starts the sign-in flow from the button', () => {
    const fixture = TestBed.createComponent(RegistrationPendingComponent);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="pending-sign-in"]') as HTMLButtonElement).click();

    expect(auth.login).toHaveBeenCalledWith('/');
  });
});
