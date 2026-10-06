import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NO_REVIEW_FILTERS, ReviewsService } from './reviews.service';

describe('ReviewsService', () => {
  let service: ReviewsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReviewsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac1_2_sends_only_the_filters_that_are_set', () => {
    service
      .list({ ...NO_REVIEW_FILTERS, assigned: 'unassigned', organizationId: 9 }, 1, 50)
      .subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/reviews'));
    expect(request.request.params.keys().sort()).toEqual(
      ['assigned', 'organizationId', 'page', 'size'].sort(),
    );
    expect(request.request.params.get('assigned')).toBe('unassigned');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 1 });
  });

  it('ac1_4_ac1_6_ac1_8_claims_takes_over_and_hands_over_with_the_version', () => {
    service.claim(5, 3).subscribe();
    service.claim(5, 3, true).subscribe();
    service.handOver(5, 3, 6).subscribe();

    const puts = http.match((r) => r.method === 'PUT' && r.url.endsWith('/awards/5/reviewer'));
    expect(puts.map((put) => put.request.body)).toEqual([
      { requestVersion: 3, takeOver: false },
      { requestVersion: 3, takeOver: true },
      { requestVersion: 3, reviewerId: 6 },
    ]);
    puts.forEach((put) => put.flush({}));
  });

  it('ac1_7_ac1_8_ac1_11_releases_reads_the_item_and_the_candidates', () => {
    service.release(5, 4).subscribe();
    service.item(5).subscribe();
    service.candidates(5).subscribe();

    const release = http.expectOne((r) => r.method === 'DELETE');
    expect(release.request.params.get('requestVersion')).toBe('4');
    release.flush(null);
    http.expectOne((r) => r.method === 'GET' && r.url.endsWith('/awards/5/reviewer')).flush({});
    http.expectOne((r) => r.url.endsWith('/awards/5/reviewers')).flush([]);
  });
});
