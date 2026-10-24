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

  it('ac3_6_exports_the_report_of_the_period_as_a_file', () => {
    let name: string | null = null;
    service
      .exportReport(9, { from: 2021, to: 2025 }, 'xlsx', 'uk')
      .subscribe((response) => (name = response.headers.get('Content-Disposition')));
    const request = http.expectOne(
      (req) => req.url === `${environment.apiUrl}/analytics/units/9/export`,
    );

    expect(request.request.responseType).toBe('blob');
    expect(request.request.params.get('format')).toBe('xlsx');
    expect(request.request.params.get('lang')).toBe('uk');
    expect(request.request.params.get('from')).toBe('2021');
    expect(request.request.params.get('to')).toBe('2025');
    request.flush(new Blob(['x']), {
      headers: { 'Content-Disposition': 'attachment; filename="analytics-FMI-2021-2025.xlsx"' },
    });
    expect(name).toContain('analytics-FMI-2021-2025.xlsx');
  });

  it('ac3_6_exports_without_years_for_the_server_default', () => {
    service.exportReport(64, { from: null, to: null }, 'csv', 'en').subscribe();
    const request = http.expectOne(
      (req) => req.url === `${environment.apiUrl}/analytics/units/64/export`,
    );

    expect(request.request.params.keys()).toEqual(['format', 'lang']);
    request.flush(new Blob());
  });
});
