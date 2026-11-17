import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationSummary } from '../../../core/organizations/organizations.service';
import { Person } from '../people.service';
import { UserSummary } from '../users.service';
import { ConfirmMembershipDialogComponent } from './confirm-membership-dialog.component';

const department = {
  id: 64,
  name: 'Department of Algebra',
  nameUk: 'Кафедра алгебри',
  code: 'DAI',
  type: 'DEPARTMENT' as const,
};

const departments: OrganizationSummary[] = [
  { ...department, parent: null },
  {
    id: 20,
    name: 'Department of Biochemistry',
    nameUk: 'Кафедра біохімії',
    code: 'DBBT',
    type: 'DEPARTMENT',
    parent: null,
  },
];

const user: UserSummary = {
  id: 7,
  email: 'newcomer@chnu.edu.ua',
  firstName: 'Олена',
  lastName: 'Нова',
  organization: department,
  status: 'ACTIVE',
  roles: [],
  membershipConfirmed: false,
};

function person(id: number, lastName: string, status: Person['status'] = 'UNCLAIMED'): Person {
  return {
    id,
    firstName: 'Оксана',
    lastName,
    email: null,
    organization: department,
    status,
    addedBy: { id: 3, name: 'Секретар', email: 'secretary@chnu.edu.ua' },
    invitedAt: null,
    awardCount: 2,
    version: 5,
  };
}

const people = [person(41, 'Мельник'), person(42, 'Нова'), person(43, 'Нова', 'PENDING')];

describe('ConfirmMembershipDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmMembershipDialogComponent>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function setup(data: object, candidates: Person[] = []): Promise<void> {
    close.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        ConfirmMembershipDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              admin: {
                confirmDialog: {
                  title: 'Підтвердити членство',
                  text: '{{name}} отримає роль «Працівник».',
                  department: 'Кафедра',
                  submit: 'Підтвердити',
                  cancel: 'Скасувати',
                  samePerson: 'Це та сама людина',
                  awards: 'нагород: {{count}}',
                  linkTitle: 'Приєднати нагороди',
                  linkText: 'Нагороди перейдуть до {{name}}.',
                  linkSubmit: 'Приєднати',
                  noCandidates: 'Немає колег',
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConfirmMembershipDialogComponent);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/organizations?type=DEPARTMENT`).flush(departments);
    flushCandidates(64, candidates);
  }

  function flushCandidates(organization: number, candidates: Person[]): void {
    const request = http.expectOne((r) => r.url === `${environment.apiUrl}/people`);
    expect(request.request.params.get('organization')).toBe(String(organization));
    request.flush({ content: candidates, page: 0, size: 100, totalElements: candidates.length });
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('ac2_7_offers_the_departments_with_the_users_own_one_preselected', async () => {
    await setup(user);

    expect(fixture.componentInstance.departments()).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Олена Нова отримає роль');
    expect(fixture.nativeElement.querySelector('[data-testid="confirm-candidates"]')).toBeNull();

    fixture.nativeElement.querySelector('[data-testid="confirm-submit"]').click();

    expect(close).toHaveBeenCalledWith({ organizationId: 64, person: null });
  });

  it('ac2_7_returns_the_corrected_department', async () => {
    await setup(user);
    fixture.componentInstance.department.setValue(20);
    flushCandidates(20, []);

    fixture.nativeElement.querySelector('[data-testid="confirm-submit"]').click();

    expect(close).toHaveBeenCalledWith({ organizationId: 20, person: null });
  });

  it('ac4_1_lists_the_unclaimed_people_of_the_department_namesakes_first_none_chosen', async () => {
    await setup(user, people);

    const options = Array.from(
      fixture.nativeElement.querySelectorAll('mat-radio-button') as NodeListOf<HTMLElement>,
    );
    expect(fixture.nativeElement.textContent).toContain('Це та сама людина');
    expect(options.map((option) => option.getAttribute('data-testid'))).toEqual([
      'confirm-candidate-42',
      'confirm-candidate-41',
    ]);
    expect(options[0].textContent).toContain('нагород: 2');
    expect(fixture.componentInstance.person.value).toBeNull();

    fixture.componentInstance.person.setValue(people[0]);
    fixture.nativeElement.querySelector('[data-testid="confirm-submit"]').click();

    expect(close).toHaveBeenCalledWith({ organizationId: 64, person: people[0] });
  });

  it('ac4_4_offers_only_the_link_and_needs_a_chosen_colleague', async () => {
    await setup({ ...user, roles: [{ id: 1 }], linkOnly: true }, [people[0]]);
    const submit: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="confirm-submit"]',
    );

    expect(fixture.nativeElement.textContent).toContain('Приєднати нагороди');
    expect(submit.disabled).toBe(true);

    fixture.componentInstance.person.setValue(people[0]);
    fixture.detectChanges();
    submit.click();

    expect(close).toHaveBeenCalledWith({ organizationId: 64, person: people[0] });
  });

  it('ac4_1_a_caller_who_cannot_list_people_still_confirms', async () => {
    close.mockClear();
    await setup(user);
    fixture.componentInstance.department.setValue(20);
    http
      .expectOne((r) => r.url === `${environment.apiUrl}/people`)
      .flush(null, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.componentInstance.candidates()).toEqual([]);
  });
});
