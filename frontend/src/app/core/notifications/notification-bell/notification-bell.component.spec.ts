import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { vi } from 'vitest';

import en from '../../../../../public/i18n/en.json';
import { LanguageService } from '../../i18n/language.service';
import { AppNotification, NotificationApi } from '../notification.api';
import { UnreadCount } from '../unread-count';
import { MENU_SIZE, NotificationBellComponent } from './notification-bell.component';

function item(id: number, read: boolean): AppNotification {
  return {
    id,
    type: 'AWARD_APPROVED',
    payload: { title: `Award ${id}`, reviewer: 'Аліна', level: 'DEAN' },
    referenceType: 'AWARD',
    referenceId: id + 100,
    read,
    sentAt: new Date().toISOString(),
  };
}

describe('NotificationBellComponent', () => {
  const count = signal(0);
  const unread = {
    count,
    badge: computed(() => (count() > 99 ? '99+' : count() === 0 ? '' : String(count()))),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
    refresh: vi.fn(),
  };
  const api = { list: vi.fn() };
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
    count.set(0);
    unread.markRead.mockReset().mockReturnValue(of(undefined));
    unread.markAllRead.mockReset().mockImplementation(() => {
      count.set(0);
      return of(2);
    });
    api.list.mockReset().mockReturnValue(of({ content: [item(1, false), item(2, true)] }));
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
        { provide: UnreadCount, useValue: unread },
        { provide: NotificationApi, useValue: api },
        { provide: LanguageService, useValue: { current: () => 'en' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
  });

  afterEach(() => document.querySelector('.cdk-overlay-container')?.replaceChildren());

  it('ac2_1_hides_the_badge_at_zero_and_names_the_count', () => {
    const badge = (): HTMLElement | null =>
      fixture.nativeElement.querySelector('[data-testid="notification-badge"]');

    expect(badge()).toBeNull();
    expect(bell().getAttribute('aria-label')).toBe('Notifications');

    count.set(120);
    fixture.detectChanges();

    expect(badge()?.textContent?.trim()).toBe('99+');
    expect(bell().getAttribute('aria-label')).toBe('Notifications, 99+ unread');
  });

  it('ac2_3_lists_the_latest_ten_with_unread_marked', async () => {
    await openMenu();

    expect(api.list).toHaveBeenCalledWith(0, MENU_SIZE);
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

    expect(unread.markRead).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
    expect(navigate).toHaveBeenCalledWith(['/awards', 101]);
  });

  it('ac2_5_mark_all_is_disabled_without_unread_and_empties_the_badge', async () => {
    api.list.mockReturnValue(of({ content: [item(2, true)] }));
    await openMenu();
    const markAll = (): HTMLButtonElement =>
      document.querySelector('[data-testid="notifications-mark-all"]') as HTMLButtonElement;

    expect(markAll().disabled).toBe(true);

    count.set(2);
    fixture.detectChanges();
    expect(markAll().disabled).toBe(false);

    markAll().click();
    fixture.detectChanges();

    expect(unread.markAllRead).toHaveBeenCalled();
    expect(count()).toBe(0);
  });

  it('links_to_the_full_page', async () => {
    await openMenu();

    expect(
      document.querySelector('[data-testid="notifications-see-all"]')?.getAttribute('href'),
    ).toBe('/notifications');
  });
});
