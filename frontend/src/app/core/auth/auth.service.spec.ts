import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OAuthErrorEvent, OAuthEvent, OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { UserProfile } from './user-profile';

const profile: UserProfile = {
  id: 5,
  email: 'employee.fmi@chnu.edu.ua',
  firstName: 'Anastasia',
  lastName: 'Employee',
  roles: [],
  organization: { id: 64, name: 'Department', nameUk: 'Кафедра', code: 'DAI', type: 'DEPARTMENT' },
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  lastLoginAt: null,
};

function refreshError(status: number): OAuthEvent {
  return new OAuthErrorEvent('token_refresh_error', new HttpErrorResponse({ status }));
}

describe('AuthService', () => {
  let events: Subject<OAuthEvent>;
  let oauth: {
    configure: ReturnType<typeof vi.fn>;
    loadDiscoveryDocumentAndTryLogin: ReturnType<typeof vi.fn>;
    setupAutomaticSilentRefresh: ReturnType<typeof vi.fn>;
    hasValidAccessToken: ReturnType<typeof vi.fn>;
    initCodeFlow: ReturnType<typeof vi.fn>;
    revokeTokenAndLogout: ReturnType<typeof vi.fn>;
    logOut: ReturnType<typeof vi.fn>;
    events: Subject<OAuthEvent>;
    state: string | undefined;
  };
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    events = new Subject<OAuthEvent>();
    oauth = {
      configure: vi.fn(),
      loadDiscoveryDocumentAndTryLogin: vi.fn().mockResolvedValue(true),
      setupAutomaticSilentRefresh: vi.fn(),
      hasValidAccessToken: vi.fn().mockReturnValue(false),
      initCodeFlow: vi.fn(),
      revokeTokenAndLogout: vi.fn().mockResolvedValue(undefined),
      logOut: vi.fn(),
      events,
      state: undefined,
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', children: [] }]),
        { provide: OAuthService, useValue: oauth },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac11 starts the code flow with the target url when login is requested, once per page', () => {
    service.login('/awards/3');
    service.login('/');

    expect(oauth.initCodeFlow).toHaveBeenCalledTimes(1);
    expect(oauth.initCodeFlow).toHaveBeenCalledWith('/awards/3');
  });

  it('ac12 loads the profile after a successful login during initialisation', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);

    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
    await init;

    expect(oauth.configure).toHaveBeenCalled();
    expect(oauth.setupAutomaticSilentRefresh).toHaveBeenCalled();
    expect(service.isAuthenticated()).toBe(true);
    expect(service.profile()).toEqual(profile);
    expect(service.fullName()).toBe('Anastasia Employee');
  });

  it('ac12 stays anonymous without a token and reacts to token events', async () => {
    await service.init();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.fullName()).toBe('');

    oauth.hasValidAccessToken.mockReturnValue(true);
    events.next({ type: 'token_received' } as OAuthEvent);
    expect(service.isAuthenticated()).toBe(true);

    events.next(refreshError(400));
    expect(service.isAuthenticated()).toBe(false);
  });

  it('ac61 drops the tokens and starts the sign-in flow when the refresh fails', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
    await init;

    events.next(refreshError(400));

    expect(oauth.logOut).toHaveBeenCalledWith(true);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.profile()).toBeNull();
    expect(oauth.initCodeFlow).toHaveBeenCalledWith('/');
  });

  it('ac61 keeps the session when the refresh fails for a transient reason', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
    await init;

    events.next(refreshError(0));
    events.next(refreshError(503));

    expect(oauth.logOut).not.toHaveBeenCalled();
    expect(service.isAuthenticated()).toBe(true);
    expect(oauth.initCodeFlow).not.toHaveBeenCalled();
  });

  it('ac61 a lost session on a public page drops the tokens without starting the sign-in flow', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
    await init;
    await TestBed.inject(Router).navigateByUrl('/reset-password?token=abc');

    service.signedOutElsewhere();

    expect(oauth.logOut).toHaveBeenCalledWith(true);
    expect(service.isAuthenticated()).toBe(false);
    expect(oauth.initCodeFlow).not.toHaveBeenCalled();
  });

  it('ac61 treats a refused profile at start-up as not signed in instead of failing', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush({}, { status: 401, statusText: 'Unauthorized' });
    await init;

    expect(oauth.logOut).toHaveBeenCalledWith(true);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.profile()).toBeNull();
    expect(oauth.initCodeFlow).not.toHaveBeenCalled();
  });

  it('ac61 still fails at start-up on a server error so the outage is visible', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush({}, { status: 500, statusText: 'Server Error' });

    await expect(init).rejects.toBeTruthy();
  });

  it('ac18 revokes the token and forgets the profile on logout', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
    await init;

    await service.logout();

    expect(oauth.revokeTokenAndLogout).toHaveBeenCalled();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.profile()).toBeNull();
  });

  it('returns the stored target url or the root', () => {
    expect(service.targetUrl()).toBe('/');
    oauth.state = encodeURIComponent('/awards/3');
    expect(service.targetUrl()).toBe('/awards/3');
  });
});
