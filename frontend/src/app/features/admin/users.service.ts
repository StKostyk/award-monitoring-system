import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AccountStatus,
  OrganizationRef,
  OrganizationType,
  RoleAssignment,
  RoleType,
} from '../../core/auth/user-profile';
import { OrganizationSummary } from '../auth/registration.service';

export interface UserSummary {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  organization: OrganizationRef;
  status: AccountStatus;
  roles: RoleAssignment[];
  membershipConfirmed: boolean;
}

export interface UserDetail extends UserSummary {
  createdAt: string;
  lastLoginAt: string | null;
  roleHistory: RoleAssignment[];
}

export interface UserPage {
  content: UserSummary[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

export interface UserFilters {
  q: string;
  role: RoleType | null;
  status: AccountStatus | null;
  unconfirmed: boolean;
}

export interface UserQuery extends UserFilters {
  page: number;
  size: number;
}

export interface RoleAssignmentRequest {
  role: RoleType;
  organizationId: number;
  validFrom?: string;
  validTo?: string | null;
  updateOrganization?: boolean;
}

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/users`;

  list(query: UserQuery): Observable<UserPage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.role) {
      params = params.set('role', query.role);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.unconfirmed) {
      params = params.set('unconfirmed', true);
    }
    return this.http.get<UserPage>(this.base, { params });
  }

  get(id: number): Observable<UserDetail> {
    return this.http.get<UserDetail>(`${this.base}/${id}`);
  }

  assignRole(id: number, request: RoleAssignmentRequest): Observable<RoleAssignment> {
    return this.http.post<RoleAssignment>(`${this.base}/${id}/roles`, request);
  }

  revokeRole(id: number, roleId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/roles/${roleId}`);
  }

  organizations(type: OrganizationType): Observable<OrganizationSummary[]> {
    return this.http.get<OrganizationSummary[]>(`${environment.apiUrl}/organizations`, {
      params: { type },
    });
  }
}
