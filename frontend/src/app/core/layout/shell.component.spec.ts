import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, of } from 'rxjs';
import { vi } from 'vitest';

import { DelegationsService } from '../../features/delegations/delegations.service';
import {
  ErasureRequest,
  PrivacyCentreService,
} from '../../features/privacy/privacy-centre.service';
import { AuthService } from '../auth/auth.service';
import { NO_PERMISSIONS, readPermissions } from '../auth/permissions';
import { BrandService, LocalizedText } from '../brand/brand.service';
import { LanguageService } from '../i18n/language.service';
import { NotificationApi } from '../notifications/notification.api';
import { ShellComponent } from './shell.component';

const dean = { id: 2, firstName: 'Мартин', lastName: 'Мартинюк', email: 'dean.fmi@chnu.edu.ua' };

function delegation(state: 'active' | 'revoked') {
  return {
    id: 1,
    role: 'DEAN' as const,
    organization: {
      id: 9,
      name: 'Faculty',
      nameUk: 'Факультет математики',
      code: 'FMI',
      type: 'FACULTY' as const,
    },
    delegator: dean,
    delegate: {
      id: 3,
      firstName: 'Аліна',
      lastName: 'Секретар',
      email: 'secretary.fmi@chnu.edu.ua',
    },
    validFrom: '2026-09-24',
    validTo: '2026-10-08',
    reason: null,
    state,
    createdAt: '2026-09-24T08:00:00Z',
    revokedAt: null,
  };
}

