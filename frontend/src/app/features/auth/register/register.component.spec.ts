import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationSummary } from '../../../core/organizations/organizations.service';
import { RegisterComponent } from './register.component';

const noticeUrl = `${environment.apiUrl}/public/privacy-notice`;
const notice = { version: '1.0', publishedOn: '2026-10-15', versions: [] };

const departments: OrganizationSummary[] = [
  {
    id: 64,
    name: 'Department of Algebra',
    nameUk: 'Кафедра алгебри',
    code: 'DAI',
    type: 'DEPARTMENT',
    parent: {
      id: 9,
      name: 'Faculty of Mathematics',
      nameUk: 'Факультет математики',
      code: 'FMI',
      type: 'FACULTY',
    },
  },
  {
    id: 20,
    name: 'Department of Biochemistry',
    nameUk: 'Кафедра біохімії',
    code: 'DBBT',
    type: 'DEPARTMENT',
    parent: {
      id: 2,
      name: 'Institute of Biology',
      nameUk: 'Інститут біології',
      code: 'IBCB',
      type: 'FACULTY',
    },
  },
];

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;
  let lang = 'uk';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        RegisterComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: { register: { errors: { 'email-taken': 'Адреса зайнята' } } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LanguageService, useValue: { current: () => lang } },
        {
          provide: AuthService,
          useValue: { isAuthenticated: signal(false), userId: signal(null) },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/organizations?type=DEPARTMENT`).flush(departments);
    http.expectOne(noticeUrl).flush(notice);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('ac27 ac28 groups departments by faculty in the active language', () => {
    lang = 'uk';
    const groups = fixture.componentInstance.groups();

    expect(groups.map((g) => g.faculty)).toEqual(['Інститут біології', 'Факультет математики']);
    expect(fixture.componentInstance.name(departments[0])).toBe('Кафедра алгебри');
    lang = 'en';
    expect(fixture.componentInstance.name(departments[0])).toBe('Department of Algebra');
  });

  it('ac28 validates the institutional address and password length before calling the server', () => {
    const form = fixture.componentInstance.form;
    form.setValue({
      email: 'x@gmail.com',
      password: 'short',
      firstName: 'A',
      lastName: 'B',
      organizationId: 64,
      privacyNotice: true,
    });

    fixture.componentInstance.submit();

    expect(form.controls.email.hasError('pattern')).toBe(true);
    expect(form.controls.password.hasError('minlength')).toBe(true);
    form.controls.email.setValue('x@CHNU.edu.ua');
    expect(form.controls.email.valid).toBe(true);
    form.controls.email.setValue('x@chnuXeduXua');
    expect(form.controls.email.hasError('pattern')).toBe(true);
    form.controls.password.setValue('ю'.repeat(37));
    expect(form.controls.password.hasError('maxbytes')).toBe(true);
    form.controls.firstName.setValue('<b>Олена</b>');
    expect(form.controls.firstName.hasError('invalid')).toBe(true);
    form.controls.firstName.setValue('   ');
    expect(form.controls.firstName.hasError('required')).toBe(true);
    form.controls.firstName.setValue("Мар'яна-Олена");
    expect(form.controls.firstName.valid).toBe(true);
    http.expectNone(`${environment.apiUrl}/auth/register`);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac21 registers with trimmed names and moves to the pending page', () => {
    fixture.componentInstance.form.setValue({
      email: 'new.user@chnu.edu.ua',
      password: 'correct-horse-battery',
      firstName: ' Олена ',
      lastName: 'Нова  ',
      organizationId: 64,
      privacyNotice: true,
    });

    fixture.componentInstance.submit();
    const request = http.expectOne(`${environment.apiUrl}/auth/register`);
    expect(request.request.body).toEqual({
      email: 'new.user@chnu.edu.ua',
      password: 'correct-horse-battery',
      firstName: 'Олена',
      lastName: 'Нова',
      organizationId: 64,
      privacyNoticeVersion: '1.0',
    });
    request.flush(
      { email: 'new.user@chnu.edu.ua', status: 'PENDING' },
      { status: 201, statusText: 'Created' },
    );

    expect(navigate).toHaveBeenCalledWith(['/registration-pending'], {
      queryParams: { email: 'new.user@chnu.edu.ua' },
    });
  });

  it('ac23 shows the typed problem from the server', () => {
    fixture.componentInstance.form.setValue({
      email: 'taken@chnu.edu.ua',
      password: 'correct-horse-battery',
      firstName: 'A',
      lastName: 'B',
      organizationId: 64,
      privacyNotice: true,
    });

    fixture.componentInstance.submit();
    http
      .expectOne(`${environment.apiUrl}/auth/register`)
      .flush(
        { type: 'urn:awards:problem:email-taken', status: 409 },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBe('register.errors.email-taken');
    expect(
      fixture.nativeElement.querySelector('[data-testid="register-error"]').textContent,
    ).toContain('Адреса зайнята');
    expect(fixture.componentInstance.submitting()).toBe(false);
  });

  it('ac1_3 refuses the form until the privacy notice box is ticked', () => {
    const form = fixture.componentInstance.form;
    form.setValue({
      email: 'new.user@chnu.edu.ua',
      password: 'correct-horse-battery',
      firstName: 'Олена',
      lastName: 'Нова',
      organizationId: 64,
      privacyNotice: false,
    });

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(form.controls.privacyNotice.hasError('required')).toBe(true);
    expect(
      fixture.nativeElement.querySelector('[data-testid="privacy-notice-required"]'),
    ).not.toBeNull();
    const link = fixture.nativeElement.querySelector('[data-testid="privacy-notice-link"]');
    expect(link.getAttribute('href')).toBe('/privacy-notice');
    expect(link.getAttribute('target')).toBe('_blank');
    http.expectNone(`${environment.apiUrl}/auth/register`);
  });

  it('ac1_3 asks to tick the box again when the notice changed while the form was open', () => {
    fixture.componentInstance.form.setValue({
      email: 'new.user@chnu.edu.ua',
      password: 'correct-horse-battery',
      firstName: 'Олена',
      lastName: 'Нова',
      organizationId: 64,
      privacyNotice: true,
    });

    fixture.componentInstance.submit();
    http.expectOne(`${environment.apiUrl}/auth/register`).flush(
      {
        type: 'urn:awards:problem:validation-failed',
        errors: [{ field: 'privacyNoticeVersion', code: 'outdated', message: 'outdated' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    http.expectOne(noticeUrl).flush({ ...notice, version: '1.1' });

    expect(fixture.componentInstance.error()).toBe('register.errors.notice-outdated');
    expect(fixture.componentInstance.form.controls.privacyNotice.value).toBe(false);
    expect(fixture.componentInstance.noticeVersion()).toBe('1.1');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac1_3 does not register while the notice version is unknown', () => {
    fixture.componentInstance.noticeVersion.set(null);
    fixture.componentInstance.form.setValue({
      email: 'new.user@chnu.edu.ua',
      password: 'correct-horse-battery',
      firstName: 'Олена',
      lastName: 'Нова',
      organizationId: 64,
      privacyNotice: true,
    });

    fixture.componentInstance.submit();
    http.expectOne(noticeUrl).flush(null, { status: 503, statusText: 'Unavailable' });

    expect(fixture.componentInstance.error()).toBe('register.errors.notice-unavailable');
    http.expectNone(`${environment.apiUrl}/auth/register`);
  });
});
