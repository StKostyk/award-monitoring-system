import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { environment } from '../../../../../environments/environment';
import { NotMeComponent } from './not-me.component';

describe('NotMeComponent', () => {
  let http: HttpTestingController;

  async function setup(token: string | null) {
    await TestBed.configureTestingModule({
      imports: [
        NotMeComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: { notMe: { done: 'Доступ відкликано', errors: { network: 'Сервер недоступний' } } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(NotMeComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('ac53 ac54 revokes access with the token only after the button is pressed', async () => {
    const fixture = await setup('raw');
    http.expectNone(`${environment.apiUrl}/auth/security/revoke`);

    (fixture.nativeElement.querySelector('[data-testid="not-me-confirm"]') as HTMLButtonElement).click();
    const request = http.expectOne(`${environment.apiUrl}/auth/security/revoke`);
    expect(request.request.body).toEqual({ token: 'raw' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('done');
    expect(fixture.nativeElement.querySelector('[data-testid="not-me-done"]').textContent).toContain('відкликано');
  });

  it('ac53 treats a used link as invalid and offers a password reset', async () => {
    const fixture = await setup('old');

    fixture.componentInstance.confirm();
    http
      .expectOne(`${environment.apiUrl}/auth/security/revoke`)
      .flush({ type: 'urn:awards:problem:token-invalid' }, { status: 410, statusText: 'Gone' });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('invalid');
    expect(fixture.nativeElement.querySelector('[data-testid="not-me-go-forgot"]')).not.toBeNull();
  });

  it('ac54 keeps the confirmation for a transient error', async () => {
    const fixture = await setup('raw');

    fixture.componentInstance.confirm();
    http.expectOne(`${environment.apiUrl}/auth/security/revoke`).error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('confirm');
    expect(fixture.nativeElement.querySelector('[data-testid="not-me-error"]').textContent).toContain('недоступний');
  });

  it('ac53 a missing token is invalid from the start and never calls the server', async () => {
    const fixture = await setup(null);

    expect(fixture.componentInstance.state()).toBe('invalid');
    fixture.componentInstance.confirm();
    http.expectNone(`${environment.apiUrl}/auth/security/revoke`);
  });
});
