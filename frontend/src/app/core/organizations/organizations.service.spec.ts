import { HttpInterceptorFn, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { OrganizationSummary, OrganizationsService } from './organizations.service';

const algebra: OrganizationSummary = {
  id: 64,
  name: 'Algebra',
  nameUk: null,
  code: null,
  type: 'DEPARTMENT',
  parent: null,
};

const session: HttpInterceptorFn = (request, next) =>
  next(request.clone({ setHeaders: { Authorization: 'Bearer session' } }));

describe('OrganizationsService', () => {
  let service: OrganizationsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([session])), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrganizationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches_each_type_once_and_again_after_a_failure', () => {
    const url = `${environment.apiUrl}/organizations?type=DEPARTMENT`;
    const received: number[][] = [];
    service.ofType('DEPARTMENT').subscribe({ error: () => undefined });
    http.expectOne(url).flush(null, { status: 500, statusText: 'x' });

    service.ofType('DEPARTMENT').subscribe((list) => received.push(list.map(({ id }) => id)));
    service.ofType('DEPARTMENT').subscribe((list) => received.push(list.map(({ id }) => id)));
    http.expectOne(url).flush([algebra]);

    expect(received).toEqual([[64], [64]]);
  });

  it('keeps_the_types_apart', () => {
    service.ofType('DEPARTMENT').subscribe();
    service.ofType('FACULTY').subscribe();

    http.expectOne(`${environment.apiUrl}/organizations?type=DEPARTMENT`).flush([]);
    http.expectOne(`${environment.apiUrl}/organizations?type=FACULTY`).flush([]);
  });

  it('ac2_8_fetches_the_public_list_without_the_session', () => {
    service.ofType('FACULTY').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/organizations?type=FACULTY`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush([]);
  });
});
