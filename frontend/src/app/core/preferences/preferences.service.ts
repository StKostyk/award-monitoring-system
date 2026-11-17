import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DashboardPreferences } from './dashboard-preferences';

/** The colour scheme and dashboard layouts the signed-in account keeps. */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/users/me/dashboard-preferences`;

  read(): Observable<DashboardPreferences> {
    return this.http.get<DashboardPreferences>(this.url);
  }

  /**
   * Replaces the stored preferences.
   *
   * @param preferences the scheme and every layout
   * @return the stored preferences
   */
  save(preferences: DashboardPreferences): Observable<DashboardPreferences> {
    return this.http.put<DashboardPreferences>(this.url, preferences);
  }
}
