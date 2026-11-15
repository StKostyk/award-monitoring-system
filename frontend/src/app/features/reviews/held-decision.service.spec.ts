import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationStart, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { ConnectivityService } from '../../core/connectivity/connectivity.service';
import { HOLD_MILLIS, HeldDecisionService, ReturnedDecision } from './held-decision.service';
import { DecisionRequest, ReviewsService } from './reviews.service';

const approve: DecisionRequest = { decision: 'APPROVE', requestVersion: 3, verified: true };
const outcome = { awardId: 5, status: 'APPROVED', requestStatus: 'APPROVED', level: 'RECTOR' };

function problem(status: number, type?: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: type ? { type: `urn:awards:problem:${type}`, reviewer: { id: 2, name: 'Олена' } } : null,
  });
}

describe('HeldDecisionService', () => {
  let service: HeldDecisionService;
  let returned: ReturnedDecision[];
  let action: Subject<void>;
  let signOut: () => Promise<void>;
  const events = new Subject<unknown>();
  const reconnected = new Subject<void>();
  const online = signal(true);
  const reviews = { decide: vi.fn() };
  const snackBar = { open: vi.fn() };
  const auth = {
    accessToken: signal<string | null>('token-1'),
    userId: signal<string | null>('21'),
    beforeSignOut: (hook: () => Promise<void>) => (signOut = hook),
  };

  beforeEach(() => {
    vi.useFakeTimers();
    reviews.decide.mockReset();
    reviews.decide.mockReturnValue(of(outcome));
    snackBar.open.mockReset();
    snackBar.open.mockImplementation(() => {
      action = new Subject<void>();
      return { onAction: () => action, dismiss: vi.fn() };
    });
    online.set(true);
    auth.userId.set('21');
    TestBed.configureTestingModule({
      providers: [
        { provide: ReviewsService, useValue: reviews },
        { provide: AuthService, useValue: auth },
        { provide: ConnectivityService, useValue: { online, reconnected$: reconnected } },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: Router, useValue: { events } },
        {
          provide: TranslocoService,
          useValue: {
            translate: (key: string, params?: object) =>
              `${key}${params ? JSON.stringify(params) : ''}`,
          },
        },
      ],
    });
    service = TestBed.inject(HeldDecisionService);
    returned = [];
    service.returned.subscribe((item) => returned.push(item));
  });

  afterEach(() => vi.useRealTimers());

  it('ac6_4_sends_the_decision_only_when_the_ten_seconds_end', async () => {
    service.hold(5, approve);

    expect(snackBar.open).toHaveBeenCalledWith('reviews.queue.held', 'reviews.queue.undo', {
      duration: HOLD_MILLIS,
    });
    expect(service.heldIds().has(5)).toBe(true);
    await vi.advanceTimersByTimeAsync(HOLD_MILLIS - 1);
    expect(reviews.decide).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1);
    expect(reviews.decide).toHaveBeenCalledWith(5, approve);
    expect(service.heldIds().size).toBe(0);
  });

  it('ac6_4_undo_within_the_hold_puts_the_request_back_and_sends_nothing', async () => {
    service.hold(5, approve);

    action.next();
    await vi.advanceTimersByTimeAsync(HOLD_MILLIS);

    expect(reviews.decide).not.toHaveBeenCalled();
    expect(returned).toEqual([{ awardId: 5, problem: null, params: {} }]);
  });

  it('edge_two_decisions_are_held_and_sent_independently', async () => {
    service.hold(5, approve);
    await vi.advanceTimersByTimeAsync(4000);
    service.hold(6, { ...approve, requestVersion: 1 });
    await vi.advanceTimersByTimeAsync(6000);

    expect(reviews.decide).toHaveBeenCalledTimes(1);
    expect(reviews.decide).toHaveBeenCalledWith(5, approve);
    await vi.advanceTimersByTimeAsync(4000);
    expect(reviews.decide).toHaveBeenLastCalledWith(6, { ...approve, requestVersion: 1 });
  });

  it('ac6_5_navigation_within_the_app_sends_at_once', async () => {
    service.hold(5, approve);

    events.next(new NavigationStart(1, '/awards'));
    await vi.advanceTimersByTimeAsync(0);

    expect(reviews.decide).toHaveBeenCalledWith(5, approve);
  });

  it('ac6_5_sign_out_waits_for_the_held_decisions', async () => {
    const answer = new Subject<typeof outcome>();
    reviews.decide.mockReturnValue(answer);
    service.hold(5, approve);
    let done = false;

    void signOut().then(() => (done = true));
    await vi.advanceTimersByTimeAsync(0);
    expect(reviews.decide).toHaveBeenCalled();
    expect(done).toBe(false);

    answer.next(outcome);
    answer.complete();
    await vi.advanceTimersByTimeAsync(0);
    expect(done).toBe(true);
  });

  it('ac6_5_a_closing_tab_sends_with_keepalive_and_the_bearer_token', () => {
    const fetch = vi.fn(() => Promise.resolve(new Response()));
    vi.stubGlobal('fetch', fetch);
    service.hold(5, approve);

    window.dispatchEvent(new Event('pagehide'));

    expect(fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/awards\/5\/decisions$/),
      expect.objectContaining({
        method: 'POST',
        keepalive: true,
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer token-1' },
        body: JSON.stringify(approve),
      }),
    );
    expect(service.heldIds().size).toBe(0);
    vi.unstubAllGlobals();
  });

  it('edge_a_decision_is_never_sent_with_another_accounts_token', async () => {
    online.set(false);
    service.hold(5, approve);
    await vi.advanceTimersByTimeAsync(HOLD_MILLIS);

    auth.userId.set('30');
    reconnected.next();
    await vi.advanceTimersByTimeAsync(0);

    expect(reviews.decide).not.toHaveBeenCalled();
  });

  it('edge_a_page_kept_for_back_and_forward_keeps_the_hold', () => {
    const fetch = vi.fn(() => Promise.resolve(new Response()));
    vi.stubGlobal('fetch', fetch);
    service.hold(5, approve);

    const kept = new Event('pagehide') as PageTransitionEvent;
    Object.defineProperty(kept, 'persisted', { value: true });
    window.dispatchEvent(kept);

    expect(fetch).not.toHaveBeenCalled();
    expect(service.heldIds().has(5)).toBe(true);
    vi.unstubAllGlobals();
  });

  it('ac6_6_a_conflict_after_the_hold_brings_the_request_back_with_its_message', async () => {
    reviews.decide.mockReturnValue(throwError(() => problem(409, 'request-claimed')));
    service.hold(5, approve);

    await vi.advanceTimersByTimeAsync(HOLD_MILLIS);

    expect(returned).toEqual([
      { awardId: 5, problem: 'reviews.problems.request-claimed', params: { name: 'Олена' } },
    ]);
    expect(snackBar.open).toHaveBeenLastCalledWith(
      'reviews.problems.request-claimed{"name":"Олена"}',
      undefined,
      { duration: HOLD_MILLIS },
    );
  });

  it('ac6_6_a_revoked_role_brings_the_request_back_as_denied', async () => {
    reviews.decide.mockReturnValue(throwError(() => problem(403, 'access-denied')));
    service.hold(5, approve);

    await vi.advanceTimersByTimeAsync(HOLD_MILLIS);

    expect(returned[0].problem).toBe('reviews.queue.denied');
  });

  it('edge_offline_waits_for_the_connection_and_a_lost_one_brings_the_request_back', async () => {
    online.set(false);
    service.hold(5, approve);
    await vi.advanceTimersByTimeAsync(HOLD_MILLIS);
    expect(reviews.decide).not.toHaveBeenCalled();
    expect(snackBar.open).toHaveBeenLastCalledWith('reviews.queue.waiting');

    reviews.decide.mockReturnValue(throwError(() => problem(0)));
    reconnected.next();
    await vi.advanceTimersByTimeAsync(0);

    expect(reviews.decide).toHaveBeenCalledWith(5, approve);
    expect(returned[0].problem).toBe('reviews.queue.offline');
  });
});
