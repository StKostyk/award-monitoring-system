import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { today } from '../../admin/role-organizations';
import { UserSummary } from '../../admin/users.service';
import { DelegateDialogComponent } from './delegate-dialog.component';

const faculty = {
  id: 9,
  name: 'Faculty of Mathematics',
  nameUk: 'Факультет математики',
  code: 'FMI',
  type: 'FACULTY' as const,
};

const secretary: UserSummary = {
  id: 3,
  email: 'secretary.fmi@chnu.edu.ua',
  firstName: 'Аліна',
  lastName: 'Секретар',
  organization: faculty,
  status: 'ACTIVE',
  roles: [],
  membershipConfirmed: true,
};

const translations = {
  uk: {
    roles: { DEAN: 'Декан', FACULTY_SECRETARY: 'Секретар факультету' },
    delegations: {
      create: {
        title: 'Делегувати повноваження',
        role: 'Роль',
        organization: 'Підрозділ',
        delegate: 'Кому передати',
        delegateHint: 'Щонайменше 2 символи',
        validFrom: 'Діє з',
        validTo: 'Діє до',
        periodHint: 'Не довше 90 днів',
        reason: 'Причина',
        submit: 'Делегувати',
        cancel: 'Скасувати',
        noRoles: 'Ви не маєте власної ролі, повноваження якої можна передати.',
        validation: {
          required: 'Обовʼязкове поле',
          past: 'Дата не може бути в минулому',
          order: 'Хибний порядок',
          range: 'Період не може перевищувати 90 днів',
          reason: 'Причина задовга',
        },
      },
      problems: {
        'delegation-overlap': 'Ви вже делегували цю роль на частину цього періоду.',
        'delegation-not-holder': 'Ви не маєте цієї ролі у цьому підрозділі.',
        network: 'Сервер недоступний. Спробуйте пізніше.',
      },
    },
  },
};

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

