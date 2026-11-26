import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import en from '../../../../../public/i18n/en.json';
import { LanguageService } from '../../../core/i18n/language.service';
import { AppNotification, NotificationApi } from '../../../core/notifications/notification.api';
import { notificationItem } from '../../../core/notifications/notification.testing';
import { UnreadCount } from '../../../core/notifications/unread-count';
import {
  NOTIFICATION_GROUPS,
  NotificationListComponent,
  PAGE_SIZE,
} from './notification-list.component';

function item(id: number, read: boolean): AppNotification {
  return notificationItem(id, read, {
    type: 'ROLE_ASSIGNED',
    payload: { role: 'DEAN', organization: 'Faculty', organizationUk: 'Факультет', validTo: null },
    referenceType: null,
    referenceId: null,
    sentAt: '2026-10-14T09:00:00Z',
  });
}

describe('NotificationListComponent', () => {
  const query = new BehaviorSubject(convertToParamMap({}));
  const count = signal(1);
  const unread = { count, markRead: vi.fn(), markUnread: vi.fn(), markAllRead: vi.fn() };
  const api = { list: vi.fn() };
  let fixture: ComponentFixture<NotificationListComponent>;
  let navigate: ReturnType<typeof vi.spyOn>;

  function element(): HTMLElement {
    return fixture.nativeElement;
  }

  function byTestId(id: string): HTMLElement | null {
    return element().querySelector(`[data-testid="${id}"]`);
  }

  function rows(): HTMLButtonElement[] {
    return Array.from(element().querySelectorAll('[data-testid="notifications-list-item"]'));
  }

  function show(params: Record<string, string>): void {
    query.next(convertToParamMap(params));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    query.next(convertToParamMap({}));
    count.set(1);
    unread.markRead.mockReset().mockReturnValue(of(undefined));
    unread.markUnread.mockReset().mockReturnValue(of(undefined));
    unread.markAllRead.mockReset().mockImplementation(() => {
      count.set(0);
      return of(1);
    });
    api.list
      .mockReset()
      .mockReturnValue(of({ content: [item(1, false), item(2, true)], totalElements: 45 }));
    await TestBed.configureTestingModule({
      imports: [
        NotificationListComponent,
        TranslocoTestingModule.forRoot({
          langs: { en },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: query } },
        { provide: UnreadCount, useValue: unread },
        { provide: NotificationApi, useValue: api },
        { provide: LanguageService, useValue: { current: () => 'en' } },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(NotificationListComponent);
    fixture.detectChanges();
  });

  it('ac7_3_lists_the_page_beside_an_empty_reading_pane_with_counted_chips', () => {
    expect(api.list).toHaveBeenCalledWith(0, PAGE_SIZE, false, []);
    expect(rows().length).toBe(2);
    expect(rows()[0].classList).toContain('notifications__row--unread');
    expect(rows()[0].textContent).toContain('You have been given the role “Dean”: Faculty');
    expect(byTestId('notifications-filter-all')?.textContent).toContain('All · 45');
    expect(byTestId('notifications-filter-unread')?.textContent).toContain('Unread · 1');
    expect(byTestId('notifications-filter-roles')?.textContent).toContain('Roles');
    expect(byTestId('notification-pane-hint')).not.toBeNull();
    expect(byTestId('notifications-paginator')).not.toBeNull();
  });

  it('ac7_2_a_group_chip_asks_for_its_types_and_counts_all_on_the_side', () => {
    api.list.mockReturnValue(of({ content: [item(1, false)], totalElements: 1 }));
    show({ filter: 'roles' });

    expect(api.list).toHaveBeenCalledWith(0, PAGE_SIZE, false, NOTIFICATION_GROUPS.roles);
    expect(api.list).toHaveBeenCalledWith(0, 1, false, []);
    expect(byTestId('notifications-filter-roles')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('ac7_2_the_unread_chip_asks_for_unread_only', () => {
    show({ filter: 'unread', page: '2' });

    expect(api.list).toHaveBeenCalledWith(2, PAGE_SIZE, true, []);
  });

  it('ac7_2_a_chip_keeps_its_filter_in_the_query_string_and_drops_the_selection', () => {
    byTestId('notifications-filter-awards')?.click();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { filter: 'awards', page: null, selected: null } }),
    );
  });

  it('ac7_2_an_unknown_filter_reads_as_all', () => {
    show({ filter: 'bogus' });

    expect(api.list).toHaveBeenLastCalledWith(0, PAGE_SIZE, false, []);
  });

  it('ac7_4_selecting_keeps_the_page_and_puts_the_id_in_the_query_string', () => {
    rows()[0].click();

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { selected: 1 }, queryParamsHandling: 'merge' }),
    );
  });

  it('ac7_4_a_chosen_notification_opens_in_the_pane_and_is_read_once', () => {
    rows()[0].click();
    show({ selected: '1' });

    expect(unread.markRead).toHaveBeenCalledTimes(1);
    expect(unread.markRead).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
    expect(byTestId('notification-pane-text')?.textContent).toContain(
      'You have been given the role',
    );
    expect(rows()[0].classList).not.toContain('notifications__row--unread');
    expect(rows()[0].getAttribute('aria-current')).toBeTruthy();
    expect(api.list).toHaveBeenCalledTimes(1);
  });

  it('ac7_4_a_selection_restored_from_the_url_keeps_its_read_state', () => {
    show({ selected: '1' });

    expect(byTestId('notification-pane-state')?.textContent).toContain('Unread');
    expect(unread.markRead).not.toHaveBeenCalled();
  });

  it('ac7_4_leaving_the_selection_reloads_the_list', () => {
    show({ selected: '1' });
    show({});

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('ac7_4_only_the_pane_button_navigates', () => {
    show({ selected: '1' });
    const open = byTestId('notification-pane-open') as HTMLButtonElement;

    expect(open.textContent).toContain('Go to the profile');
    open.click();

    expect(navigate).toHaveBeenCalledWith(['/profile']);
  });

  it('ac7_4_an_award_notification_opens_the_award', () => {
    api.list.mockReturnValue(of({ content: [notificationItem(3, true)], totalElements: 1 }));
    fixture.componentInstance.load();
    show({ selected: '3' });
    (byTestId('notification-pane-open') as HTMLButtonElement).click();

    expect(byTestId('notification-pane-open')?.textContent).toContain('Open the award');
    expect(navigate).toHaveBeenCalledWith(['/awards', 103]);
  });

  it('ac7_4_a_notification_without_a_target_keeps_its_text_and_hides_the_button', () => {
    api.list.mockReturnValue(
      of({ content: [notificationItem(3, true, { referenceId: null })], totalElements: 1 }),
    );
    fixture.componentInstance.load();
    show({ selected: '3' });

    expect(byTestId('notification-pane-text')).not.toBeNull();
    expect(byTestId('notification-pane-open')).toBeNull();
  });

  it('ac7_4_mark_unread_turns_it_unread_without_reading_it_again', () => {
    show({ selected: '2' });
    const calls = unread.markRead.mock.calls.length;
    (byTestId('notification-pane-unread') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(unread.markUnread).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
    expect(rows()[1].classList).toContain('notifications__row--unread');
    expect(byTestId('notification-pane-unread')).toBeNull();
    expect(unread.markRead.mock.calls.length).toBe(calls);
  });

  it('ac7_4_an_unread_notification_offers_no_mark_unread', () => {
    unread.markRead.mockReturnValue(new Subject<void>());
    rows()[0].click();
    show({ selected: '1' });

    expect(byTestId('notification-pane-text')).not.toBeNull();
    expect(byTestId('notification-pane-unread')).toBeNull();
  });

  it('ac7_4_a_selection_outside_the_list_shows_not_found_and_keeps_the_list', () => {
    show({ selected: '99' });

    expect(byTestId('notification-pane-not-found')?.textContent).toContain(
      'Notification not found',
    );
    expect(rows().length).toBe(2);
    expect(unread.markRead).not.toHaveBeenCalled();
  });

  it('ac7_4_says_so_when_reading_fails', () => {
    unread.markRead.mockReturnValue(throwError(() => new Error('offline')));
    rows()[0].click();
    show({ selected: '1' });

    expect(byTestId('notifications-action-failed')).not.toBeNull();
    expect(rows()[0].classList).toContain('notifications__row--unread');
    expect(navigate).not.toHaveBeenCalledWith(['/profile']);
  });

  it('ac7_5_back_returns_to_the_list', () => {
    show({ selected: '1' });
    (byTestId('notifications-back') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { selected: null }, queryParamsHandling: 'merge' }),
    );
  });

  it('ac2_6_moves_a_page_past_the_end_to_the_last_page', () => {
    api.list.mockReturnValue(of({ content: [], totalElements: 45 }));
    show({ filter: 'unread', page: '999' });

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { filter: 'unread', page: 2, selected: null },
        replaceUrl: true,
      }),
    );
    expect(byTestId('notifications-page-empty')).toBeNull();
  });

  it('ac2_6_moves_a_page_past_the_end_of_one_page_to_the_first', () => {
    api.list.mockReturnValue(of({ content: [], totalElements: 12 }));
    show({ page: '999' });

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { filter: null, page: null, selected: null },
        replaceUrl: true,
      }),
    );
  });

  it('ac2_6_stays_on_the_last_page_when_it_comes_back_empty', () => {
    api.list.mockReturnValue(of({ content: [], totalElements: 45 }));
    show({ page: '2' });

    expect(navigate).not.toHaveBeenCalled();
    expect(byTestId('notifications-page-empty')).not.toBeNull();
  });

  it('ac2_6_asks_for_at_most_the_highest_page_the_api_binds', () => {
    query.next(convertToParamMap({ page: '99999999999' }));

    expect(api.list).toHaveBeenLastCalledWith(2_147_483_647, PAGE_SIZE, false, []);
  });

  it('ac2_6_shows_the_empty_state_of_the_filter', () => {
    api.list.mockReturnValue(of({ content: [], totalElements: 0 }));
    show({ filter: 'unread' });

    expect(byTestId('notifications-page-empty')?.textContent).toContain('No unread notifications');
    show({ filter: 'review' });

    expect(byTestId('notifications-page-empty')?.textContent).toContain(
      'No notifications in this group',
    );
    expect(byTestId('notifications-paginator')).toBeNull();
  });

  it('ac2_5_marks_all_read_and_reloads', () => {
    const button = (): HTMLButtonElement =>
      byTestId('notifications-page-mark-all') as HTMLButtonElement;
    api.list.mockReturnValue(of({ content: [item(1, true)], totalElements: 1 }));
    button().click();
    fixture.detectChanges();

    expect(unread.markAllRead).toHaveBeenCalled();
    expect(api.list).toHaveBeenCalledTimes(2);
    expect(button().disabled).toBe(true);
  });

  it('ac2_5_says_so_when_mark_all_fails_and_keeps_the_list', () => {
    unread.markAllRead.mockReturnValue(throwError(() => new Error('down')));
    (byTestId('notifications-page-mark-all') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(byTestId('notifications-action-failed')?.textContent).toContain(
      'The notification could not be updated',
    );
    expect(rows().length).toBe(2);
  });

  it('offers_a_retry_when_loading_fails', () => {
    api.list.mockReturnValue(throwError(() => new Error('down')));
    show({ page: '1' });
    const alert = byTestId('notifications-load-failed') as HTMLElement;

    expect(alert.textContent).toContain('Notifications could not be loaded');

    api.list.mockReturnValue(of({ content: [item(3, false)], totalElements: 1 }));
    (alert.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(rows().length).toBe(1);
  });
});
