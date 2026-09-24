import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationSummary } from '../../auth/registration.service';
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
  { id: 20, name: 'Department of Biochemistry', nameUk: 'Кафедра біохімії', code: 'DBBT', type: 'DEPARTMENT', parent: null },
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

describe('ConfirmMembershipDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmMembershipDialogComponent>;
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(async () => {
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
        { provide: MAT_DIALOG_DATA, useValue: user },
        { provide: MatDialogRef, useValue: { close } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConfirmMembershipDialogComponent);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/organizations?type=DEPARTMENT`).flush(departments);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('ac2_7_offers_the_departments_with_the_users_own_one_preselected', () => {
    expect(fixture.componentInstance.departments()).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Олена Нова отримає роль');

    fixture.nativeElement.querySelector('[data-testid="confirm-submit"]').click();

    expect(close).toHaveBeenCalledWith(64);
  });

  it('ac2_7_returns_the_corrected_department', () => {
    fixture.componentInstance.department.setValue(20);

    fixture.nativeElement.querySelector('[data-testid="confirm-submit"]').click();

    expect(close).toHaveBeenCalledWith(20);
  });
});
