import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { UserProfile } from '../../core/auth/user-profile';

export interface NameChange {
  firstName?: string;
  lastName?: string;
}

/** The caller's own profile: names, the sign-in address and the data export. */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/users/me`;

  updateNames(change: NameChange): Observable<UserProfile> {
    return this.http.patch<UserProfile>(this.base, change);
  }

  requestEmailChange(newEmail: string, currentPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/email-change`, { newEmail, currentPassword });
  }

  exportData(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/export`, { observe: 'response', responseType: 'blob' });
  }

  confirmEmailChange(token: string): Observable<{ email: string }> {
    return this.http.post<{ email: string }>(`${environment.apiUrl}/auth/email-change/confirm`, { token });
  }
}
