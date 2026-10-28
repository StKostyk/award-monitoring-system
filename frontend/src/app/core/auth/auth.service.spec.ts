import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OAuthErrorEvent, OAuthEvent, OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { ConnectivityService } from '../connectivity/connectivity.service';
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

@Component({ template: '' })
class FormStubComponent {}

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
    loadDiscoveryDocument: ReturnType<typeof vi.fn>;
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
      loadDiscoveryDocument: vi.fn().mockResolvedValue({}),
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

  it('ac11 starts the code flow with the target url when login is requested, once per page', async () => {
    await service.init();
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

  describe('from a form with unsaved values', () => {
    let leave: ReturnType<typeof vi.fn>;
    let clearAll: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
      leave = vi.fn().mockReturnValue(false);
      const router = TestBed.inject(Router);
      router.resetConfig([
        { path: 'awards/new', component: FormStubComponent, canDeactivate: [leave] },
        { path: '**', children: [] },
      ]);
      oauth.hasValidAccessToken.mockReturnValue(true);
      const init = service.init();
      await Promise.resolve();
      http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
      await init;
      await router.navigateByUrl('/awards/new');
      clearAll = vi.spyOn(TestBed.inject(FormCopiesService), 'clearAll');
    });

    it('ac1_staying_on_the_form_keeps_the_session_and_the_copies', async () => {
      await service.logout();

      expect(leave).toHaveBeenCalled();
      expect(clearAll).not.toHaveBeenCalled();
      expect(oauth.revokeTokenAndLogout).not.toHaveBeenCalled();
      expect(service.isAuthenticated()).toBe(true);
      expect(TestBed.inject(Router).url).toBe('/awards/new');
    });

    it('ac1_staying_on_the_form_offline_keeps_the_session_and_the_copies', async () => {
      TestBed.inject(ConnectivityService).unreachable();

      await service.logout();

      expect(clearAll).not.toHaveBeenCalled();
      expect(oauth.logOut).not.toHaveBeenCalled();
      expect(service.isAuthenticated()).toBe(true);
      expect(TestBed.inject(Router).url).toBe('/awards/new');
    });

    it('ac1_signs_out_when_the_form_is_left_after_staying_once', async () => {
      TestBed.inject(ConnectivityService).unreachable();
      await service.logout();
      leave.mockReturnValue(true);

      await service.logout();

      expect(clearAll).toHaveBeenCalled();
      expect(service.isAuthenticated()).toBe(false);
      expect(TestBed.inject(Router).url).toBe('/offline?next=%2F');
    });

    it('ac1_signs_out_from_the_page_it_would_open', async () => {
      leave.mockReturnValue(true);
      await TestBed.inject(Router).navigateByUrl('/');
      expect(TestBed.inject(Router).url).toBe('/');

      await service.logout();

      expect(oauth.revokeTokenAndLogout).toHaveBeenCalled();
      expect(service.isAuthenticated()).toBe(false);
    });

    it('ac1_signs_out_when_the_landing_page_fails_to_open', async () => {
      leave.mockReturnValue(true);
      oauth.revokeTokenAndLogout.mockRejectedValue(new HttpErrorResponse({ status: 0 }));
      TestBed.inject(Router).resetConfig([
        { path: 'awards/new', component: FormStubComponent, canDeactivate: [leave] },
        {
          path: '',
          pathMatch: 'full',
          loadComponent: () => Promise.reject(new Error('chunk unavailable')),
        },
        { path: '**', children: [] },
      ]);

      await service.logout();

      expect(clearAll).toHaveBeenCalled();
      expect(oauth.revokeTokenAndLogout).toHaveBeenCalled();
      expect(oauth.logOut).toHaveBeenCalledWith(true);
      expect(service.isAuthenticated()).toBe(false);
      expect(TestBed.inject(Router).url).toBe('/offline?next=%2F');
    });

    it('ac1_asks_the_form_before_anything_is_removed', async () => {
      leave.mockImplementation(() => clearAll.mock.calls.length === 0 && service.isAuthenticated());
      TestBed.inject(ConnectivityService).unreachable();

      await service.logout();

      expect(clearAll).toHaveBeenCalled();
      expect(oauth.logOut).toHaveBeenCalledWith(true);
      expect(service.isAuthenticated()).toBe(false);
      expect(TestBed.inject(Router).url).toBe('/offline?next=%2F');
    });
  });

  it('returns the stored target url or the root', () => {
    expect(service.targetUrl()).toBe('/');
    oauth.state = encodeURIComponent('/awards/3');
    expect(service.targetUrl()).toBe('/awards/3');
  });

  describe('without a connection', () => {
    const unreachable = new HttpErrorResponse({ status: 0 });
    let connectivity: ConnectivityService;

    beforeEach(() => {
      connectivity = TestBed.inject(ConnectivityService);
      oauth.loadDiscoveryDocumentAndTryLogin.mockRejectedValue(unreachable);
    });

    async function startOffline(): Promise<void> {
      connectivity.unreachable();
      const init = service.init();
      await new Promise((resolve) => setTimeout(resolve));
      for (const request of http.match(`${environment.apiUrl}/users/me`)) {
        request.error(new ProgressEvent('error'), { status: 0 });
      }
      await init;
    }

    it('ac1_7_starts_with_the_stored_session_when_discovery_and_profile_are_unreachable', async () => {
      oauth.hasValidAccessToken.mockReturnValue(true);
      oauth.getAccessToken.mockReturnValue(tokenWith({ sub: '5' }));

      await startOffline();

      expect(service.isAuthenticated()).toBe(true);
      expect(service.profile()).toBeNull();
      expect(oauth.initCodeFlow).not.toHaveBeenCalled();
    });

    it('ac1_7_keeps_a_session_whose_access_token_expired_while_a_refresh_token_is_stored', async () => {
      oauth.getRefreshToken.mockReturnValue('refresh');

      await startOffline();

      expect(service.isAuthenticated()).toBe(true);
    });

    it('ac1_7_still_fails_at_start_up_when_discovery_is_refused', async () => {
      oauth.loadDiscoveryDocumentAndTryLogin.mockRejectedValue(
        new HttpErrorResponse({ status: 500 }),
      );

      await expect(service.init()).rejects.toBeTruthy();
    });

    it('ac1_9_loads_discovery_renews_the_token_once_and_loads_the_profile_when_back_online', async () => {
      oauth.getRefreshToken.mockReturnValue('refresh');
      oauth.refreshToken.mockImplementation(() => {
        oauth.hasValidAccessToken.mockReturnValue(true);
        oauth.getAccessToken.mockReturnValue(tokenWith({ sub: '5' }));
        events.next({ type: 'token_refreshed' } as OAuthEvent);
        return Promise.resolve({});
      });
      await startOffline();

      const reconnect = service.reconnect();
      await new Promise((resolve) => setTimeout(resolve));
      http.expectOne(`${environment.apiUrl}/users/me`).flush(profile);
      await reconnect;

      expect(oauth.loadDiscoveryDocument).toHaveBeenCalledTimes(1);
      expect(oauth.refreshToken).toHaveBeenCalledTimes(1);
      expect(service.profile()).toEqual(profile);
      await service.reconnect();
      expect(oauth.refreshToken).toHaveBeenCalledTimes(1);
    });

    it('ac1_9_retries_a_token_renewal_that_failed_offline_when_the_connection_returns', async () => {
      oauth.loadDiscoveryDocumentAndTryLogin.mockResolvedValue(true);
      oauth.getRefreshToken.mockReturnValue('refresh');
      oauth.refreshToken.mockRejectedValueOnce(unreachable).mockResolvedValue({});
      vi.useFakeTimers();
      try {
        await service.init();
        events.next({ type: 'token_expires' } as OAuthEvent);
        await vi.advanceTimersByTimeAsync(1000);
      } finally {
        vi.useRealTimers();
      }
      connectivity.unreachable();

      connectivity.reachable();
      await new Promise((resolve) => setTimeout(resolve));

      expect(oauth.refreshToken).toHaveBeenCalledTimes(2);
    });

    it('ac1_8_opens_the_offline_page_instead_of_the_sign_in', async () => {
      await startOffline();

      service.login('/awards');
      await new Promise((resolve) => setTimeout(resolve));

      expect(oauth.initCodeFlow).not.toHaveBeenCalled();
      expect(TestBed.inject(Router).url).toBe('/offline?next=%2Fawards');
    });

    it('ac1_8_loads_the_discovery_document_before_the_sign_in_once_online', async () => {
      await startOffline();
      connectivity.reachable();

      service.login('/awards');
      await new Promise((resolve) => setTimeout(resolve));

      expect(oauth.loadDiscoveryDocument).toHaveBeenCalled();
      expect(oauth.initCodeFlow).toHaveBeenCalledWith('/awards');
    });

    it('ac1_12_signs_out_on_the_device_only_and_opens_the_offline_page', async () => {
      oauth.hasValidAccessToken.mockReturnValue(true);
      await startOffline();
      const clearAll = vi.spyOn(TestBed.inject(FormCopiesService), 'clearAll');

      await service.logout();

      expect(clearAll).toHaveBeenCalled();
      expect(oauth.revokeTokenAndLogout).not.toHaveBeenCalled();
      expect(oauth.logOut).toHaveBeenCalledWith(true);
      expect(service.isAuthenticated()).toBe(false);
      expect(TestBed.inject(Router).url).toBe('/offline?next=%2F');
    });

    it('ac1_12_falls_back_to_the_device_sign_out_when_the_revocation_gets_no_answer', async () => {
      oauth.loadDiscoveryDocumentAndTryLogin.mockResolvedValue(true);
      oauth.revokeTokenAndLogout.mockRejectedValue(unreachable);
      await service.init();

      await service.logout();

      expect(oauth.logOut).toHaveBeenCalledWith(true);
      expect(TestBed.inject(Router).url).toBe('/offline?next=%2F');
    });
  });
});