function tokenWith(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('ShellComponent', () => {
  const auth = {
    isAuthenticated: signal(true),
    fullName: computed(() => 'Martyn Martyniuk'),
    permissions: signal(NO_PERMISSIONS),
    logout: vi.fn().mockResolvedValue(undefined),
    login: vi.fn(),
  };
  const language = { toggle: vi.fn(), current: () => 'uk' };
  const delegations = { list: vi.fn().mockReturnValue(of({ given: [], received: [] })) };
  const notifications = {
    unreadCount: vi.fn().mockReturnValue(of(3)),
    list: vi.fn().mockReturnValue(of({ content: [], totalElements: 0 })),
  };
  const privacy = {
    erasure: signal<ErasureRequest | null>(null),
    summary: vi.fn().mockReturnValue(of({})),
    forgetErasure: vi.fn(),
  };
  const layout = new BehaviorSubject<BreakpointState>({ matches: true, breakpoints: {} });
  const brands = {
    brand: signal({
      id: 'chnu',
      name: { uk: 'Облік нагород ЧНУ', en: 'ChNU Awards' },
      organization: { uk: 'Чернівецький університет', en: 'Chernivtsi University' },
      logo: 'brand/chnu/logo.svg',
    }),
    text: (text: LocalizedText) => text.uk,
  };

  beforeEach(async () => {
    auth.logout.mockClear();
    auth.permissions.set(NO_PERMISSIONS);
    language.toggle.mockClear();
    delegations.list.mockClear();
    privacy.erasure.set(null);
    privacy.summary.mockClear();
    privacy.forgetErasure.mockClear();
    delegations.list.mockReturnValue(of({ given: [], received: [] }));
    layout.next({ matches: true, breakpoints: {} });
    document.documentElement.removeAttribute('data-color-scheme');
    await TestBed.configureTestingModule({
      imports: [
        ShellComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              app: {
                language: 'EN',
                logout: 'Вийти',
                menu: 'Меню',
                home: 'Головна',
                scheme: { title: 'Тема', system: 'Як на пристрої', light: 'Світла', dark: 'Темна' },
              },
              admin: { users: { title: 'Користувачі' } },
              notifications: {
                bell: 'Сповіщення',
                bellUnread: 'Сповіщення, непрочитаних: {{count}}',
              },
              delegations: {
                title: 'Мої делегування',
                actingFor: 'Діє за дорученням: {{name}}',
              },
              compliance: { title: 'Відповідність' },
              privacy: {
                erasure: {
                  banner: 'Ваш обліковий запис буде видалено {{date}}.',
                  bannerLink: 'Скасувати',
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: language },
        { provide: DelegationsService, useValue: delegations },
        { provide: PrivacyCentreService, useValue: privacy },
        { provide: NotificationApi, useValue: notifications },
        { provide: BrandService, useValue: brands },
        { provide: BreakpointObserver, useValue: { observe: () => layout } },
      ],
    }).compileComponents();
  });

  it('ac12 shows the signed-in user and the logout button', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="user-name"]')?.textContent).toContain(
      'Martyn Martyniuk',
    );
    (element.querySelector('[data-testid="logout"]') as HTMLButtonElement).click();

    expect(auth.logout).toHaveBeenCalled();
  });

  it('ac2_1_shows_the_bell_with_the_unread_count_before_the_account_menu', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const bell = element.querySelector('[data-testid="notification-bell"]') as HTMLElement;
    const account = element.querySelector('[data-testid="user-menu"]') as HTMLElement;

    expect(bell.compareDocumentPosition(account) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(bell.getAttribute('aria-label')).toBe('Сповіщення, непрочитаних: 3');
  });

  it('ac17 toggles the language from the toolbar', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    (
      fixture.nativeElement.querySelector('[data-testid="language-toggle"]') as HTMLButtonElement
    ).click();

    expect(language.toggle).toHaveBeenCalled();
  });

  it('ac2_10_shows_the_users_entry_only_to_callers_who_may_open_the_directory', () => {
    auth.isAuthenticated.set(true);
    const anonymous = TestBed.createComponent(ShellComponent);
    anonymous.detectChanges();

    expect(anonymous.nativeElement.querySelector('[data-testid="nav-users"]')).toBeNull();

    auth.permissions.set(readPermissions(tokenWith({ permissions: ['user:read:scope'] })));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('[data-testid="nav-users"]');

    expect(link.getAttribute('href')).toBe('/admin/users');
    expect(link.textContent).toContain('Користувачі');
  });

  it('ac1_9_shows_my_awards_to_a_holder_of_award_read_own', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(readPermissions(tokenWith({ permissions: [] })));
    const unconfirmed = TestBed.createComponent(ShellComponent);
    unconfirmed.detectChanges();

    expect(unconfirmed.nativeElement.querySelector('[data-testid="nav-awards"]')).toBeNull();

    auth.permissions.set(readPermissions(tokenWith({ permissions: ['award:read:own'] })));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-awards"]').getAttribute('href'),
    ).toBe('/awards');
  });

  it('ac2_4_shows_my_dashboard_to_a_holder_of_analytics_read_own', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(readPermissions(tokenWith({ permissions: ['award:read:own'] })));
    const without = TestBed.createComponent(ShellComponent);
    without.detectChanges();

    expect(without.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeNull();

    auth.permissions.set(readPermissions(tokenWith({ permissions: ['analytics:read:own'] })));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]').getAttribute('href'),
    ).toBe('/dashboard');
  });

  it('ac3_6_shows_the_delegations_entry_only_to_a_holder_of_an_approval_role', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(readPermissions(tokenWith({ role_scopes: ['EMPLOYEE:64'] })));
    const employee = TestBed.createComponent(ShellComponent);
    employee.detectChanges();

    expect(employee.nativeElement.querySelector('[data-testid="nav-delegations"]')).toBeNull();

    auth.permissions.set(readPermissions(tokenWith({ role_scopes: ['DEAN:9'] })));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('[data-testid="nav-delegations"]');

    expect(link.getAttribute('href')).toBe('/delegations');
    expect(link.textContent).toContain('Мої делегування');
  });

  it('ac2_5_shows_the_compliance_entry_only_to_the_gdpr_officer', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(readPermissions(tokenWith({ permissions: ['user:read:all'] })));
    const administrator = TestBed.createComponent(ShellComponent);
    administrator.detectChanges();

    expect(administrator.nativeElement.querySelector('[data-testid="nav-compliance"]')).toBeNull();

    auth.permissions.set(readPermissions(tokenWith({ permissions: ['compliance:read'] })));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('[data-testid="nav-compliance"]');

    expect(link.getAttribute('href')).toBe('/compliance');
    expect(link.textContent).toContain('Відповідність');
  });

  it('ac3_6_names_the_delegator_while_a_received_delegation_is_active', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(
      readPermissions(
        tokenWith({ role_scopes: ['FACULTY_SECRETARY:9', 'DEAN:9'], delegations: ['DEAN:9:2'] }),
      ),
    );
    delegations.list.mockReturnValue(of({ given: [], received: [delegation('active')] }));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(delegations.list).toHaveBeenCalledWith('active');
    expect(fixture.nativeElement.querySelector('[data-testid="acting-for"]').textContent).toContain(
      'Діє за дорученням: Мартин Мартинюк',
    );
  });

  it('ac3_2_ac3_3_keeps_the_banner_away_without_a_delegation_in_the_token', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(readPermissions(tokenWith({ role_scopes: ['FACULTY_SECRETARY:9'] })));
    delegations.list.mockReturnValue(of({ given: [], received: [delegation('revoked')] }));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(delegations.list).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-testid="acting-for"]')).toBeNull();
  });

  it('hides the user controls when anonymous', () => {
    auth.isAuthenticated.set(false);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="logout"]')).toBeNull();
  });

  it('ac2_5_offers_an_anonymous_visitor_to_sign_in_and_come_back', () => {
    auth.isAuthenticated.set(false);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/public/achievements?year=2025');

    (fixture.nativeElement.querySelector('[data-testid="login"]') as HTMLButtonElement).click();

    expect(auth.login).toHaveBeenCalledWith('/public/achievements?year=2025');
    expect(fixture.nativeElement.querySelector('[data-testid="language-toggle"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-achievements"]')).toBeNull();
  });

  it('ac2_5_a_signed_in_user_gets_no_sign_in_button', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="login"]')).toBeNull();
  });

  it('ac3_shows_the_brand_name_organization_and_logo_in_the_side_menu', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const nav: HTMLElement = fixture.nativeElement.querySelector('[data-testid="side-nav"]');

    expect(nav.textContent).toContain('Облік нагород ЧНУ');
    expect(nav.textContent).toContain('Чернівецький університет');
    expect(nav.querySelector('img')?.getAttribute('src')).toBe('brand/chnu/logo.svg');
  });

  it('ac5_keeps_the_side_menu_open_on_a_wide_screen', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="nav-toggle"]')).toBeNull();
    expect(element.querySelector('[data-testid="brand"]')).toBeNull();
    expect(element.querySelector('[data-testid="nav-home"]')?.textContent).toContain('Головна');
    expect(element.querySelector('mat-sidenav')?.classList).toContain('mat-drawer-opened');
  });

  it('ac5_opens_the_drawer_from_the_menu_button_and_closes_it_when_a_link_is_chosen', async () => {
    auth.isAuthenticated.set(true);
    layout.next({ matches: false, breakpoints: {} });
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    const drawer = () => element.querySelector('mat-sidenav') as HTMLElement;

    expect(drawer().classList).not.toContain('mat-drawer-opened');
    expect(element.querySelector('[data-testid="brand"]')?.textContent).toContain(
      'Облік нагород ЧНУ',
    );

    (element.querySelector('[data-testid="nav-toggle"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(drawer().classList).toContain('mat-drawer-opened');

    (element.querySelector('[data-testid="nav-home"]') as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(drawer().classList).not.toContain('mat-drawer-opened');
  });

  it('ac2_3_offers_the_privacy_centre_in_the_account_menu', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="user-menu"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(document.querySelector('[data-testid="nav-privacy"]')?.getAttribute('href')).toBe(
      '/privacy',
    );
  });

  it('ac4_switches_the_colour_scheme_from_the_user_menu', async () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="user-menu"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    (document.querySelector('[data-testid="color-scheme-menu"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    (document.querySelector('[data-testid="color-scheme-dark"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(document.documentElement.getAttribute('data-color-scheme')).toBe('dark');
  });

  it('ac4_8_shows_the_erasure_date_with_a_link_to_the_privacy_centre', () => {
    auth.isAuthenticated.set(true);
    privacy.erasure.set({
      id: 7,
      requestedAt: '2026-10-16T10:00:00Z',
      scheduledFor: '2026-11-15T10:00:00Z',
      postponed: false,
    });
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('[data-testid="erasure-banner"]');
    expect(privacy.summary).toHaveBeenCalled();
    expect(banner?.textContent).toContain('15.11.2026');
    expect(
      fixture.nativeElement
        .querySelector('[data-testid="erasure-banner-link"]')
        ?.getAttribute('href'),
    ).toBe('/privacy');

    privacy.erasure.set(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="erasure-banner"]')).toBeNull();
  });

  it('ac4_8_reads_no_erasure_for_an_anonymous_visitor', () => {
    auth.isAuthenticated.set(false);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(privacy.summary).not.toHaveBeenCalled();
    expect(privacy.forgetErasure).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-testid="erasure-banner"]')).toBeNull();
  });
});
