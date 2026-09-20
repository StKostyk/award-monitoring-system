import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AccountStatus, OrganizationRef, OrganizationType } from '../../core/auth/user-profile';

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  organizationId: number;
}

export interface RegistrationResponse {
  email: string;
  status: AccountStatus;
}

export interface OrganizationSummary {
  id: number;
  name: string;
  nameUk: string | null;
  code: string | null;
  type: OrganizationType;
  parent: OrganizationRef | null;
}

@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth`;

  departments(): Observable<OrganizationSummary[]> {
    return this.http.get<OrganizationSummary[]>(`${environment.apiUrl}/organizations`, {
      params: { type: 'DEPARTMENT' },
    });
  }

  register(request: RegisterRequest): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.base}/register`, request);
  }

  verifyEmail(token: string, password: string): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.base}/verify-email`, { token, password });
  }

  resendVerification(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/resend-verification`, { email });
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/password-reset/request`, { email });
  }

  confirmPasswordReset(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.base}/password-reset/confirm`, { token, password });
  }
}
