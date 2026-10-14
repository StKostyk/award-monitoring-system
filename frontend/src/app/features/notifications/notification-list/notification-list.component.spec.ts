import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import en from '../../../../../public/i18n/en.json';
import { LanguageService } from '../../../core/i18n/language.service';
import { AppNotification, NotificationApi } from '../../../core/notifications/notification.api';
import { UnreadCount } from '../../../core/notifications/unread-count';
import { NotificationListComponent, PAGE_SIZE } from './notification-list.component';

function item(id: number, read: boolean): AppNotification {
  return {
    id,
    type: 'ROLE_ASSIGNED',
    payload: { role: 'DEAN', organization: 'Faculty', organizationUk: 'Факультет', validTo: null },
    referenceType: null,
    referenceId: null,
    read,
    sentAt: '2026-10-14T09:00:00Z',
  };
}

describe('NotificationListComponent', () => {
  const query = new BehaviorSubject(convertToParamMap({}));
  const count = signal(1);
  const unread = { count, markRead: vi.fn(), markAllRead: vi.fn() };
  const api = { list: vi.fn() };
  let fixture: ComponentFixture<NotificationListComponent>;
  let navigate: ReturnType<typeof vi.spyOn>;

  function element(): HTMLElement {
    return fixture.nativeElement;
  }

  function rows(): HTMLButtonElement[] {
    return Array.from(element().querySelectorAll('[data-testid="notifications-list-item"]'));
  }

  beforeEach(async () => {
    query.next(convertToParamMap({}));
    count.set(1);
    unread.markRead.mockReset().mockReturnValue(of(undefined));
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

  it('ac2_6_lists_twenty_per_page_with_a_paginator', () => {
    expect(api.list).toHaveBeenCalledWith(0, PAGE_SIZE, false);
    expect(rows().length).toBe(2);
    expect(rows()[0].classList).toContain('notifications__item--unread');
    expect(rows()[0].textContent).toContain('You have been given the role “Dean”: Faculty');
    expect(element().querySelector('[data-testid="notifications-paginator"]')).not.toBeNull();
  });

  it('ac2_6_reads_the_filter_and_page_from_the_query_string', () => {
    query.next(convertToParamMap({ unread: 'true', page: '2' }));
    fixture.detectChanges();

    expect(api.list).toHaveBeenLastCalledWith(2, PAGE_SIZE, true);
    const toggle = element().querySelector('[data-testid="notifications-unread-only"] button');
    expect(toggle?.getAttribute('aria-checked')).toBe('true');
  });

  it('ac2_6_keeps_the_toggle_in_the_query_string', () => {
    (
      element().querySelector('[data-testid="notifications-unread-only"] button') as HTMLElement
    ).click();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { unread: true, page: null } }),
    );
  });

  it('ac2_6_shows_the_unread_empty_state', () => {
    api.list.mockReturnValue(of({ content: [], totalElements: 0 }));
    query.next(convertToParamMap({ unread: 'true' }));
    fixture.detectChanges();

    expect(
      element().querySelector('[data-testid="notifications-page-empty"]')?.textContent,
    ).toContain('No unread notifications');
    expect(element().querySelector('[data-testid="notifications-paginator"]')).toBeNull();
  });

  it('ac2_4_marks_the_item_read_and_opens_the_profile', () => {
    rows()[0].click();
    fixture.detectChanges();

    expect(unread.markRead).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
    expect(navigate).toHaveBeenCalledWith(['/profile']);
    expect(rows()[0].classList).not.toContain('notifications__item--unread');
  });

  it('ac2_5_marks_all_read_and_reloads', () => {
    const button = (): HTMLButtonElement =>
      element().querySelector('[data-testid="notifications-page-mark-all"]') as HTMLButtonElement;
    api.list.mockReturnValue(of({ content: [item(1, true)], totalElements: 1 }));
    button().click();
    fixture.detectChanges();

    expect(unread.markAllRead).toHaveBeenCalled();
    expect(api.list).toHaveBeenCalledTimes(2);
    expect(button().disabled).toBe(true);
  });

  it('ac2_5_says_so_when_mark_all_fails_and_keeps_the_list', () => {
    unread.markAllRead.mockReturnValue(throwError(() => new Error('down')));
    (
      element().querySelector('[data-testid="notifications-page-mark-all"]') as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(
      element().querySelector('[data-testid="notifications-action-failed"]')?.textContent,
    ).toContain('The notification could not be updated');
    expect(rows().length).toBe(2);
  });

  it('offers_a_retry_when_loading_fails', () => {
    api.list.mockReturnValue(throwError(() => new Error('down')));
    query.next(convertToParamMap({ page: '1' }));
    fixture.detectChanges();
    const alert = element().querySelector('[role="alert"]') as HTMLElement;

    expect(alert.textContent).toContain('Notifications could not be loaded');

    api.list.mockReturnValue(of({ content: [item(3, false)], totalElements: 1 }));
    (alert.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(rows().length).toBe(1);
  });
});
