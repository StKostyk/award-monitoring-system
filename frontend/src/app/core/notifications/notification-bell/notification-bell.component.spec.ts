import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import en from '../../../../../public/i18n/en.json';
import { AuthService } from '../../auth/auth.service';
import { LanguageService } from '../../i18n/language.service';
import { NotificationApi } from '../notification.api';
import { notificationItem } from '../notification.testing';
import { UnreadCount } from '../unread-count';
import { MENU_SIZE, NotificationBellComponent } from './notification-bell.component';

describe('NotificationBellComponent', () => {
  const api = {
    list: vi.fn(),
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  };
  let unread: UnreadCount;
  let fixture: ComponentFixture<NotificationBellComponent>;

  function bell(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="notification-bell"]');
  }

  async function openMenu(): Promise<void> {
    bell().click();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function menuItems(): HTMLButtonElement[] {
    return Array.from(document.querySelectorAll('[data-testid="notification-item"]'));
  }

  beforeEach(async () => {
    api.unreadCount.mockReset().mockReturnValue(of(0));
    api.markRead.mockReset().mockReturnValue(of(undefined));
    api.markAllRead.mockReset().mockReturnValue(of(2));
    api.list
      .mockReset()
      .mockReturnValue(of({ content: [notificationItem(1, false), notificationItem(2, true)] }));
    await TestBed.configureTestingModule({
      imports: [
        NotificationBellComponent,
        TranslocoTestingModule.forRoot({
          langs: { en },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: NotificationApi, useValue: api },
        { provide: AuthService, useValue: { isAuthenticated: signal(false) } },
        { provide: LanguageService, useValue: { current: () => 'en' } },
      ],
    }).compileComponents();
    unread = TestBed.inject(UnreadCount);
    vi.spyOn(unread, 'refresh');
    fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
  });

  afterEach(() => document.querySelector('.cdk-overlay-container')?.replaceChildren());

  it('ac2_1_hides_the_badge_at_zero_and_names_the_count', () => {
    const badge = (): HTMLElement | null =>
      fixture.nativeElement.querySelector('[data-testid="notification-badge"]');

    expect(badge()).toBeNull();
    expect(bell().getAttribute('aria-label')).toBe('Notifications');

    unread.count.set(120);
    fixture.detectChanges();

    expect(badge()?.textContent?.trim()).toBe('99+');
    expect(bell().getAttribute('aria-label')).toBe('Notifications, 99+ unread');
  });

  it('ac2_3_lists_the_latest_ten_with_unread_marked', async () => {
    await openMenu();

    expect(api.list).toHaveBeenCalledWith(0, MENU_SIZE, false);
    expect(unread.refresh).toHaveBeenCalled();
    const items = menuItems();
    expect(items.length).toBe(2);
    expect(items[0].classList).toContain('bell__item--unread');
    expect(items[0].textContent).toContain('«Award 1» approved by Аліна at the dean level');
    expect(items[0].textContent).toContain('now');
    expect(items[1].classList).not.toContain('bell__item--unread');
  });

  it('ac2_3_shows_the_empty_state', async () => {
    api.list.mockReturnValue(of({ content: [] }));
    await openMenu();

    expect(document.querySelector('[data-testid="notifications-empty"]')?.textContent).toContain(
      'No notifications yet',
    );
  });

  it('ac2_4_marks_the_item_read_and_opens_the_award', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await openMenu();
    menuItems()[0].click();

    expect(api.markRead).toHaveBeenCalledWith(1);
    expect(navigate).toHaveBeenCalledWith(['/awards', 101]);
  });

  it('ac2_5_mark_all_is_disabled_without_unread_and_empties_the_badge', async () => {
    api.list.mockReturnValue(of({ content: [notificationItem(2, true)] }));
    await openMenu();
    const markAll = (): HTMLButtonElement =>
      document.querySelector('[data-testid="notifications-mark-all"]') as HTMLButtonElement;

    expect(markAll().disabled).toBe(true);

    unread.count.set(2);
    fixture.detectChanges();
    expect(markAll().disabled).toBe(false);

    markAll().click();
    fixture.detectChanges();

    expect(api.markAllRead).toHaveBeenCalled();
    expect(unread.count()).toBe(0);
  });

  it('ac2_5_mark_all_keeps_the_menu_open_and_shows_the_items_read', async () => {
    await openMenu();
    (document.querySelector('[data-testid="notifications-mark-all"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(menuItems().length).toBe(2);
    expect(menuItems().some((element) => element.classList.contains('bell__item--unread'))).toBe(
      false,
    );
  });

  it('says_so_when_mark_all_fails', async () => {
    api.markAllRead.mockReturnValue(throwError(() => new Error('offline')));
    await openMenu();
    (document.querySelector('[data-testid="notifications-mark-all"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(
      document.querySelector('[data-testid="notifications-action-failed"]')?.textContent,
    ).toContain('could not be updated');
    expect(menuItems()[0].classList).toContain('bell__item--unread');
  });

  it('links_to_the_full_page', async () => {
    await openMenu();

    expect(
      document.querySelector('[data-testid="notifications-see-all"]')?.getAttribute('href'),
    ).toBe('/notifications');
  });
});
