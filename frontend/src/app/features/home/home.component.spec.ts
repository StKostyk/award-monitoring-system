import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { UserProfile } from '../../core/auth/user-profile';
import { LanguageService } from '../../core/i18n/language.service';
import { AwardsService } from '../awards/awards.service';
import { HomeComponent } from './home.component';

const profile: UserProfile = {
  id: 3,
  email: 'dean.fmi@chnu.edu.ua',
  firstName: 'Martyn',
  lastName: 'Martyniuk',
  roles: [
    {
      id: 11,
      role: 'DEAN',
      organization: {
        id: 9,
        name: 'Faculty of Mathematics',
        nameUk: 'Факультет математики',
        code: 'FMI',
        type: 'FACULTY',
      },
      validFrom: '2026-09-01',
      validTo: null,
    },
  ],
  organization: {
    id: 9,
    name: 'Faculty of Mathematics',
    nameUk: 'Факультет математики',
    code: 'FMI',
    type: 'FACULTY',
  },
  faculty: null,
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  lastLoginAt: null,
  membershipConfirmed: true,
};

const newcomer: UserProfile = {
  ...profile,
  roles: [],
  organization: {
    id: 64,
    name: 'Department of Algebra',
    nameUk: 'Кафедра алгебри',
    code: 'DAI',
    type: 'DEPARTMENT',
  },
  membershipConfirmed: false,
};

describe('HomeComponent', () => {
  const granted = signal<string[]>([]);
  const auth = {
    profile: signal<UserProfile | null>(profile),
    permissions: computed(() => ({
      hasPermission: (permission: string) => granted().includes(permission),
    })),
  };
  const awards = { list: vi.fn() };
  let lang = 'uk';
  const language = { current: () => lang };

  beforeEach(async () => {
    granted.set([]);
    awards.list
      .mockReset()
      .mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, size: 5, number: 0 }));
    await TestBed.configureTestingModule({
      imports: [
        HomeComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              home: {
                greeting: 'Вітаємо, {{name}}',
                roles: 'Ролі',
                organization: 'Підрозділ',
                email: 'Пошта',
                noRoles: 'Немає',
                membership: {
                  title: 'Членство у підрозділі не підтверджено',
                  text: 'Ваше членство у підрозділі ще не підтверджено: {{organization}}.',
                  hint: 'Зверніться до секретаря факультету.',
                },
              },
              roles: { DEAN: 'Декан' },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: language },
        { provide: AwardsService, useValue: awards },
      ],
    }).compileComponents();
  });

  it('ac13 shows the profile with roles in the active language', () => {
    lang = 'uk';
    auth.profile.set(profile);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;

    expect(text).toContain('Вітаємо, Martyn');
    expect(text).toContain('dean.fmi@chnu.edu.ua');
    expect(text).toContain('Декан');
    expect(text).toContain('Факультет математики');
  });

  it('falls back to the English organisation name', () => {
    lang = 'en';
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Faculty of Mathematics');
  });

  it('ac2_6_shows_the_membership_banner_until_the_department_confirms', () => {
    lang = 'uk';
    auth.profile.set(newcomer);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const banner = fixture.nativeElement.querySelector('[data-testid="membership-banner"]');

    expect(banner).not.toBeNull();
    expect(banner.textContent).toContain('Ваше членство у підрозділі ще не підтверджено');
    expect(banner.textContent).toContain('Кафедра алгебри');
  });

  it('ac2_6_hides_the_membership_banner_once_a_role_is_granted', () => {
    auth.profile.set(profile);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="membership-banner"]')).toBeNull();
  });

  it('renders nothing until the profile is loaded', () => {
    auth.profile.set(null);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-card"]')).toBeNull();
  });

  it('ac1_17_shows_my_submissions_to_those_who_create_awards', () => {
    granted.set(['award:create']);
    auth.profile.set(profile);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="my-submissions"]')).not.toBeNull();
    expect(awards.list).toHaveBeenCalledWith(expect.objectContaining({ status: 'PENDING' }), 0, 5);
  });

  it('ac1_17_hides_my_submissions_without_award_create', () => {
    auth.profile.set(profile);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="my-submissions"]')).toBeNull();
    expect(awards.list).not.toHaveBeenCalled();
  });
});
