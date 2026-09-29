import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OAuthErrorEvent, OAuthEvent, OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { FormCopiesService } from '../storage/form-copies.service';
import { AuthService } from './auth.service';
import { UserProfile } from './user-profile';

const profile: UserProfile = {
  id: 5,
  email: 'employee.fmi@chnu.edu.ua',
  firstName: 'Anastasia',
  lastName: 'Employee',
  roles: [],
  organization: { id: 64, name: 'Department', nameUk: 'Кафедра', code: 'DAI', type: 'DEPARTMENT' },
  faculty: null,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  lastLoginAt: null,
  membershipConfirmed: false,
};

function refreshError(status: number): OAuthEvent {
  return new OAuthErrorEvent('token_refresh_error', new HttpErrorResponse({ status }));
}

function tokenWith(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('AuthService', () => {
  let events: Subject<OAuthEvent>;
  let oauth: {
    configure: ReturnType<typeof vi.fn>;
    loadDiscoveryDocumentAndTryLogin: ReturnType<typeof vi.fn>;
    setupAutomaticSilentRefresh: ReturnType<typeof vi.fn>;
    hasValidAccessToken: ReturnType<typeof vi.fn>;
    getAccessToken: ReturnType<typeof vi.fn>;
    initCodeFlow: ReturnType<typeof vi.fn>;
    revokeTokenAndLogout: ReturnType<typeof vi.fn>;
    logOut: ReturnType<typeof vi.fn>;
    getRefreshToken: ReturnType<typeof vi.fn>;
    refreshToken: ReturnType<typeof vi.fn>;
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
      getAccessToken: vi.fn().mockReturnValue(null),
      initCodeFlow: vi.fn(),
      revokeTokenAndLogout: vi.fn().mockResolvedValue(undefined),
      logOut: vi.fn(),
      getRefreshToken: vi.fn().mockReturnValue(null),
      refreshToken: vi.fn(),
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

  it('ac4_6 refreshes once for requests refused together and hands out the new token', async () => {
    oauth.getRefreshToken.mockReturnValue('refresh');
    oauth.refreshToken.mockResolvedValue({});
    oauth.getAccessToken.mockReturnValue('fresh-token');

    const [first, second] = await Promise.all([service.refreshOnce(), service.refreshOnce()]);

    expect([first, second]).toEqual(['fresh-token', 'fresh-token']);
    expect(oauth.refreshToken).toHaveBeenCalledTimes(1);
  });

  it('ac4_6 answers null without a refresh token or when the server refuses the refresh', async () => {
    await expect(service.refreshOnce()).resolves.toBeNull();
    expect(oauth.refreshToken).not.toHaveBeenCalled();

    oauth.getRefreshToken.mockReturnValue('refresh');
    oauth.refreshToken.mockRejectedValue(new HttpErrorResponse({ status: 400 }));
    await expect(service.refreshOnce()).resolves.toBeNull();
  });

  it('ac4_6 lets a network failure of the refresh reject so the session is kept', async () => {
    oauth.getRefreshToken.mockReturnValue('refresh');
    oauth.refreshToken.mockRejectedValue(new HttpErrorResponse({ status: 0 }));

    await expect(service.refreshOnce()).rejects.toMatchObject({ status: 0 });
  });

  it('ac4_6 hands out a token renewed meanwhile without refreshing again', async () => {
    oauth.getRefreshToken.mockReturnValue('refresh');
    oauth.getAccessToken.mockReturnValue('renewed-token');
    oauth.hasValidAccessToken.mockReturnValue(true);

    await expect(service.refreshOnce('refused-token')).resolves.toBe('renewed-token');
    expect(oauth.refreshToken).not.toHaveBeenCalled();
  });

  it('ac4_6 the expiry timer refreshes through the same single flight', async () => {
    vi.useFakeTimers();
    try {
      oauth.getRefreshToken.mockReturnValue('refresh');
      oauth.refreshToken.mockResolvedValue({});
      await service.init();

      events.next({ type: 'token_expires' } as OAuthEvent);
      events.next({ type: 'token_expires' } as OAuthEvent);
      await vi.advanceTimersByTimeAsync(1000);

      expect(oauth.refreshToken).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });

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
    expect(oauth.setupAutomaticSilentRefresh).not.toHaveBeenCalled();
    expect(service.isAuthenticated()).toBe(true);
    expect(service.profile()).toEqual(profile);
    expect(service.fullName()).toBe('Anastasia Employee');
  });

  it('ac2_10_exposes_the_permissions_carried_by_the_access_token', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    oauth.getAccessToken.mockReturnValue(
      tokenWith({ permissions: ['user:read:scope'], role_scopes: ['DEAN:9'] }),
    );

    const init = service.init();
    await Promise.resolve();
    http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
    await init;

    expect(service.permissions().hasPermission('user:read:scope')).toBe(true);
    expect(service.permissions().roleScopes).toEqual([{ role: 'DEAN', organizationId: 9 }]);

    await service.logout();

    expect(service.accessToken()).toBeNull();
    expect(service.permissions().permissions).toEqual([]);
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

  it('f4 a sign-in removes the form copies of other users and keeps the own ones', async () => {
    const copies = TestBed.inject(FormCopiesService);
    localStorage.clear();
    copies.save('21', 'award-new', { titleUk: 'Своя' });
    copies.save('22', 'award-5', { titleUk: 'Чужа' });
    await service.init();

    oauth.hasValidAccessToken.mockReturnValue(true);
    oauth.getAccessToken.mockReturnValue(tokenWith({ sub: '21' }));
    events.next({ type: 'token_received' } as OAuthEvent);

    expect(copies.load('21', 'award-new')).toEqual({ titleUk: 'Своя' });
    expect(copies.load('22', 'award-5')).toBeNull();
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
    http
      .expectOne(`${environment.apiUrl}/users/me`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });
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
    http
      .expectOne(`${environment.apiUrl}/users/me`)
      .flush({}, { status: 500, statusText: 'Server Error' });

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
