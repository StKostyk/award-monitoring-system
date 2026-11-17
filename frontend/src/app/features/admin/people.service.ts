import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AccountStatus, OrganizationRef } from '../../core/auth/user-profile';
import { Page } from '../../shared/page';
import { Award, UnitRef } from '../awards/awards.service';

/** A colleague added without an account, or one who has claimed it since. */
export interface Person {
  id: number;
  firstName: string;
  lastName: string;
  /** Null when unknown; masked as `p***@domain` in lists and duplicate matches. */
  email: string | null;
  organization: OrganizationRef;
  status: AccountStatus;
  addedBy: { id: number; name: string; email: string };
  invitedAt: string | null;
  awardCount: number;
  version: number;
}

export interface PersonDetail extends Person {
  awards: Award[];
}

export interface PersonLinkResult {
  userId: number;
  movedAwards: number;
}

/** The fields of an added or changed person; a null address means it is unknown. */
export interface PersonForm {
  firstName: string;
  lastName: string;
  organizationId: number;
  email: string | null;
  duplicateAcknowledged?: boolean;
  version?: number;
}

/** People offered by one search. */
const SEARCH_SIZE = 10;

/** The domain of the placeholder address a person without a known address carries. */
const PLACEHOLDER_DOMAIN = '@unclaimed.invalid';

/** Whether the address is the placeholder of a person whose address is unknown. */
export function placeholderAddress(email: string | null): boolean {
  return !email || email.endsWith(PLACEHOLDER_DOMAIN);
}

@Injectable({ providedIn: 'root' })
export class PeopleService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/people`;

  add(form: PersonForm): Observable<Person> {
    return this.http.post<Person>(this.base, form);
  }

  /**
   * People of the caller's scope whose name or address contains the text, sorted by last name.
   *
   * @param q the text, ignored by the server under two characters
   * @param size the page size
   */
  list(q: string, size = SEARCH_SIZE, organization?: number): Observable<Page<Person>> {
    let params = new HttpParams().set('q', q).set('size', size);
    if (organization) {
      params = params.set('organization', organization);
    }
    return this.http.get<Page<Person>>(this.base, { params });
  }

  /** Joins the person to a registered account: the awards move to it and the person row is scrubbed. */
  link(id: number, userId: number, version: number): Observable<PersonLinkResult> {
    return this.http.post<PersonLinkResult>(`${this.base}/${id}/link`, { userId, version });
  }

  get(id: number): Observable<PersonDetail> {
    return this.http.get<PersonDetail>(`${this.base}/${id}`);
  }

  change(id: number, form: PersonForm): Observable<Person> {
    return this.http.put<Person>(`${this.base}/${id}`, form);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  invite(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/invitation`, null);
  }

  /** Departments of the caller's scope; faculties are left out, a person belongs to a department. */
  departments(): Observable<UnitRef[]> {
    return this.http
      .get<UnitRef[]>(`${environment.apiUrl}/awards/recipient-units`)
      .pipe(map((units) => units.filter((unit) => unit.type === 'DEPARTMENT')));
  }
}
