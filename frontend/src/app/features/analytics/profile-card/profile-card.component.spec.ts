import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../../core/auth/auth.service';
import { RoleType, UserProfile } from '../../../core/auth/user-profile';
import { ALGEBRA, languageStub, translations } from '../analytics.testing';
import { ProfileCardComponent } from './profile-card.component';

describe('ProfileCardComponent', () => {
  function profile(roles: RoleType[]): UserProfile {
    return {
      id: 5,
      email: 'olena@chnu.edu.ua',
      firstName: 'Олена',
      lastName: 'Коваль',
      roles: roles.map((role, index) => ({
        id: index,
        role,
        organization: ALGEBRA,
        validFrom: '2024-01-01',
        validTo: null,
      })),
      organization: ALGEBRA,
      faculty: null,
      status: 'ACTIVE',
      createdAt: '2024-01-01T00:00:00Z',
      lastLoginAt: null,
      membershipConfirmed: true,
    };
  }

  function render(account: UserProfile | null, language: 'en' | 'uk' = 'uk'): HTMLElement {
    const current = signal(account);
    TestBed.configureTestingModule({
      imports: [ProfileCardComponent, translations(language)],
      providers: [
        languageStub(language).provider,
        {
          provide: AuthService,
          useValue: {
            profile: current,
            fullName: () => (account ? `${account.firstName} ${account.lastName}` : ''),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ProfileCardComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('ac2_1_shows_initials_name_position_and_department', () => {
    const card = render(profile(['EMPLOYEE']));

    expect(card.querySelector('[data-testid="profile-initials"]')?.textContent?.trim()).toBe('ОК');
    expect(card.querySelector('[data-testid="profile-name"]')?.textContent?.trim()).toBe(
      'Олена Коваль',
    );
    expect(card.querySelector('[data-testid="profile-position"]')?.textContent?.trim()).toBe(
      'Працівник',
    );
    expect(card.querySelector('[data-testid="profile-department"]')?.textContent?.trim()).toBe(
      'Кафедра алгебри та інформатики',
    );
  });

  it('ac2_1_names_the_leading_role_as_the_position_in_english', () => {
    const card = render(profile(['EMPLOYEE', 'DEAN']), 'en');

    expect(card.querySelector('[data-testid="profile-position"]')?.textContent?.trim()).toBe(
      'Dean',
    );
    expect(card.querySelector('[data-testid="profile-department"]')?.textContent?.trim()).toBe(
      'Department of Algebra and Informatics',
    );
  });

  it('ac2_1_renders_nothing_before_the_profile_loads', () => {
    const card = render(null);

    expect(card.querySelector('[data-testid="profile-name"]')).toBeNull();
  });
});
