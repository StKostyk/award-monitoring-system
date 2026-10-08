import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { LanguageService } from '../../core/i18n/language.service';
import { NO_REVIEW_FILTERS, ReviewsService } from './reviews.service';

describe('ReviewsService', () => {
  let service: ReviewsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LanguageService, useValue: { current: () => 'en' } },
      ],
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

  it('ac2_1_posts_the_decision_with_its_version_and_comment', () => {
    service
      .decide(5, { decision: 'RETURN', requestVersion: 3, comment: 'Додайте наказ' })
      .subscribe((outcome) => expect(outcome.status).toBe('DRAFT'));

    const request = http.expectOne(
      (r) => r.method === 'POST' && r.url.endsWith('/awards/5/decisions'),
    );
    expect(request.request.body).toEqual({
      decision: 'RETURN',
      requestVersion: 3,
      comment: 'Додайте наказ',
    });
    request.flush({
      awardId: 5,
      status: 'DRAFT',
      requestStatus: 'RETURNED',
      level: 'FACULTY_SECRETARY',
      requestVersion: 4,
    });
  });

  it('ac4_1_posts_one_decision_with_every_item', () => {
    const body = {
      decision: 'APPROVE' as const,
      items: [
        { awardId: 5, requestVersion: 3 },
        { awardId: 6, requestVersion: 1 },
      ],
    };
    service.decideBatch(body).subscribe((results) => expect(results.length).toBe(2));

    const request = http.expectOne(
      (r) => r.method === 'POST' && r.url.endsWith('/reviews/decisions'),
    );
    expect(request.request.body).toEqual(body);
    request.flush([
      { awardId: 5, outcome: 'DONE' },
      { awardId: 6, outcome: 'FAILED', code: 'request-claimed' },
    ]);
  });

  it('ac4_5_asks_for_the_templates_in_the_interface_language', () => {
    service.templates('RETURN').subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/reviews/templates'));
    expect(request.request.params.get('decision')).toBe('RETURN');
    expect(request.request.headers.get('Accept-Language')).toBe('en');
    request.flush([]);
  });

  it('ac1_1_reads_the_review_period_of_a_faculty', () => {
    service.reviewPeriod(9).subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/organizations/9/review-period'));
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('ac1_2_ac1_3_puts_the_period_or_null_for_the_default', () => {
    service.setReviewPeriod(9, 5).subscribe();
    service.setReviewPeriod(9, null).subscribe();

    const requests = http.match((r) => r.url.endsWith('/organizations/9/review-period'));
    expect(requests.map((r) => r.request.method)).toEqual(['PUT', 'PUT']);
    expect(requests.map((r) => r.request.body)).toEqual([
      { workingDays: 5 },
      { workingDays: null },
    ]);
    requests.forEach((r) => r.flush({}));
  });
});
