import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  AwardsService,
  CategoryNode,
  NO_FILTERS,
  awardTitle,
  duplicateMatches,
  flattenCategories,
  isRecent,
} from './awards.service';

const form = {
  title: null,
  titleUk: 'Грамота',
  description: null,
  descriptionUk: null,
  categoryId: 13,
  awardingOrganization: 'МОН',
  awardDate: '2025-05-01',
  externalUrl: null,
  recipientOrganizationId: null,
};

describe('AwardsService', () => {
  let service: AwardsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AwardsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac1_7_sends_only_the_filters_that_are_set', () => {
    service.list({ ...NO_FILTERS, status: 'DRAFT', dateFrom: '2025-01-01' }, 0, 100).subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/awards'));
    expect(request.request.params.keys().sort()).toEqual(['dateFrom', 'page', 'size', 'status']);
    expect(request.request.params.get('status')).toBe('DRAFT');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 });
  });

  it('ac1_3_sends_the_version_with_an_update_and_a_submission', () => {
    service.update(5, form, 3).subscribe();
    service.submit(5, 4).subscribe();

    expect(
      http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/awards/5')).request.body,
    ).toEqual({
      ...form,
      version: 3,
    });
    expect(http.expectOne((r) => r.url.endsWith('/awards/5/submit')).request.body).toEqual({
      version: 4,
      acknowledgeDuplicate: false,
    });
  });

  it('ac2_5_sends_the_acknowledgement_of_a_possible_duplicate', () => {
    service.submit(5, 4, true).subscribe();

    expect(http.expectOne((r) => r.url.endsWith('/awards/5/submit')).request.body).toEqual({
      version: 4,
      acknowledgeDuplicate: true,
    });
  });

  it('ac1_1_creates_gets_and_deletes', () => {
    service.create(form).subscribe();
    service.get(5).subscribe();
    service.remove(5).subscribe();

    expect(
      http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/awards')).request.body,
    ).toEqual(form);
    http.expectOne((r) => r.method === 'GET' && r.url.endsWith('/awards/5'));
    http.expectOne((r) => r.method === 'DELETE' && r.url.endsWith('/awards/5'));
  });

  it('ac3_1_asks_for_suggestions_with_the_title_and_the_organisation', () => {
    service.suggestions('Best paper', 'IEEE').subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/award-categories/suggestions'));
    expect(request.request.params.get('title')).toBe('Best paper');
    expect(request.request.params.get('organization')).toBe('IEEE');
  });

  it('ac1_9_fetches_the_catalogue_once_and_again_after_a_failure', () => {
    const received: CategoryNode[][] = [];
    service.categories().subscribe({ error: () => undefined });
    http
      .expectOne((r) => r.url.endsWith('/award-categories'))
      .flush(null, { status: 500, statusText: 'x' });

    service.categories().subscribe((tree) => received.push(tree));
    service.categories().subscribe((tree) => received.push(tree));
    http.expectOne((r) => r.url.endsWith('/award-categories')).flush([]);

    expect(received).toEqual([[], []]);
  });

  it('ac2_1_pages_the_versions_and_the_audit_trail', () => {
    service.versions(5, 1).subscribe();
    service.auditTrail(5, 2, 50).subscribe();

    const versions = http.expectOne((r) => r.url.endsWith('/awards/5/versions'));
    expect(versions.request.params.get('page')).toBe('1');
    expect(versions.request.params.get('size')).toBe('20');
    versions.flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 1 });
    const trail = http.expectOne((r) => r.url.endsWith('/awards/5/audit-trail'));
    expect(trail.request.params.get('page')).toBe('2');
    expect(trail.request.params.get('size')).toBe('50');
    trail.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 2 });
  });

  it('ac2_6_downloads_the_export_as_a_blob_with_its_headers', () => {
    let disposition: string | null = null;
    service
      .exportAuditTrail(5)
      .subscribe((response) => (disposition = response.headers.get('Content-Disposition')));

    const request = http.expectOne((r) => r.url.endsWith('/awards/5/audit-trail/export'));
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['csv']), {
      headers: { 'Content-Disposition': 'attachment; filename="a.csv"' },
    });

    expect(disposition).toBe('attachment; filename="a.csv"');
  });

  it('ac2_1_merges_the_organisation_names_of_every_award_type_once', () => {
    const received: string[][] = [];
    service
      .organizations()
      .subscribe((names) => received.push([...names.values()].map((name) => name.name)));
    service
      .organizations()
      .subscribe((names) => received.push([...names.values()].map((name) => name.name)));
    const requests = http.match((r) => r.url.endsWith('/organizations'));
    expect(requests.map((request) => request.request.params.get('type'))).toEqual([
      'DEPARTMENT',
      'FACULTY',
      'COLLEGE',
    ]);
    requests[0].flush([{ id: 64, name: 'Algebra', nameUk: null }]);
    requests[1].flush([{ id: 9, name: 'Mathematics', nameUk: null }]);
    requests[2].flush([]);

    expect(received).toEqual([
      ['Algebra', 'Mathematics'],
      ['Algebra', 'Mathematics'],
    ]);
  });
  it('ac0_1_reads_the_units_the_caller_may_enter_awards_for', () => {
    let units: unknown;
    service.recipientUnits().subscribe((found) => (units = found));

    http
      .expectOne((r) => r.url.endsWith('/awards/recipient-units'))
      .flush([{ id: 9, type: 'FACULTY' }]);

    expect(units).toEqual([{ id: 9, type: 'FACULTY' }]);
  });
});

describe('award helpers', () => {
  const national: CategoryNode = {
    id: 10,
    name: 'National Awards',
    nameUk: 'Національні нагороди',
    level: 'NATIONAL',
    description: null,
    children: [
      {
        id: 13,
        name: 'Ministry Recognition',
        nameUk: null,
        level: 'NATIONAL',
        description: null,
        children: [],
      },
    ],
  };

  it('ac1_9_picks_the_title_of_the_interface_language_and_falls_back', () => {
    expect(awardTitle({ title: 'Letter', titleUk: 'Подяка' }, 'uk')).toBe('Подяка');
    expect(awardTitle({ title: 'Letter', titleUk: 'Подяка' }, 'en')).toBe('Letter');
    expect(awardTitle({ title: null, titleUk: 'Подяка' }, 'en')).toBe('Подяка');
    expect(awardTitle({ title: null, titleUk: null }, 'uk')).toBe('');
  });

  it('ac1_9_lists_the_tree_parents_first_with_their_depth', () => {
    expect(flattenCategories([national]).map((item) => [item.category.id, item.depth])).toEqual([
      [10, 0],
      [13, 1],
    ]);
  });

  it('ac2_3_the_last_thirty_days_up_to_today_are_recent', () => {
    expect(isRecent('2026-09-28', '2026-09-28')).toBe(true);
    expect(isRecent('2026-08-30', '2026-09-28')).toBe(true);
    expect(isRecent('2026-08-29', '2026-09-28')).toBe(false);
    expect(isRecent('2026-09-29', '2026-09-28')).toBe(false);
    expect(isRecent('', '2026-09-28')).toBe(false);
  });

  it('ac2_5_reads_the_matches_of_a_possible_duplicate', () => {
    const match = {
      id: 9,
      title: null,
      titleUk: 'Грамота',
      awardDate: '2025-05-01',
      status: 'PENDING',
    };
    const problem = new HttpErrorResponse({ status: 409, error: { matches: [match] } });

    expect(duplicateMatches(problem)).toEqual([match]);
    expect(duplicateMatches(new HttpErrorResponse({ status: 409 }))).toEqual([]);
  });
});
