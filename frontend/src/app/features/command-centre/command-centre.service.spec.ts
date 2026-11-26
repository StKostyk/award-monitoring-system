import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CommandCentreService, MEMBERS_PAGE_SIZE } from './command-centre.service';

describe('CommandCentreService', () => {
  let service: CommandCentreService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CommandCentreService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac5_1_reads_the_decisions_of_the_day', () => {
    let decided = -1;
    service.reviewDay().subscribe((day) => (decided = day.decided));

    http
      .expectOne((r) => r.method === 'GET' && r.url.endsWith('/reviews/today'))
      .flush({ decided: 4, since: '2026-11-17T22:00:00Z' });

    expect(decided).toBe(4);
  });

  it('ac5_3_lists_the_active_members_of_the_department', () => {
    let names: string[] = [];
    service.members(41).subscribe((people) => (names = people.map((person) => person.lastName)));

    const request = http.expectOne((r) => r.url.endsWith('/users'));
    expect(request.request.params.get('organization')).toBe('41');
    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.get('size')).toBe(String(MEMBERS_PAGE_SIZE));
    request.flush({ content: [{ id: 7, firstName: 'Ivan', lastName: 'Bondar' }] });

    expect(names).toEqual(['Bondar']);
  });

  it('ac5_3_posts_the_reminder_with_the_recipient', () => {
    service.remind(41, 7).subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/analytics/units/41/reminders'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ recipientId: 7 });
    request.flush({}, { status: 202, statusText: 'Accepted' });
  });
});
