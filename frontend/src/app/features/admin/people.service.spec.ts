import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { PeopleService, placeholderAddress } from './people.service';

describe('PeopleService', () => {
  const base = `${environment.apiUrl}/people`;
  let service: PeopleService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PeopleService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac1_1_posts_the_form_and_ac1_6_puts_it_with_the_version', () => {
    const form = { firstName: 'Петро', lastName: 'Іваненко', organizationId: 64, email: null };
    service.add(form).subscribe();
    const added = http.expectOne(base);
    expect(added.request.method).toBe('POST');
    expect(added.request.body).toEqual(form);
    added.flush({});

    service.change(41, { ...form, version: 2 }).subscribe();
    const changed = http.expectOne(`${base}/41`);
    expect(changed.request.method).toBe('PUT');
    expect(changed.request.body.version).toBe(2);
    changed.flush({});
  });

  it('ac1_5_ac1_7_invites_and_removes_one_person', () => {
    service.invite(41).subscribe();
    const invited = http.expectOne(`${base}/41/invitation`);
    expect(invited.request.method).toBe('POST');
    invited.flush(null, { status: 202, statusText: 'Accepted' });

    service.remove(41).subscribe();
    const removed = http.expectOne(`${base}/41`);
    expect(removed.request.method).toBe('DELETE');
    removed.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('ac1_3_offers_departments_only', () => {
    let names: string[] = [];
    service.departments().subscribe((units) => (names = units.map((unit) => unit.name)));
    http.expectOne(`${environment.apiUrl}/awards/recipient-units`).flush([
      { id: 9, name: 'Faculty', nameUk: null, type: 'FACULTY' },
      { id: 64, name: 'Algebra', nameUk: null, type: 'DEPARTMENT' },
    ]);

    expect(names).toEqual(['Algebra']);
  });

  it('d10_recognises_the_placeholder_of_an_unknown_address', () => {
    expect(placeholderAddress('unclaimed-1f2e@unclaimed.invalid')).toBe(true);
    expect(placeholderAddress(null)).toBe(true);
    expect(placeholderAddress('p.ivanenko@chnu.edu.ua')).toBe(false);
  });
});
