import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ConnectivityService } from './connectivity.service';

describe('ConnectivityService', () => {
  let service: ConnectivityService;

  beforeEach(() => {
    service = TestBed.inject(ConnectivityService);
  });

  it('ac1_7_follows_the_connection_events_of_the_browser', () => {
    window.dispatchEvent(new Event('offline'));
    expect(service.online()).toBe(false);

    window.dispatchEvent(new Event('online'));
    expect(service.online()).toBe(true);
  });

  it('ac1_9_announces_a_return_of_the_connection_once', () => {
    let returns = 0;
    service.reconnected$.subscribe(() => returns++);

    service.reachable();
    service.unreachable();
    service.reachable();
    service.reachable();

    expect(returns).toBe(1);
  });

  it('ac2_9_a_server_without_answer_leaves_the_browser_link_up', () => {
    service.unreachable();

    expect(service.online()).toBe(false);
    expect(service.linked()).toBe(navigator.onLine);
  });

  it('ac2_9_a_late_answer_keeps_the_app_offline_while_the_browser_has_no_link', () => {
    const link = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    let returns = 0;
    service.reconnected$.subscribe(() => returns++);
    window.dispatchEvent(new Event('offline'));

    service.reachable();
    expect(service.online()).toBe(false);

    link.mockReturnValue(true);
    window.dispatchEvent(new Event('online'));
    expect(service.online()).toBe(true);
    expect(returns).toBe(1);
    link.mockRestore();
  });
});
