import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AnalyticsService, levelBand } from './analytics.service';
import { sampleAnalytics } from './analytics.testing';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AnalyticsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac2_5_reads_the_analytics_with_only_the_years_that_are_set', () => {
    let read = 0;
    service.read({ from: null, to: null }).subscribe(() => read++);
    const bare = http.expectOne((request) => request.url === `${environment.apiUrl}/analytics/me`);

    expect(bare.request.params.keys()).toEqual([]);
    bare.flush(sampleAnalytics());

    service.read({ from: 2020, to: null }).subscribe(() => read++);
    const from = http.expectOne((request) => request.url === `${environment.apiUrl}/analytics/me`);

    expect(from.request.params.get('from')).toBe('2020');
    expect(from.request.params.has('to')).toBe(false);
    from.flush(sampleAnalytics());

    service.read({ from: 2020, to: 2024 }).subscribe(() => read++);
    const both = http.expectOne((request) => request.url === `${environment.apiUrl}/analytics/me`);

    expect(both.request.params.get('to')).toBe('2024');
    both.flush(sampleAnalytics());
    expect(read).toBe(3);
  });

  it('ac2_9_reads_and_saves_the_hidden_widgets', () => {
    const url = `${environment.apiUrl}/users/me/dashboard-preferences`;
    let hidden: string[] = [];
    service.hiddenWidgets().subscribe((value) => (hidden = value));
    http.expectOne(url).flush({ hiddenWidgets: ['RANK'] });

    expect(hidden).toEqual(['RANK']);

    service.saveHiddenWidgets(['TIMELINE', 'RANK']).subscribe((value) => (hidden = value));
    const put = http.expectOne(url);

    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ hiddenWidgets: ['TIMELINE', 'RANK'] });
    put.flush({ hiddenWidgets: ['TIMELINE', 'RANK'] });
    expect(hidden).toEqual(['TIMELINE', 'RANK']);
  });

  it('ac2_5_folds_the_unit_levels_into_one_band', () => {
    const unitLevels = ['SPECIALITY', 'DEPARTMENT', 'COLLEGE', 'FACULTY', 'LOCAL'] as const;

    expect(unitLevels.map((level) => levelBand(level))).toEqual(Array(5).fill('UNIT'));
    expect(levelBand('UNIVERSITY')).toBe('UNIVERSITY');
    expect(levelBand('INTERNATIONAL')).toBe('INTERNATIONAL');
  });
});
