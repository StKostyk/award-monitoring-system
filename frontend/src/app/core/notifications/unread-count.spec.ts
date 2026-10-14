import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../auth/auth.service';
import { AppNotification, NotificationApi } from './notification.api';
import { NOTIFICATION_POLL_MS, UnreadCount } from './unread-count';

const POLL_MS = 1000;

const approval: AppNotification = {
  id: 7,
  type: 'AWARD_APPROVED',
  payload: {},
  referenceType: 'AWARD',
  referenceId: 5,
  read: false,
  sentAt: '2026-10-14T09:00:00Z',
};

describe('UnreadCount', () => {
  const auth = { isAuthenticated: signal(true) };
  const api = {
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  };
  let visibility: DocumentVisibilityState;

  function create(): UnreadCount {
    const store = TestBed.inject(UnreadCount);
    TestBed.tick();
    return store;
  }

  function show(state: DocumentVisibilityState): void {
    visibility = state;
    document.dispatchEvent(new Event('visibilitychange'));
  }

  beforeEach(() => {
    vi.useFakeTimers();
    visibility = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    auth.isAuthenticated.set(true);
    api.unreadCount.mockReset().mockReturnValue(of(2));
    api.markRead.mockReset().mockReturnValue(of(undefined));
    api.markAllRead.mockReset().mockReturnValue(of(2));
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: NotificationApi, useValue: api },
        { provide: NOTIFICATION_POLL_MS, useValue: POLL_MS },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('ac2_2_fetches_at_start_and_on_every_interval', () => {
    const store = create();

    expect(store.count()).toBe(2);
    expect(api.unreadCount).toHaveBeenCalledTimes(1);

    api.unreadCount.mockReturnValue(of(4));
    vi.advanceTimersByTime(POLL_MS);

    expect(api.unreadCount).toHaveBeenCalledTimes(2);
    expect(store.count()).toBe(4);
  });

  it('ac2_2_fetches_nothing_while_hidden_and_once_on_return', () => {
    create();
    show('hidden');
    vi.advanceTimersByTime(POLL_MS * 5);

    expect(api.unreadCount).toHaveBeenCalledTimes(1);

    show('visible');

    expect(api.unreadCount).toHaveBeenCalledTimes(2);
  });

  it('ac2_2_fetches_nothing_while_signed_out_and_resumes_after_sign_in', () => {
    auth.isAuthenticated.set(false);
    const store = create();
    vi.advanceTimersByTime(POLL_MS * 3);
    show('visible');

    expect(api.unreadCount).not.toHaveBeenCalled();
    expect(store.count()).toBe(0);

    auth.isAuthenticated.set(true);
    TestBed.tick();

    expect(api.unreadCount).toHaveBeenCalledTimes(1);
    expect(store.count()).toBe(2);
  });

  it('ac2_2_stops_and_clears_the_badge_on_sign_out', () => {
    const store = create();
    auth.isAuthenticated.set(false);
    TestBed.tick();
    vi.advanceTimersByTime(POLL_MS * 3);

    expect(api.unreadCount).toHaveBeenCalledTimes(1);
    expect(store.count()).toBe(0);
  });

  it('ac2_2_stops_polling_on_401_until_the_tab_is_shown_again', () => {
    api.unreadCount.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    create();
    vi.advanceTimersByTime(POLL_MS * 3);

    expect(api.unreadCount).toHaveBeenCalledTimes(1);

    show('hidden');
    show('visible');

    expect(api.unreadCount).toHaveBeenCalledTimes(2);
  });

  it('keeps_polling_after_a_network_failure', () => {
    api.unreadCount.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    const store = create();
    vi.advanceTimersByTime(POLL_MS);

    expect(api.unreadCount).toHaveBeenCalledTimes(2);
    expect(store.count()).toBe(2);
  });

  it('ac2_1_shows_99_plus_above_99_and_nothing_at_0', () => {
    const store = create();

    store.count.set(0);
    expect(store.badge()).toBe('');
    store.count.set(99);
    expect(store.badge()).toBe('99');
    store.count.set(100);
    expect(store.badge()).toBe('99+');
  });

  it('ac2_4_marks_an_unread_item_and_lowers_the_count', () => {
    const store = create();
    api.unreadCount.mockReturnValue(of(1));
    store.markRead(approval).subscribe();

    expect(api.markRead).toHaveBeenCalledWith(7);
    expect(api.unreadCount).toHaveBeenCalledTimes(2);
    expect(store.count()).toBe(1);
  });

  it('ac2_5_a_poll_in_flight_does_not_bring_the_badge_back_after_mark_all', () => {
    const pending = new Subject<number>();
    const store = create();
    api.unreadCount.mockReturnValue(pending);
    vi.advanceTimersByTime(POLL_MS);
    store.markAllRead().subscribe();
    pending.next(2);

    expect(store.count()).toBe(0);
  });

  it('ac2_4_sends_nothing_for_a_read_item', () => {
    const store = create();
    store.markRead({ ...approval, read: true }).subscribe();

    expect(api.markRead).not.toHaveBeenCalled();
    expect(store.count()).toBe(2);
  });

  it('ac2_5_mark_all_empties_the_badge', () => {
    const store = create();
    store.markAllRead().subscribe();

    expect(store.count()).toBe(0);
    expect(store.badge()).toBe('');
  });
});
