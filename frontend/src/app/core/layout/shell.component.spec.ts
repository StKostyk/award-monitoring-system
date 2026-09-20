import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { AuthService } from '../auth/auth.service';
import { LanguageService } from '../i18n/language.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  const auth = {
    isAuthenticated: signal(true),
    fullName: computed(() => 'Martyn Martyniuk'),
    logout: vi.fn().mockResolvedValue(undefined),
  };
  const language = { toggle: vi.fn(), current: () => 'uk' };

  beforeEach(async () => {
    auth.logout.mockClear();
    language.toggle.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        ShellComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: { app: { title: 'Нагороди', language: 'EN', logout: 'Вийти' } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: language },
      ],
    }).compileComponents();
  });

  it('ac12 shows the signed-in user and the logout button', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="user-name"]')?.textContent).toContain('Martyn Martyniuk');
    (element.querySelector('[data-testid="logout"]') as HTMLButtonElement).click();

    expect(auth.logout).toHaveBeenCalled();
  });

  it('ac17 toggles the language from the toolbar', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="language-toggle"]') as HTMLButtonElement).click();

    expect(language.toggle).toHaveBeenCalled();
  });

  it('hides the user controls when anonymous', () => {
    auth.isAuthenticated.set(false);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="logout"]')).toBeNull();
  });
});
