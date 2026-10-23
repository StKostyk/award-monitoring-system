import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { FACULTY, facultyAnalytics } from './unit-analytics.testing';
import { UnitAnalyticsService } from './unit-analytics.service';

describe('UnitAnalyticsService', () => {
  let service: UnitAnalyticsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UnitAnalyticsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac2_1_lists_the_units_open_to_the_caller', () => {
    let units: unknown;
    service.units().subscribe((body) => (units = body));

    http.expectOne(`${environment.apiUrl}/analytics/units`).flush([FACULTY]);

    expect(units).toEqual([FACULTY]);
  });

  it('ac2_2_reads_a_unit_for_the_chosen_years_only', () => {
    service.read(9, { from: 2024, to: null }).subscribe();
    const request = http.expectOne((req) => req.url === `${environment.apiUrl}/analytics/units/9`);

    expect(request.request.params.get('from')).toBe('2024');
    expect(request.request.params.has('to')).toBe(false);
    request.flush(facultyAnalytics());
  });

  it('ac2_2_reads_a_unit_without_years_for_the_server_default', () => {
    service.read(64, { from: null, to: null }).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/analytics/units/64`);

    expect(request.request.params.keys()).toEqual([]);
    request.flush(facultyAnalytics());
  });
});
