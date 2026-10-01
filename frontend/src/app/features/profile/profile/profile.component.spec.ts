import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { UserProfile } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { ProfileComponent, fileName } from './profile.component';

const PROFILE: UserProfile = {
  id: 5,
  email: 'employee.fmi@chnu.edu.ua',
  firstName: 'Анастасія',
  lastName: 'Петренко',
  roles: [
    {
      id: 1,
      role: 'EMPLOYEE',
      organization: {
        id: 64,
        name: 'DAI',
        nameUk: 'Кафедра алгебри та інформатики',
        code: 'DAI',
        type: 'DEPARTMENT',
      },
      validFrom: '2026-09-01',
      validTo: null,
    },
  ],
  organization: {
    id: 64,
    name: 'DAI',
    nameUk: 'Кафедра алгебри та інформатики',
    code: 'DAI',
    type: 'DEPARTMENT',
  },
  faculty: {
    id: 9,
    name: 'FMI',
    nameUk: 'Факультет математики та інформатики',
    code: 'FMI',
    type: 'FACULTY',
  },
  status: 'ACTIVE',
  createdAt: '2026-09-01T08:00:00Z',
  lastLoginAt: '2026-09-29T08:00:00Z',
  membershipConfirmed: true,
};

describe('ProfileComponent', () => {
  let http: HttpTestingController;
  const dialog = { open: vi.fn() };
  const auth = {
    profile: signal<UserProfile | null>(null),
    loadProfile: vi.fn(() => Promise.resolve(PROFILE)),
  };

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [
        ProfileComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: { profile: { saved: 'Збережено', membershipConfirmed: 'Членство підтверджено' } },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProfileComponent);
    http
      .expectOne(`${environment.apiUrl}/delegations?state=active`)
      .flush({ given: [], received: [] });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: { nativeElement: HTMLElement }, id: string): string {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`)?.textContent?.trim() ?? '';
  }

  afterEach(() => {
    http.verify();
    vi.clearAllMocks();
  });

  it('ac11 shows the address, department with its faculty, roles and membership', async () => {
    const fixture = await setup();

    expect(text(fixture, 'profile-email')).toBe('employee.fmi@chnu.edu.ua');
    expect(text(fixture, 'profile-department')).toBe('Кафедра алгебри та інформатики');
    expect(text(fixture, 'profile-faculty')).toBe('Факультет математики та інформатики');
    expect(text(fixture, 'profile-membership')).toBe('Членство підтверджено');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="profile-role"]').length).toBe(1);
  });

  it('ac17 keeps save disabled while the names are pristine or invalid', async () => {
    const fixture = await setup();
    const save = () =>
      fixture.nativeElement.querySelector('[data-testid="profile-save"]') as HTMLButtonElement;
    expect(save().disabled).toBe(true);

    fixture.componentInstance.names.controls.lastName.setValue('Петренко1');
    fixture.componentInstance.names.markAsDirty();
    fixture.detectChanges();
    expect(save().disabled).toBe(true);
    expect(fixture.componentInstance.firstError('lastName')).toBe('invalid');

    fixture.componentInstance.names.controls.lastName.setValue('   ');
    expect(fixture.componentInstance.firstError('lastName')).toBe('required');

    fixture.componentInstance.names.controls.lastName.setValue('Петренко-Коваль');
    fixture.detectChanges();
    expect(save().disabled).toBe(false);
  });

  it('ac13 sends only the changed name and updates the header profile', async () => {
    const fixture = await setup();
    fixture.componentInstance.names.controls.lastName.setValue(' Петренко-Коваль ');
    fixture.componentInstance.names.markAsDirty();

    fixture.componentInstance.save();
    const request = http.expectOne(`${environment.apiUrl}/users/me`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ lastName: 'Петренко-Коваль' });
    request.flush({ ...PROFILE, lastName: 'Петренко-Коваль' });
    fixture.detectChanges();

    expect(auth.profile()?.lastName).toBe('Петренко-Коваль');
    expect(text(fixture, 'profile-saved')).toBe('Збережено');
    expect(fixture.componentInstance.names.pristine).toBe(true);
  });

  it('ac17 puts server field errors under the field and keeps the input on a network failure', async () => {
    const fixture = await setup();
    fixture.componentInstance.names.controls.firstName.setValue('Олена');
    fixture.componentInstance.names.markAsDirty();

    fixture.componentInstance.save();
    http.expectOne(`${environment.apiUrl}/users/me`).flush(
      {
        type: 'urn:awards:problem:validation-failed',
        errors: [{ field: 'firstName', code: 'too-long', message: '' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    expect(fixture.componentInstance.firstError('firstName')).toBe('too-long');

    fixture.componentInstance.names.controls.firstName.setValue('Олена');
    fixture.componentInstance.save();
    http
      .expectOne(`${environment.apiUrl}/users/me`)
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    expect(fixture.componentInstance.problem()).toBe('profile.errors.network');
    expect(fixture.componentInstance.names.controls.firstName.value).toBe('Олена');
  });

  it('ac17 opens the address dialog and announces where the link went', async () => {
    dialog.open.mockReturnValue({ afterClosed: () => of('mover.new@chnu.edu.ua') });
    const fixture = await setup();

    fixture.componentInstance.changeAddress();

    expect(dialog.open.mock.calls[0][1]).toMatchObject({
      data: { email: 'employee.fmi@chnu.edu.ua' },
    });
    expect(fixture.componentInstance.sentTo()).toBe('mover.new@chnu.edu.ua');
  });

  it('ac35 downloads the export under its attachment name and disables the button meanwhile', async () => {
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:export');
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    const fixture = await setup();
    const button = (): HTMLButtonElement =>
      fixture.nativeElement.querySelector('[data-testid="profile-download"]');

    button().click();
    fixture.detectChanges();
    expect(button().disabled).toBe(true);
    const request = http.expectOne(`${environment.apiUrl}/users/me/export`);
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['{}']), {
      headers: {
        'Content-Disposition': 'attachment; filename="award-monitoring-export-2026-09-30.json"',
      },
    });
    fixture.detectChanges();

    expect(createUrl).toHaveBeenCalled();
    const link = click.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toBe('award-monitoring-export-2026-09-30.json');
    expect(button().disabled).toBe(false);
    expect(fixture.componentInstance.exportMessage()).toBe('profile.downloaded');
    vi.restoreAllMocks();
  });

  it('ac35 shows too many requests on 429 and a general failure otherwise', async () => {
    const fixture = await setup();

    fixture.componentInstance.downloadData();
    http
      .expectOne(`${environment.apiUrl}/users/me/export`)
      .flush(new Blob(['{}']), { status: 429, statusText: 'Too Many Requests' });
    expect(fixture.componentInstance.exportProblem()).toBe('profile.errors.tooMany');

    fixture.componentInstance.downloadData();
    http
      .expectOne(`${environment.apiUrl}/users/me/export`)
      .flush(new Blob(['{}']), { status: 500, statusText: 'Server Error' });
    expect(fixture.componentInstance.exportProblem()).toBe('profile.errors.export');
    expect(fixture.componentInstance.exporting()).toBe(false);
  });

  it('ac35 falls back to a default name without an attachment header', () => {
    expect(fileName(null)).toBe('award-monitoring-export.json');
    expect(fileName('attachment; filename=data.json')).toBe('data.json');
  });
});
