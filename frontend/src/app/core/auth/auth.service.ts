import { HttpClient, HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthErrorEvent, OAuthService } from 'angular-oauth2-oidc';
import { debounceTime, filter, firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
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
];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly formCopies = inject(FormCopiesService);
  private loginStarted = false;
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
    await this.oauth.loadDiscoveryDocumentAndTryLogin();
    this.oauth.events
      .pipe(
        filter((event) => event.type === 'token_expires'),
        debounceTime(1000),
      )
      .subscribe(() => {
        this.refreshOnce().catch(() => undefined);
      });
    if (this.oauth.hasValidAccessToken()) {
      try {
        await this.loadProfile();
        this.remember();
      } catch (err) {
        if (err instanceof HttpErrorResponse && err.status === HttpStatusCode.Unauthorized) {
          this.oauth.logOut(true);
          this.forget();
        } else {
          throw err;
        }
      }
    }
  }

  /** Starts the code flow once per page; the guard and a failed refresh may both ask for it. */
  login(targetUrl = '/'): void {
    if (this.loginStarted) {
      return;
    }
    this.loginStarted = true;
    this.oauth.initCodeFlow(targetUrl);
  }

  /** Where the code flow should land after the callback; set by {@link login}. */
  targetUrl(): string {
    const state = this.oauth.state;
    return state ? decodeURIComponent(state) : '/';
  }

  async logout(): Promise<void> {
    this.formCopies.clearAll();
    await this.oauth.revokeTokenAndLogout();
    this.forget();
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

  private remember(): void {
    this.isAuthenticated.set(this.oauth.hasValidAccessToken());
    this.accessToken.set(this.oauth.getAccessToken() ?? null);
    const userId = this.userId();
    if (userId) {
      this.formCopies.clearOthers(userId);
    }
  }

  private forget(): void {
    this.isAuthenticated.set(false);
    this.accessToken.set(null);
    this.profile.set(null);
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
