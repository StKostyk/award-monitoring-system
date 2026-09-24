import { HttpClient, HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthErrorEvent, OAuthService } from 'angular-oauth2-oidc';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { authConfig } from './auth.config';
import { readPermissions } from './permissions';
import { UserProfile } from './user-profile';

/** Routes that work without a session; a lost session there must not bounce the visitor to the login page. */
const PUBLIC_ROUTES = [
  '/register',
  '/registration-pending',
  '/verify-email',
  '/forgot-password',
  '/reset-password',
  '/security/not-me',
  '/callback',
];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private loginStarted = false;

  readonly isAuthenticated = signal(false);
  readonly accessToken = signal<string | null>(null);
  readonly profile = signal<UserProfile | null>(null);
  readonly fullName = computed(() => {
    const profile = this.profile();
    return profile ? `${profile.firstName} ${profile.lastName}` : '';
  });
  readonly permissions = computed(() => readPermissions(this.accessToken()));

  async init(): Promise<void> {
    this.oauth.configure(authConfig);
    this.oauth.events.subscribe((event) => {
      if (event.type === 'token_received' || event.type === 'token_refreshed') {
        this.remember();
      }
      if (event.type === 'logout') {
        this.forget();
      }
      if (event.type === 'session_terminated' || (event.type === 'token_refresh_error' && refused(event))) {
        this.signedOutElsewhere();
      }
    });
    window.addEventListener('pageshow', (event) => {
      if (event.persisted) {
        this.loginStarted = false;
      }
    });
    await this.oauth.loadDiscoveryDocumentAndTryLogin();
    this.oauth.setupAutomaticSilentRefresh();
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
    await this.oauth.revokeTokenAndLogout();
    this.forget();
  }

  async loadProfile(): Promise<UserProfile> {
    const profile = await firstValueFrom(this.http.get<UserProfile>(`${environment.apiUrl}/users/me`));
    this.profile.set(profile);
    return profile;
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
  }

  private forget(): void {
    this.isAuthenticated.set(false);
    this.accessToken.set(null);
    this.profile.set(null);
  }
}

/** A refresh answered 400 (`invalid_grant`) or 401: the refresh token is gone. Other failures are transient. */
function refused(event: OAuthErrorEvent | { type: string }): boolean {
  const reason = (event as OAuthErrorEvent).reason;
  return (
    reason instanceof HttpErrorResponse &&
    (reason.status === HttpStatusCode.BadRequest || reason.status === HttpStatusCode.Unauthorized)
  );
}
