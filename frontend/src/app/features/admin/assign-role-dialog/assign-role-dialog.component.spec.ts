import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { UserSummary } from '../users.service';
import { AssignRoleDialogComponent } from './assign-role-dialog.component';

const department = {
  id: 64,
  name: 'Department of Algebra',
  nameUk: 'Кафедра алгебри',
  code: 'DAI',
  type: 'DEPARTMENT' as const,
};

const user: UserSummary = {
  id: 7,
  email: 'employee.fmi@chnu.edu.ua',
  firstName: 'Анастасія',
  lastName: 'Працівник',
  organization: department,
  status: 'ACTIVE',
  roles: [],
  membershipConfirmed: true,
};

const translations = {
  uk: {
    roles: { EMPLOYEE: 'Працівник', FACULTY_SECRETARY: 'Секретар факультету' },
    admin: {
      assign: {
        title: 'Призначити роль',
        role: 'Роль',
        organization: 'Підрозділ',
        validFrom: 'Діє з',
        validTo: 'Діє до',
        submit: 'Призначити',
        cancel: 'Скасувати',
        noRoles: 'Ви не можете призначати ролі.',
        validation: { required: 'Обовʼязкове поле', past: 'Не в минулому', order: 'Хибний порядок' },
      },
      problems: {
        'role-above-level': 'Роль вища за ваш рівень',
        network: 'Сервер недоступний. Спробуйте пізніше.',
      },
    },
  },
};

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

async function build(scopes: string[]): Promise<{
  fixture: ComponentFixture<AssignRoleDialogComponent>;
  http: HttpTestingController;
  close: ReturnType<typeof vi.fn>;
}> {
  const close = vi.fn();
  await TestBed.configureTestingModule({
    imports: [
      AssignRoleDialogComponent,
      NoopAnimationsModule,
      TranslocoTestingModule.forRoot({
        langs: translations,
        translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
      }),
    ],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MAT_DIALOG_DATA, useValue: user },
      { provide: MatDialogRef, useValue: { close } },
      { provide: LanguageService, useValue: { current: () => 'uk' } },
      {
        provide: AuthService,
        useValue: { permissions: signal(readPermissions(token({ role_scopes: scopes }))) },
      },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(AssignRoleDialogComponent);
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController), close };
}

describe('AssignRoleDialogComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('ac2_10_offers_only_the_roles_the_caller_may_grant', async () => {
    const secretary = await build(['FACULTY_SECRETARY:9']);

    expect(secretary.fixture.componentInstance.form.enabled).toBe(true);
    expect(secretary.fixture.nativeElement.textContent).not.toContain('Ви не можете призначати ролі.');
    TestBed.resetTestingModule();

    const dean = await build(['DEAN:9']);
    dean.fixture.componentInstance.roleChanged('FACULTY_SECRETARY');
    const requests = dean.http.match((request) => request.url.endsWith('/organizations'));

    expect(requests.map((request) => request.request.params.get('type'))).toEqual([
      'FACULTY',
      'DEPARTMENT',
    ]);
    requests.forEach((request) => request.flush([]));
  });

  it('ac2_10_tells_a_caller_without_grant_rights_that_nothing_can_be_assigned', async () => {
    const { fixture } = await build(['EMPLOYEE:64']);

    expect(fixture.nativeElement.querySelector('[data-testid="assign-no-roles"]').textContent).toContain(
      'Ви не можете призначати ролі.',
    );
    expect(fixture.nativeElement.querySelector('[data-testid="assign-submit"]')).toBeNull();
  });

  it('ac2_10_refuses_a_validity_that_starts_in_the_past', async () => {
    const { fixture, http } = await build(['DEAN:9']);
    const form = fixture.componentInstance.form;
    form.patchValue({ role: 'EMPLOYEE', organizationId: 64, validFrom: '2020-01-01' });

    fixture.componentInstance.submit();

    expect(form.controls.validFrom.hasError('past')).toBe(true);
    http.expectNone(`${environment.apiUrl}/users/7/roles`);
  });

  it('ac2_10_keeps_the_dialog_filled_when_the_server_is_unreachable', async () => {
    const { fixture, http, close } = await build(['DEAN:9']);
    const form = fixture.componentInstance.form;
    form.patchValue({ role: 'EMPLOYEE', organizationId: 64, validTo: '2027-01-31' });

    fixture.componentInstance.submit();
    const request = http.expectOne(`${environment.apiUrl}/users/7/roles`);

    expect(request.request.body).toEqual({
      role: 'EMPLOYEE',
      organizationId: 64,
      validFrom: form.controls.validFrom.value,
      validTo: '2027-01-31',
    });
    request.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="assign-error"]').textContent).toContain(
      'Сервер недоступний',
    );
    expect(form.controls.organizationId.value).toBe(64);
    expect(close).not.toHaveBeenCalled();
  });

  it('ac2_10_renders_a_role_above_level_refusal_inline', async () => {
    const { fixture, http, close } = await build(['DEAN:9']);
    fixture.componentInstance.form.patchValue({ role: 'FACULTY_SECRETARY', organizationId: 9 });

    fixture.componentInstance.submit();
    http
      .expectOne(`${environment.apiUrl}/users/7/roles`)
      .flush(
        { type: 'urn:awards:problem:role-above-level', status: 403 },
        { status: 403, statusText: 'Forbidden' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="assign-error"]').textContent).toContain(
      'Роль вища за ваш рівень',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
