import { HttpClient, HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { NavigationCancel, NavigationCancellationCode, Router, UrlTree } from '@angular/router';
import { OAuthErrorEvent, OAuthService } from 'angular-oauth2-oidc';
import { debounceTime, filter, firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { problemType } from '../api/problem';
import { ConnectivityService } from '../connectivity/connectivity.service';
import { FormCopiesService } from '../storage/form-copies.service';
import { authConfig } from './auth.config';
import { readPermissions, readSubject } from './permissions';
import { UserProfile } from './user-profile';

/** Routes that work without a session; a lost session there must not bounce the visitor to the login page. */
const PUBLIC_ROUTES = [
  '/register',
  '/registration-pending',
  '/verify-email',
  '/forgot-password',
  '/reset-password',
  '/security/not-me',
  '/confirm-email-change',
  '/callback',
  '/public/',
  '/not-found',
  '/privacy-notice',
  '/offline',
];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly formCopies = inject(FormCopiesService);
  private readonly connectivity = inject(ConnectivityService);
  private loginStarted = false;
  private discovered = false;
  private renewalFailed = false;
  private refreshing: Promise<string | null> | null = null;

  readonly isAuthenticated = signal(false);
  readonly accessToken = signal<string | null>(null);
  readonly profile = signal<UserProfile | null>(null);
  readonly fullName = computed(() => {
    const profile = this.profile();
    return profile ? `${profile.firstName} ${profile.lastName}` : '';
  });
  readonly permissions = computed(() => readPermissions(this.accessToken()));
  readonly userId = computed(() => readSubject(this.accessToken()));

  async init(): Promise<void> {
    this.oauth.configure(authConfig);
    this.oauth.events.subscribe((event) => {
      if (event.type === 'token_received' || event.type === 'token_refreshed') {
        this.remember();
      }
      if (event.type === 'logout') {
        this.forget();
      }
      if (
        event.type === 'session_terminated' ||
        (event.type === 'token_refresh_error' && refused(event))
      ) {
        this.signedOutElsewhere();
      }
    });
    window.addEventListener('pageshow', (event) => {
      if (event.persisted) {
        this.loginStarted = false;
      }
    });
    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      this.discovered = true;
    } catch (err) {
      unlessUnreachable(err);
    }
    this.oauth.events
      .pipe(
        filter((event) => event.type === 'token_expires'),
        debounceTime(1000),
      )
      .subscribe(() => {
        this.refreshOnce().catch(() => {
          this.renewalFailed = true;
        });
      });
    this.connectivity.reconnected$.subscribe(() => {
      this.reconnect().catch(() => undefined);
    });
    if (this.oauth.hasValidAccessToken()) {
      await this.loadProfileAtStart();
    } else if (!this.discovered && this.oauth.getRefreshToken()) {
      this.resumeOffline();
    }
  }

  /**
   * Starts the code flow once per page; the guard and a failed refresh may both ask for it. Without a connection
   * the offline page opens instead and starts the flow once the connection returns.
   */
  login(targetUrl = '/'): void {
    if (!this.connectivity.online()) {
      void this.router.navigateByUrl(this.offlinePage(targetUrl));
      return;
    }
    if (this.loginStarted) {
      return;
    }
    this.loginStarted = true;
    if (this.discovered) {
      this.oauth.initCodeFlow(targetUrl);
      return;
    }
    this.discover()
      .then(() => this.oauth.initCodeFlow(targetUrl))
      .catch(() => {
        this.loginStarted = false;
        void this.router.navigateByUrl(this.offlinePage(targetUrl));
      });
  }

  /**
   * The offline page, which signs in to the target once the connection returns.
   *
   * @param targetUrl where the sign-in should land
   * @return the page as a router target
   */
  offlinePage(targetUrl: string): UrlTree {
    return this.router.createUrlTree(['/offline'], { queryParams: { next: targetUrl } });
  }

  /**
   * After a start or a token renewal without a connection: loads what is missing once the server answers again,
   * the discovery document first, then the renewed token, then the profile.
   *
   * @return resolves once every missing part is loaded
   */
  async reconnect(): Promise<void> {
    if (!this.discovered) {
      await this.discover();
    }
    if (this.renewalFailed) {
      this.renewalFailed = false;
      await this.refreshOnce().catch(() => undefined);
    }
    if (this.isAuthenticated() && !this.profile()) {
      await this.loadProfileAtStart();
    }
  }

  /** Where the code flow should land after the callback; set by {@link login}. */
  targetUrl(): string {
    const state = this.oauth.state;
    return state ? decodeURIComponent(state) : '/';
  }

  /**
   * Signs out unless the current page refuses to be left, so a form with unsaved values asks before its copy and
   * the tokens are removed; a landing page that fails to open does not stop the sign-out. Without a connection the
   * tokens are dropped on this device only and the offline page opens.
   */
  async logout(): Promise<void> {
    const online = this.connectivity.online();
    if (!(await this.leaveFor(online ? '/' : this.offlinePage('/')))) {
      return;
    }
    this.formCopies.clearAll();
    if (online) {
      try {
        await this.oauth.revokeTokenAndLogout();
        this.forget();
        return;
      } catch (err) {
        unlessUnreachable(err);
      }
    }
    this.oauth.logOut(true);
    this.forget();
    await this.router.navigateByUrl(this.offlinePage('/'));
  }

  async loadProfile(): Promise<UserProfile> {
    const profile = await firstValueFrom(
      this.http.get<UserProfile>(`${environment.apiUrl}/users/me`),
    );
    this.profile.set(profile);
    return profile;
  }

  /**
   * Renews the access token once for the expiry timer and every request refused at the same moment, because a
   * refresh token presented twice ends the whole session. A token renewed since the refused request was sent
   * is handed out without another refresh.
   *
   * @param refusedToken the access token the refused request carried, if any
   * @returns the access token to use, or null when there is no refresh token or the server refused it; a
   *          network or server failure rejects, so the caller keeps the session
   */
  refreshOnce(refusedToken?: string | null): Promise<string | null> {
    const current = this.oauth.getAccessToken() || null;
    if (refusedToken && current && current !== refusedToken && this.oauth.hasValidAccessToken()) {
      return Promise.resolve(current);
    }
    if (!this.oauth.getRefreshToken()) {
      return Promise.resolve(null);
    }
    this.refreshing ??= this.oauth
      .refreshToken()
      .then(() => this.oauth.getAccessToken() || null)
      .catch((err: unknown) => {
        if (refusedError(err)) {
          return null;
        }
        throw err;
      })
      .finally(() => {
        this.refreshing = null;
      });
    return this.refreshing;
  }

  /** The session was ended elsewhere (reset, revocation): drop the tokens and, on a guarded page, sign in again. */
  signedOutElsewhere(): void {
    if (!this.isAuthenticated()) {
      return;
    }
    this.oauth.logOut(true);
    this.forget();
    const url = this.router.url;
    if (!PUBLIC_ROUTES.some((route) => url.startsWith(route))) {
      this.login(url);
    }
  }

  /** A refused profile ends the session; an unreachable server keeps it until the profile can be loaded. */
  private async loadProfileAtStart(): Promise<void> {
    try {
      await this.loadProfile();
      this.remember();
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === HttpStatusCode.Unauthorized) {
        this.oauth.logOut(true);
        this.forget();
        return;
      }
      unlessUnreachable(err);
      this.remember();
    }
  }

  /** Started without a connection on an expired access token: the stored session holds until it is renewed. */
  private resumeOffline(): void {
    this.isAuthenticated.set(true);
    this.accessToken.set(this.oauth.getAccessToken() || null);
    this.renewalFailed = true;
  }

  private async discover(): Promise<void> {
    await this.oauth.loadDiscoveryDocument();
    this.discovered = true;
  }

  private remember(): void {
    this.isAuthenticated.set(this.oauth.hasValidAccessToken());
    this.accessToken.set(this.oauth.getAccessToken() ?? null);
    const userId = this.userId();
    if (userId) {
      this.formCopies.clearOthers(userId);
    }
  }

  private async leaveFor(target: string | UrlTree): Promise<boolean> {
    let refused = false;
    const cancels = this.router.events
      .pipe(filter((event) => event instanceof NavigationCancel))
      .subscribe((event) => {
        refused = event.code === NavigationCancellationCode.GuardRejected;
      });
    await this.router.navigateByUrl(target).catch(() => false);
    cancels.unsubscribe();
    return !refused;
  }

  private forget(): void {
    this.isAuthenticated.set(false);
    this.accessToken.set(null);
    this.profile.set(null);
  }
}

/** Rethrows anything but an unreachable server, which start-up and sign-out outlast. */
function unlessUnreachable(err: unknown): void {
  if (problemType(err) !== 'network') {
    throw err;
  }
}

/** A refresh answered 400 (`invalid_grant`) or 401: the refresh token is gone. Other failures are transient. */
function refused(event: OAuthErrorEvent | { type: string }): boolean {
  return refusedError((event as OAuthErrorEvent).reason);
}

function refusedError(reason: unknown): boolean {
  return (
    reason instanceof HttpErrorResponse &&
    (reason.status === HttpStatusCode.BadRequest || reason.status === HttpStatusCode.Unauthorized)
  );
}
