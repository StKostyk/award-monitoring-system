import { HttpInterceptorFn, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import {
  OrganizationsService,
  OrganizationSummary,
} from '../../core/organizations/organizations.service';
import {
  AchievementsService,
  NO_ACHIEVEMENT_FILTERS,
  UnitOption,
  readQuery,
  writeQuery,
} from './achievements.service';

function unit(id: number, nameUk: string, parent: number | null): OrganizationSummary {
  return {
    id,
    name: nameUk,
    nameUk,
    code: null,
    type: parent === null ? 'FACULTY' : 'DEPARTMENT',
    parent:
      parent === null ? null : { id: parent, name: 'F', nameUk: null, code: null, type: 'FACULTY' },
  };
}

const session: HttpInterceptorFn = (request, next) =>
  next(request.clone({ setHeaders: { Authorization: 'Bearer session' } }));

describe('AchievementsService', () => {
  let service: AchievementsService;
  let http: HttpTestingController;
  const organizations = { ofType: vi.fn() };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([session])),
        provideHttpClientTesting(),
        { provide: OrganizationsService, useValue: organizations },
      ],
    });
    service = TestBed.inject(AchievementsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac1_6_sends_only_the_filters_that_are_set', () => {
    service
      .list({
        filters: { ...NO_ACHIEVEMENT_FILTERS, unit: 9, recipient: 'UNIT' },
        page: 1,
        size: 50,
      })
      .subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/achievements'));
    expect(request.request.params.keys().sort()).toEqual(['page', 'recipient', 'size', 'unit']);
    expect(request.request.params.get('unit')).toBe('9');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 1 });
  });

  it('ac1_5_the_colleague_list_is_sent_with_the_session', () => {
    service.list({ filters: NO_ACHIEVEMENT_FILTERS, page: 0, size: 20 }).subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/api/v1/achievements'));
    expect(request.request.headers.get('Authorization')).toBe('Bearer session');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
  });

  it('ac2_8_the_public_list_is_sent_without_the_session', () => {
    service
      .list({ filters: { ...NO_ACHIEVEMENT_FILTERS, unit: 64 }, page: 0, size: 20 }, 'public')
      .subscribe();

    const request = http.expectOne((r) => r.url.endsWith('/public/achievements'));
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.params.get('unit')).toBe('64');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
  });

  it('ac1_6_lists_each_faculty_followed_by_its_departments_by_name', () => {
    organizations.ofType.mockImplementation((type: string) =>
      of(
        type === 'FACULTY'
          ? [unit(9, 'Факультет математики', null), unit(5, 'Біологічний факультет', null)]
          : [
              unit(65, 'Кафедра геометрії', 9),
              unit(64, 'Кафедра алгебри', 9),
              unit(40, 'Кафедра ботаніки', 5),
            ],
      ),
    );
    let units: UnitOption[] = [];

    service.units('uk').subscribe((list) => (units = list));

    expect(units.map((option) => [option.unit.id, option.depth])).toEqual([
      [5, 0],
      [40, 1],
      [9, 0],
      [64, 1],
      [65, 1],
    ]);
  });

  it('ac1_8_reads_the_filters_from_the_address_bar', () => {
    const query = readQuery(
      convertToParamMap({
        unit: '9',
        year: '2025',
        level: 'NATIONAL',
        recipient: 'PERSON',
        page: '2',
        size: '50',
      }),
    );

    expect(query).toEqual({
      filters: { unit: 9, year: 2025, level: 'NATIONAL', recipient: 'PERSON' },
      page: 2,
      size: 50,
    });
  });

  it('ac1_8_leaves_out_values_the_api_would_refuse', () => {
    const query = readQuery(
      convertToParamMap({
        unit: 'abc',
        year: '-1',
        level: 'GALACTIC',
        recipient: 'NOBODY',
        size: '7',
      }),
    );

    expect(query).toEqual({ filters: NO_ACHIEVEMENT_FILTERS, page: 0, size: 20 });
  });

  it('ac1_8_writes_only_the_filters_that_are_set_and_a_later_page', () => {
    expect(writeQuery({ filters: NO_ACHIEVEMENT_FILTERS, page: 0, size: 20 })).toEqual({});
    expect(
      writeQuery({ filters: { ...NO_ACHIEVEMENT_FILTERS, year: 2025 }, page: 3, size: 100 }),
    ).toEqual({ year: 2025, page: 3, size: 100 });
  });
});
