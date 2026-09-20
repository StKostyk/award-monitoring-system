import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { AuthService } from '../../core/auth/auth.service';
import { UserProfile } from '../../core/auth/user-profile';
import { LanguageService } from '../../core/i18n/language.service';
import { HomeComponent } from './home.component';

const profile: UserProfile = {
  id: 3,
  email: 'dean.fmi@chnu.edu.ua',
  firstName: 'Martyn',
  lastName: 'Martyniuk',
  roles: [
    {
      role: 'DEAN',
      organization: { id: 9, name: 'Faculty of Mathematics', nameUk: 'Факультет математики', code: 'FMI', type: 'FACULTY' },
      validFrom: '2026-09-01',
      validTo: null,
    },
  ],
  organization: { id: 9, name: 'Faculty of Mathematics', nameUk: 'Факультет математики', code: 'FMI', type: 'FACULTY' },
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00Z',
  lastLoginAt: null,
};

describe('HomeComponent', () => {
  const auth = { profile: signal<UserProfile | null>(profile) };
  let lang = 'uk';
  const language = { current: () => lang };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HomeComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              home: { greeting: 'Вітаємо, {{name}}', roles: 'Ролі', organization: 'Підрозділ', email: 'Пошта', noRoles: 'Немає' },
              roles: { DEAN: 'Декан' },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: language },
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

  it('renders nothing until the profile is loaded', () => {
    auth.profile.set(null);
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-card"]')).toBeNull();
  });
});
