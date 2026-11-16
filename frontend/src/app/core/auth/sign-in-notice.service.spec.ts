import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { afterEach, beforeEach, vi } from 'vitest';

import { AuthService } from './auth.service';
import { SignInNoticeService } from './sign-in-notice.service';

describe('SignInNoticeService', () => {
  let service: SignInNoticeService;
  const snackBar = { open: vi.fn() };
  const auth = { userId: signal<string | null>('21') };

  beforeEach(() => {
    sessionStorage.clear();
    snackBar.open.mockReset();
    auth.userId.set('21');
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: MatSnackBar, useValue: snackBar },
        {
          provide: TranslocoService,
          useValue: {
            selectTranslate: (key: string, params?: object) =>
              of(`${key}${params ? JSON.stringify(params) : ''}`),
          },
        },
      ],
    });
    service = TestBed.inject(SignInNoticeService);
  });

  afterEach(() => vi.restoreAllMocks());

  it('ac1_a_notice_left_before_sign_in_shows_once_to_the_same_account', () => {
    service.leave('21', 'reviews.queue.signedOut', { id: '5' });

    service.showPending();
    service.showPending();

    expect(snackBar.open).toHaveBeenCalledTimes(1);
    expect(snackBar.open).toHaveBeenCalledWith(
      'reviews.queue.signedOut{"id":"5"}',
      'reviews.queue.gotIt',
    );
  });

  it('edge_another_account_never_sees_the_notice', () => {
    service.leave('21', 'reviews.queue.signedOut', { id: '5' });
    auth.userId.set('34');

    service.showPending();
    auth.userId.set('21');
    service.showPending();

    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('edge_nothing_left_shows_nothing', () => {
    service.showPending();

    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('edge_unavailable_storage_drops_the_notice_quietly', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    expect(() => service.leave('21', 'reviews.queue.signedOut', { id: '5' })).not.toThrow();
    expect(() => service.showPending()).not.toThrow();
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('edge_a_damaged_note_is_dropped', () => {
    sessionStorage.setItem('award.signInNotice', '{not json');

    service.showPending();

    expect(snackBar.open).not.toHaveBeenCalled();
    expect(sessionStorage.getItem('award.signInNotice')).toBeNull();
  });
});
