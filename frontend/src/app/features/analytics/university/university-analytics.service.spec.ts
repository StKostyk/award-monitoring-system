import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { UniversityAnalyticsService } from './university-analytics.service';
import { complianceSummary, universityAnalytics } from './university-analytics.testing';

describe('UniversityAnalyticsService', () => {
  let service: UniversityAnalyticsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UniversityAnalyticsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac2_2_reads_the_university_for_the_period', async () => {
    const result = firstValueFrom(service.read({ from: 2022, to: 2026 }));
    const request = http.expectOne(`${environment.apiUrl}/analytics/university?from=2022&to=2026`);
    request.flush(universityAnalytics());

    expect(request.request.method).toBe('GET');
    expect((await result).projection.status).toBe('PROJECTED');
  });

  it('ac2_2_leaves_out_an_open_period', async () => {
    const result = firstValueFrom(service.read({ from: null, to: null }));
    http.expectOne(`${environment.apiUrl}/analytics/university`).flush(universityAnalytics());

    expect((await result).units).toHaveLength(3);
  });

  it('ac2_7_reads_the_compliance_summary', async () => {
    const result = firstValueFrom(service.compliance());
    http
      .expectOne(`${environment.apiUrl}/analytics/university/compliance`)
      .flush(complianceSummary());

    expect((await result).status).toBe('ATTENTION');
  });
});
