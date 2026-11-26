import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { NEVER, of } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { UserProfile } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { NotificationApi } from '../../../core/notifications/notification.api';
import { ProfileComponent, sectionOf } from './profile.component';

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

  async function setup(url = '/profile') {
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
        provideRouter([{ path: 'profile', component: ProfileComponent }]),
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationApi, useValue: { preferences: () => NEVER } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    const componentInstance = await harness.navigateByUrl(url, ProfileComponent);
    http
      .expectOne(`${environment.apiUrl}/delegations?state=active`)
      .flush({ given: [], received: [] });
    await harness.fixture.whenStable();
    harness.detectChanges();
    return {
      harness,
      componentInstance,
      nativeElement: harness.routeNativeElement!,
      detectChanges: () => harness.detectChanges(),
    };
  }

  function text(fixture: { nativeElement: HTMLElement }, id: string): string {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`)?.textContent?.trim() ?? '';
  }

  function query(fixture: { nativeElement: HTMLElement }, id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  }

  afterEach(() => {
    http.verify();
    vi.clearAllMocks();
  });

  it('ac6_1 shows the person, address, membership and dates in the banner', async () => {
    const fixture = await setup();

    expect(text(fixture, 'profile-name')).toContain('Петренко');
    expect(text(fixture, 'profile-role-line')).not.toBe('');
    expect(text(fixture, 'profile-email')).toBe('employee.fmi@chnu.edu.ua');
    expect(text(fixture, 'profile-membership')).toBe('Членство підтверджено');
    expect(text(fixture, 'profile-created')).toBe('01.09.2026');
    expect(text(fixture, 'profile-last-login')).not.toBe('');
  });

  it('ac11 shows the department with its faculty and the roles in the roles section', async () => {
    const fixture = await setup('/profile?section=roles');

    expect(text(fixture, 'profile-department')).toBe('Кафедра алгебри та інформатики');
    expect(text(fixture, 'profile-faculty')).toBe('Факультет математики та інформатики');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="profile-role"]').length).toBe(1);
    expect(query(fixture, 'profile-names')).toBeNull();
  });

  it('ac6_2 shows the names first and marks the first section without a choice', async () => {
    const fixture = await setup();

    expect(query(fixture, 'profile-names')).not.toBeNull();
    expect(query(fixture, 'profile-section-names')!.getAttribute('aria-current')).toBe('true');
    expect(query(fixture, 'profile-section-roles')!.getAttribute('aria-current')).toBeNull();
    expect(query(fixture, 'profile-back')).toBeNull();
    expect(query(fixture, 'profile-section-address')!.getAttribute('href')).toBe(
      '/profile?section=address',
    );
  });

  it('ac6_2 opens the section named in the address and goes back to the list', async () => {
    const fixture = await setup('/profile?section=address');

    expect(query(fixture, 'profile-address')).not.toBeNull();
    expect(query(fixture, 'profile-section-address')!.getAttribute('aria-current')).toBe('true');

    query(fixture, 'profile-back')!.click();
    await fixture.harness.fixture.whenStable();
    fixture.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/profile');
    expect(query(fixture, 'profile-back')).toBeNull();
  });

  it('ac6_2 reads the section from the query or the old anchor', () => {
    expect(sectionOf('roles', null)).toBe('roles');
    expect(sectionOf(null, 'notifications')).toBe('notifications');
    expect(sectionOf('elsewhere', null)).toBeNull();
    expect(sectionOf(null, 'top')).toBeNull();
  });

  it('ac3_6 loads the notification switches in their section', async () => {
    const fixture = await setup('/profile?section=notifications');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(
        fixture.nativeElement.querySelector(
          '[data-testid="profile-notifications"] [data-testid="notification-preferences"]',
        ),
      ).not.toBeNull();
    });
  });

  it('ac3_6 opens the notifications from the old anchor', async () => {
    const fixture = await setup('/profile#notifications');

    expect(query(fixture, 'profile-notifications')).not.toBeNull();
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

  it('ac2_5 links the privacy section to the privacy centre', async () => {
    const fixture = await setup('/profile?section=privacy');

    for (const link of ['consents', 'export', 'erasure']) {
      expect(query(fixture, `profile-privacy-${link}`)!.getAttribute('href')).toBe('/privacy');
    }
    expect(query(fixture, 'profile-download')).toBeNull();
  });
});