function days(from: string, count: number): string {
  const date = new Date(`${from}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + count);
  return date.toISOString().substring(0, 10);
}

async function build(claims: Record<string, unknown>): Promise<{
  fixture: ComponentFixture<DelegateDialogComponent>;
  http: HttpTestingController;
  close: ReturnType<typeof vi.fn>;
}> {
  const close = vi.fn();
  await TestBed.configureTestingModule({
    imports: [
      DelegateDialogComponent,
      NoopAnimationsModule,
      TranslocoTestingModule.forRoot({
        langs: translations,
        translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
      }),
    ],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: { close } },
      { provide: LanguageService, useValue: { current: () => 'uk' } },
      { provide: AuthService, useValue: { permissions: signal(readPermissions(token(claims))) } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(DelegateDialogComponent);
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController), close };
}

describe('DelegateDialogComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('ac3_2_offers_only_the_approval_roles_the_caller_holds_in_their_own_right', async () => {
    const { fixture } = await build({
      role_scopes: ['FACULTY_SECRETARY:9', 'DEAN:9', 'EMPLOYEE:64'],
      delegations: ['DEAN:9:2'],
    });

    expect(fixture.componentInstance.roles).toEqual(['FACULTY_SECRETARY']);
  });

  it('ac3_6_tells_a_caller_without_an_approval_role_that_nothing_can_be_delegated', async () => {
    const { fixture } = await build({ role_scopes: ['EMPLOYEE:64'] });

    expect(fixture.nativeElement.querySelector('[data-testid="delegate-no-roles"]').textContent).toContain(
      'Ви не маєте власної ролі',
    );
    expect(fixture.nativeElement.querySelector('[data-testid="delegate-submit"]')).toBeNull();
  });

  it('ac3_6_offers_only_the_organisations_where_the_caller_holds_the_role', async () => {
    const { fixture, http } = await build({ role_scopes: ['DEAN:9'] });

    fixture.componentInstance.roleChanged('DEAN');
    const requests = http.match((request) => request.url.endsWith('/organizations'));

    expect(requests.map((request) => request.request.params.get('type'))).toEqual([
      'FACULTY',
      'COLLEGE',
    ]);
    requests[0].flush([faculty, { ...faculty, id: 10, nameUk: 'Інший факультет' }]);
    requests[1].flush([]);

    expect(fixture.componentInstance.organizations().map((organization) => organization.id)).toEqual([9]);
    expect(fixture.componentInstance.form.controls.organizationId.value).toBe(9);
  });

  it('ac3_1_refuses_a_period_that_starts_in_the_past_or_runs_longer_than_90_days', async () => {
    const { fixture, http } = await build({ role_scopes: ['DEAN:9'] });
    const form = fixture.componentInstance.form;
    form.patchValue({
      role: 'DEAN',
      organizationId: 9,
      delegate: secretary,
      validFrom: '2020-01-01',
      validTo: '2020-01-10',
    });

    fixture.componentInstance.submit();

    expect(form.controls.validFrom.hasError('past')).toBe(true);

    form.patchValue({ validFrom: today(), validTo: days(today(), 91) });
    fixture.componentInstance.submit();

    expect(form.controls.validTo.hasError('range')).toBe(true);

    form.patchValue({ validTo: days(today(), -1) });
    fixture.componentInstance.submit();

    expect(form.controls.validTo.hasError('order')).toBe(true);
    http.expectNone(`${environment.apiUrl}/delegations`);
  });

  it('ac3_1_sends_the_delegation_and_closes_on_success', async () => {
    const { fixture, http, close } = await build({ role_scopes: ['DEAN:9'] });
    const validTo = days(today(), 14);
    fixture.componentInstance.form.patchValue({
      role: 'DEAN',
      organizationId: 9,
      delegate: secretary,
      validTo,
      reason: ' Відпустка ',
    });

    fixture.componentInstance.submit();
    const request = http.expectOne(`${environment.apiUrl}/delegations`);

    expect(request.request.body).toEqual({
      delegateId: 3,
      role: 'DEAN',
      organizationId: 9,
      validFrom: today(),
      validTo,
      reason: 'Відпустка',
    });
    request.flush({ id: 1 });

    expect(close).toHaveBeenCalledWith({ id: 1 });
  });

  it('ac3_1_renders_the_typed_refusals_of_the_server_inline', async () => {
    const { fixture, http, close } = await build({ role_scopes: ['DEAN:9'] });
    fixture.componentInstance.form.patchValue({
      role: 'DEAN',
      organizationId: 9,
      delegate: secretary,
      validTo: days(today(), 14),
    });

    fixture.componentInstance.submit();
    http
      .expectOne(`${environment.apiUrl}/delegations`)
      .flush(
        { type: 'urn:awards:problem:delegation-overlap', status: 422 },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="delegate-error"]').textContent).toContain(
      'Ви вже делегували цю роль',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('ac3_6_searches_the_directory_for_a_delegate_from_two_characters', async () => {
    vi.useFakeTimers();
    const { fixture, http } = await build({ role_scopes: ['DEAN:9'] });

    fixture.componentInstance.form.controls.delegate.setValue('с');
    vi.advanceTimersByTime(300);
    http.expectNone((request) => request.url === `${environment.apiUrl}/users`);

    fixture.componentInstance.form.controls.delegate.setValue('сек');
    vi.advanceTimersByTime(300);
    const request = http.expectOne((request) => request.url === `${environment.apiUrl}/users`);

    expect(request.request.params.get('q')).toBe('сек');
    expect(request.request.params.get('status')).toBe('ACTIVE');
    request.flush({ content: [secretary], totalElements: 1, totalPages: 1, size: 10, number: 0, first: true, last: true });

    expect(fixture.componentInstance.candidates()).toEqual([secretary]);
    expect(fixture.componentInstance.display(secretary)).toContain('Секретар Аліна');
    vi.useRealTimers();
  });
});
