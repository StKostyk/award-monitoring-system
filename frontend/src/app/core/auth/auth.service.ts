import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { authConfig } from './auth.config';
import { UserProfile } from './user-profile';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly http = inject(HttpClient);

  readonly isAuthenticated = signal(false);
  readonly profile = signal<UserProfile | null>(null);
  readonly fullName = computed(() => {
    const profile = this.profile();
    return profile ? `${profile.firstName} ${profile.lastName}` : '';
  });

  async init(): Promise<void> {
    this.oauth.configure(authConfig);
    this.oauth.events.subscribe((event) => {
      if (event.type === 'token_received' || event.type === 'token_refreshed') {
        this.isAuthenticated.set(this.oauth.hasValidAccessToken());
      }
      if (event.type === 'logout' || event.type === 'token_refresh_error' || event.type === 'session_terminated') {
        this.isAuthenticated.set(false);
        this.profile.set(null);
      }
    });
    await this.oauth.loadDiscoveryDocumentAndTryLogin();
    this.oauth.setupAutomaticSilentRefresh();
    this.isAuthenticated.set(this.oauth.hasValidAccessToken());
    if (this.isAuthenticated()) {
      await this.loadProfile();
    }
  }

  login(targetUrl = '/'): void {
    this.oauth.initCodeFlow(targetUrl);
  }

  /** Where the code flow should land after the callback; set by {@link login}. */
  targetUrl(): string {
    const state = this.oauth.state;
    return state ? decodeURIComponent(state) : '/';
  }

  async logout(): Promise<void> {
    await this.oauth.revokeTokenAndLogout();
    this.isAuthenticated.set(false);
    this.profile.set(null);
  }

  async loadProfile(): Promise<UserProfile> {
    const profile = await firstValueFrom(this.http.get<UserProfile>(`${environment.apiUrl}/users/me`));
    this.profile.set(profile);
    return profile;
  }
}
