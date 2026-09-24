import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { UsersService } from './users.service';

describe('UsersService', () => {
  let service: UsersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UsersService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac2_10_sends_only_the_filters_that_are_set', () => {
    service
      .list({ q: '', role: null, status: null, unconfirmed: false, page: 1, size: 50 })
      .subscribe();
    const bare = http.expectOne((request) => request.url === `${environment.apiUrl}/users`);

    expect(bare.request.params.toString()).toBe('page=1&size=50');
    bare.flush({ content: [] });

    service
      .list({ q: 'нова', role: 'DEAN', status: 'ACTIVE', unconfirmed: true, page: 0, size: 20 })
      .subscribe();
    const filtered = http.expectOne((request) => request.url === `${environment.apiUrl}/users`);

    expect(filtered.request.params.get('q')).toBe('нова');
    expect(filtered.request.params.get('role')).toBe('DEAN');
    expect(filtered.request.params.get('status')).toBe('ACTIVE');
    expect(filtered.request.params.get('unconfirmed')).toBe('true');
    filtered.flush({ content: [] });
  });

  it('ac2_10_reads_one_user_and_assigns_and_revokes_roles', () => {
    service.get(7).subscribe();
    http.expectOne(`${environment.apiUrl}/users/7`).flush({});

    service.assignRole(7, { role: 'EMPLOYEE', organizationId: 64, updateOrganization: true }).subscribe();
    const assign = http.expectOne(`${environment.apiUrl}/users/7/roles`);

    expect(assign.request.method).toBe('POST');
    expect(assign.request.body).toEqual({
      role: 'EMPLOYEE',
      organizationId: 64,
      updateOrganization: true,
    });
    assign.flush({});

    service.revokeRole(7, 3).subscribe();
    const revoke = http.expectOne(`${environment.apiUrl}/users/7/roles/3`);

    expect(revoke.request.method).toBe('DELETE');
    revoke.flush(null);

    service.organizations('FACULTY').subscribe();
    http.expectOne(`${environment.apiUrl}/organizations?type=FACULTY`).flush([]);
  });
});
