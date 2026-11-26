import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ComplianceService } from './compliance.service';

const base = `${environment.apiUrl}/compliance`;
const empty = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };

describe('ComplianceService', () => {
  let service: ComplianceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ComplianceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac2_5_reads_the_policies_and_a_page_of_runs', () => {
    service.policies().subscribe();
    http.expectOne(`${base}/retention-policies`).flush([]);

    service.runs({ page: 2, size: 50 }).subscribe();
    const runs = http.expectOne((request) => request.url === `${base}/retention-runs`);

    expect(runs.request.params.toString()).toBe('page=2&size=50');
    runs.flush(empty);
  });

  it('ac2_5_sends_the_erasure_status_only_when_one_is_chosen', () => {
    service.erasureRequests({ page: 0, size: 20 }, null).subscribe();
    const all = http.expectOne((request) => request.url === `${base}/erasure-requests`);

    expect(all.request.params.toString()).toBe('page=0&size=20');
    all.flush(empty);

    service.erasureRequests({ page: 0, size: 20 }, 'PENDING').subscribe();
    const pending = http.expectOne((request) => request.url === `${base}/erasure-requests`);

    expect(pending.request.params.get('status')).toBe('PENDING');
    pending.flush(empty);
  });

  it('ac2_5_asks_for_active_holds_only_when_the_filter_is_active', () => {
    service.legalHolds({ page: 0, size: 20 }, true).subscribe();
    const active = http.expectOne((request) => request.url === `${base}/legal-holds`);

    expect(active.request.params.get('active')).toBe('true');
    active.flush(empty);

    service.legalHolds({ page: 0, size: 20 }, false).subscribe();
    const all = http.expectOne((request) => request.url === `${base}/legal-holds`);

    expect(all.request.params.has('active')).toBe(false);
    all.flush(empty);
  });
});
