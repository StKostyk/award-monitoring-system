import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../core/i18n/language.service';
import { Award, AwardsService } from '../awards/awards.service';
import { MySubmissionsComponent } from './my-submissions.component';

function pending(id: number, overdue: boolean): Award {
  return {
    id,
    title: null,
    titleUk: `Нагорода ${id}`,
    description: null,
    descriptionUk: null,
    category: null,
    awardingOrganization: 'МОН',
    awardDate: '2025-05-01',
    externalUrl: null,
    status: 'PENDING',
    impactScore: 80,
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    recipient: { type: 'PERSON', organization: null },
    organization: { id: 64, name: 'Algebra', nameUk: null, code: 'DAI', type: 'DEPARTMENT' },
    visibility: 'PRIVATE',
    request: {
      status: 'SUBMITTED',
      currentLevel: 'DEAN',
      submittedAt: '2026-09-28T09:00:00Z',
      deadline: '2026-10-01T09:00:00Z',
      estimatedCompletion: '2026-10-07',
      overdue,
    },
    warnings: [],
    createdAt: '2026-09-28T08:00:00Z',
    updatedAt: '2026-09-28T09:00:00Z',
    version: 2,
  };
}

describe('MySubmissionsComponent', () => {
  const service = { list: vi.fn() };

  async function create() {
    await TestBed.configureTestingModule({
      imports: [
        MySubmissionsComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              home: { submissions: { title: 'Мої подання', empty: 'Немає нагород на розгляді' } },
              awards: {
                statusPanel: { returned: 'Очікує ваших виправлень' },
                timing: { expected: 'Очікується до {{date}}', delayed: 'Затримка' },
              },
              roles: { DEAN: 'Декан' },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AwardsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(MySubmissionsComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => service.list.mockReset());

  it('ac1_17_lists_pending_awards_with_level_expected_date_and_delay', async () => {
    service.list.mockReturnValue(
      of({ content: [pending(7, true), pending(6, false)], totalElements: 2, totalPages: 1 }),
    );
    const element = await create();
    const items = element.querySelectorAll('[data-testid="my-submission"]');

    expect(service.list).toHaveBeenCalledWith(expect.objectContaining({ status: 'PENDING' }), 0, 5);
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Нагорода 7');
    expect(
      items[0].querySelector('[data-testid="request-timing"]')?.textContent?.replace(/\s+/g, ' '),
    ).toContain('Декан · Очікується до 07.10.2026');
    expect(items[0].querySelector('[data-testid="request-delayed"]')).not.toBeNull();
    expect(items[1].querySelector('[data-testid="request-delayed"]')).toBeNull();
    expect(items[0].querySelector('a')?.getAttribute('href')).toBe('/awards/7');
  });

  it('ac1_17_a_returned_request_waits_for_the_owner_corrections', async () => {
    const award = pending(8, false);
    const returned = {
      ...award,
      request: { ...award.request!, status: 'RETURNED' as const, estimatedCompletion: null },
    };
    service.list.mockReturnValue(of({ content: [returned], totalElements: 1, totalPages: 1 }));
    const element = await create();

    expect(element.querySelector('[data-testid="request-timing"]')?.textContent?.trim()).toBe(
      'Очікує ваших виправлень',
    );
  });

  it('ac1_17_without_pending_awards_offers_a_new_one', async () => {
    service.list.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0 }));
    const element = await create();

    expect(element.querySelector('[data-testid="my-submissions-empty"]')?.textContent).toContain(
      'Немає нагород на розгляді',
    );
    expect(
      element.querySelector('[data-testid="my-submissions-create"]')?.getAttribute('href'),
    ).toBe('/awards/new');
  });

  it('shows_a_problem_when_the_list_fails', async () => {
    service.list.mockReturnValue(throwError(() => new Error('down')));
    const element = await create();

    expect(element.querySelector('[data-testid="my-submissions-error"]')).not.toBeNull();
  });
});
