import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { PrivacyNotice } from '../consents.service';
import { PrivacyNoticeComponent } from './privacy-notice.component';

const noticeUrl = `${environment.apiUrl}/public/privacy-notice`;
const acknowledgeUrl = `${environment.apiUrl}/users/me/consents/notice-acknowledgement`;
const notice: PrivacyNotice = {
  version: '1.1',
  publishedOn: '2026-11-01',
  versions: [
    { version: '1.1', publishedOn: '2026-11-01' },
    { version: '1.0', publishedOn: '2026-10-15' },
  ],
};

describe('PrivacyNoticeComponent', () => {
  const lang = signal<'uk' | 'en'>('uk');
  const auth = {
    isAuthenticated: signal(false),
    userId: signal('7'),
    logout: vi.fn().mockResolvedValue(undefined),
  };
  let fixture: ComponentFixture<PrivacyNoticeComponent>;
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function open(query: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        PrivacyNoticeComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: { current: lang } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(PrivacyNoticeComponent);
    fixture.detectChanges();
    http.expectOne(noticeUrl).flush(notice);
    fixture.detectChanges();
  }

  function text(path: string, html: string): void {
    http.expectOne(path).flush(html);
    fixture.detectChanges();
  }

  function shown(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  beforeEach(() => {
    lang.set('uk');
    auth.isAuthenticated.set(false);
    auth.logout.mockClear();
  });

  afterEach(() => http.verify());

  it('ac1_9 shows the current version in the interface language and reloads it on a language switch', async () => {
    await open();
    text('/privacy/1.1/uk.html', '<h2>Хто відповідає</h2>');
    expect(shown('notice-text')?.innerHTML).toContain('Хто відповідає');

    lang.set('en');
    fixture.detectChanges();
    text('/privacy/1.1/en.html', '<h2>Who is responsible</h2>');

    expect(shown('notice-text')?.innerHTML).toContain('Who is responsible');
    expect(shown('notice-acknowledge')).toBeNull();
  });

  it('ac1_9 loads an older version from the selector', async () => {
    await open();
    text('/privacy/1.1/uk.html', '<p>1.1</p>');

    fixture.componentInstance.version.set('1.0');
    fixture.detectChanges();
    text('/privacy/1.0/uk.html', '<p>1.0</p>');

    expect(shown('notice-text')?.textContent).toBe('1.0');
  });

  it('ac1_9 says so when a text cannot be loaded', async () => {
    await open();
    http.expectOne('/privacy/1.1/uk.html').flush('', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(shown('notice-failed')).not.toBeNull();
  });

  it('ac1_8 acknowledges the current version and returns to the requested page', async () => {
    auth.isAuthenticated.set(true);
    await open({ acknowledge: 'true', returnUrl: '/awards?status=DRAFT' });
    text('/privacy/1.1/uk.html', '<p>1.1</p>');
    expect(shown('notice-acknowledge-hint')).not.toBeNull();

    shown('notice-acknowledge')?.click();
    const request = http.expectOne(acknowledgeUrl);
    expect(request.request.body).toEqual({ version: '1.1' });
    request.flush({});

    expect(navigateByUrl).toHaveBeenCalledWith('/awards?status=DRAFT');
  });

  it.each(['https://evil.example', '//evil.example', '/\\evil.example'])(
    'ac1_8 returns to the start page instead of %s',
    async (returnUrl) => {
      auth.isAuthenticated.set(true);
      await open({ acknowledge: 'true', returnUrl });
      text('/privacy/1.1/uk.html', '<p>1.1</p>');

      fixture.componentInstance.acknowledge();
      http.expectOne(acknowledgeUrl).flush({});

      expect(navigateByUrl).toHaveBeenCalledWith('/');
    },
  );

  it('ac1_8 reloads the notice when it changed again before the acknowledgement', async () => {
    auth.isAuthenticated.set(true);
    await open({ acknowledge: 'true' });
    text('/privacy/1.1/uk.html', '<p>1.1</p>');

    fixture.componentInstance.acknowledge();
    http.expectOne(acknowledgeUrl).flush(
      {
        type: 'urn:awards:problem:validation-failed',
        errors: [{ field: 'version', code: 'outdated', message: 'outdated' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    http.expectOne(noticeUrl).flush({ ...notice, version: '1.2' });
    fixture.detectChanges();
    http.expectOne('/privacy/1.2/uk.html').flush('<p>1.2</p>');

    expect(fixture.componentInstance.error()).toBe('privacy.notice.errors.outdated');
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(fixture.componentInstance.sending()).toBe(false);
  });

  it('ac1_8 maps other failures of the acknowledgement', async () => {
    auth.isAuthenticated.set(true);
    await open({ acknowledge: 'true' });
    text('/privacy/1.1/uk.html', '<p>1.1</p>');

    fixture.componentInstance.acknowledge();
    http.expectOne(acknowledgeUrl).flush(null, { status: 503, statusText: 'Unavailable' });

    expect(fixture.componentInstance.error()).toBe('privacy.notice.errors.network');
  });

  it('ac1_8 signs out from the acknowledgement page', async () => {
    auth.isAuthenticated.set(true);
    await open({ acknowledge: 'true' });
    text('/privacy/1.1/uk.html', '<p>1.1</p>');

    shown('notice-sign-out')?.click();

    expect(auth.logout).toHaveBeenCalled();
  });
});
