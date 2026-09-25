import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef, RoleType } from '../../core/auth/user-profile';

export type DelegationState = 'active' | 'upcoming' | 'expired' | 'revoked';

export interface UserBrief {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
}

export interface Delegation {
  id: number;
  role: RoleType;
  organization: OrganizationRef;
  delegator: UserBrief;
  delegate: UserBrief;
  validFrom: string;
  validTo: string;
  reason: string | null;
  state: DelegationState;
  createdAt: string;
  revokedAt: string | null;
}

export interface DelegationList {
  given: Delegation[];
  received: Delegation[];
}

export interface DelegationRequest {
  delegateId: number;
  role: RoleType;
  organizationId: number;
  validFrom: string;
  validTo: string;
  reason?: string | null;
}

@Injectable({ providedIn: 'root' })
export class DelegationsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/delegations`;

  list(state?: DelegationState, delegatorId?: number): Observable<DelegationList> {
    let params = new HttpParams();
    if (state) {
      params = params.set('state', state);
    }
    if (delegatorId) {
      params = params.set('delegatorId', delegatorId);
    }
    return this.http.get<DelegationList>(this.base, { params });
  }

  create(request: DelegationRequest): Observable<Delegation> {
    return this.http.post<Delegation>(this.base, request);
  }

  revoke(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
