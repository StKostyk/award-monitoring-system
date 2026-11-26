import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { OrganizationRef, OrganizationType } from '../auth/user-profile';

/** An active organisation as listed by `GET /organizations`. */
export interface OrganizationSummary {
  id: number;
  name: string;
  nameUk: string | null;
  code: string | null;
  type: OrganizationType;
  parent: OrganizationRef | null;
}

/**
 * Active organisations by type, fetched once per type while the app is open. The list is public, so it is fetched
 * without the session and works on the pages open without sign-in.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationsService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly lists = new Map<OrganizationType, Observable<OrganizationSummary[]>>();

  /** The active organisations of one type; a failed fetch is tried again next time. */
  ofType(type: OrganizationType): Observable<OrganizationSummary[]> {
    let list = this.lists.get(type);
    if (!list) {
      list = this.http
        .get<OrganizationSummary[]>(`${environment.apiUrl}/organizations`, { params: { type } })
        .pipe(
          catchError((error: unknown) => {
            this.lists.delete(type);
            return throwError(() => error);
          }),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
      this.lists.set(type, list);
    }
    return list;
  }
}
