import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject } from '@angular/core';
import { Observable, firstValueFrom, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';

export interface NoticeVersion {
  version: string;
  publishedOn: string;
}

export interface PrivacyNotice extends NoticeVersion {
  versions: NoticeVersion[];
}

export type ConsentType = 'DATA_PROCESSING' | 'ANALYTICS';

export interface ConsentOverview {
  notice: {
    currentVersion: string;
    acknowledgedVersion: string | null;
    acknowledgedAt: string | null;
    acknowledgementRequired: boolean;
  };
  consents: { type: ConsentType; granted: boolean; changedAt: string | null }[];
  history: { type: ConsentType; version: string; granted: boolean; at: string }[];
}

/** How long a read overview answers the acknowledgement guard before it is read again. */
export const OVERVIEW_TTL_MS = 15 * 60 * 1000;

interface CachedOverview {
  userId: string | null;
  readAt: number;
  overview: ConsentOverview;
}

@Injectable({ providedIn: 'root' })
export class ConsentsService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiUrl}/users/me/consents`;
  private cached: CachedOverview | null = null;

  constructor() {
    effect(() => {
      if (!this.auth.isAuthenticated()) {
        this.cached = null;
      }
    });
  }

  notice(): Observable<PrivacyNotice> {
    return this.http.get<PrivacyNotice>(`${environment.apiUrl}/public/privacy-notice`);
  }

  /**
   * The text of one notice version, served as a static page next to the application.
   *
   * @param version the notice version
   * @param lang the interface language
   * @returns the HTML of the notice
   */
  noticeText(version: string, lang: string): Observable<string> {
    return this.http.get(`/privacy/${encodeURIComponent(version)}/${lang}.html`, {
      responseType: 'text',
    });
  }

  /**
   * The overview of the signed-in person, read once per sign-in and again after {@link OVERVIEW_TTL_MS}.
   *
   * @returns the cached or freshly read overview
   */
  async overview(): Promise<ConsentOverview> {
    const userId = this.auth.userId();
    const cached = this.cached;
    if (cached?.userId === userId && Date.now() - cached.readAt < OVERVIEW_TTL_MS) {
      return cached.overview;
    }
    return this.remember(await firstValueFrom(this.http.get<ConsentOverview>(this.base)));
  }

  acknowledge(version: string): Observable<ConsentOverview> {
    return this.http
      .post<ConsentOverview>(`${this.base}/notice-acknowledgement`, { version })
      .pipe(tap((overview) => this.remember(overview)));
  }

  private remember(overview: ConsentOverview): ConsentOverview {
    this.cached = { userId: this.auth.userId(), readAt: Date.now(), overview };
    return overview;
  }
}
