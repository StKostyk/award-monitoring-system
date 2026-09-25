import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { DelegationsService } from './delegations.service';

describe('DelegationsService', () => {
  let service: DelegationsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DelegationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac3_5_reads_the_two_lists_with_the_filters_that_are_set', () => {
    service.list().subscribe();
    const bare = http.expectOne((request) => request.url === `${environment.apiUrl}/delegations`);

    expect(bare.request.params.toString()).toBe('');
    bare.flush({ given: [], received: [] });

    service.list('active', 42).subscribe();
    const filtered = http.expectOne((request) => request.url === `${environment.apiUrl}/delegations`);

    expect(filtered.request.params.get('state')).toBe('active');
    expect(filtered.request.params.get('delegatorId')).toBe('42');
    filtered.flush({ given: [], received: [] });
  });

  it('ac3_1_ac3_4_creates_and_revokes_a_delegation', () => {
    service
      .create({
        delegateId: 5,
        role: 'DEAN',
        organizationId: 9,
        validFrom: '2026-09-24',
        validTo: '2026-10-08',
        reason: 'Відпустка',
      })
      .subscribe();
    const create = http.expectOne(`${environment.apiUrl}/delegations`);

    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({
      delegateId: 5,
      role: 'DEAN',
      organizationId: 9,
      validFrom: '2026-09-24',
      validTo: '2026-10-08',
      reason: 'Відпустка',
    });
    create.flush({});

    service.revoke(3).subscribe();
    const revoke = http.expectOne(`${environment.apiUrl}/delegations/3`);

    expect(revoke.request.method).toBe('DELETE');
    revoke.flush(null);
  });
});
