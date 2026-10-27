import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SwUpdate, VersionEvent } from '@angular/service-worker';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import {
  RELOADED_KEY,
  UPDATE_CHECK_INTERVAL_MS,
  UpdatePromptService,
} from './update-prompt.service';

const AVAILABLE = 'Доступна нова версія застосунку.';

describe('UpdatePromptService', () => {
  let versionUpdates: Subject<VersionEvent>;
  let unrecoverable: Subject<{ type: 'UNRECOVERABLE_STATE'; reason: string }>;
  let updates: {
    isEnabled: boolean;
    versionUpdates: Subject<VersionEvent>;
    unrecoverable: Subject<{ type: 'UNRECOVERABLE_STATE'; reason: string }>;
    checkForUpdate: ReturnType<typeof vi.fn>;
    activateUpdate: ReturnType<typeof vi.fn>;
  };
  let action: Subject<void>;
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let service: UpdatePromptService;
  let reload: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(() => {
    sessionStorage.removeItem(RELOADED_KEY);
    versionUpdates = new Subject();
    unrecoverable = new Subject();
    updates = {
      isEnabled: true,
      versionUpdates,
      unrecoverable,
      checkForUpdate: vi.fn().mockResolvedValue(false),
      activateUpdate: vi.fn().mockResolvedValue(true),
    };
    action = new Subject();
    snackBar = { open: vi.fn().mockReturnValue({ onAction: () => action }) };
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { uk: { app: { update: { available: AVAILABLE, action: 'Оновити' } } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
          preloadLangs: true,
        }),
      ],
      providers: [
        { provide: SwUpdate, useValue: updates },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    service = TestBed.inject(UpdatePromptService);
    reload = vi.fn<() => void>();
    service.reload = reload;
  });

  function versionReady(): void {
    versionUpdates.next({
      type: 'VERSION_READY',
      currentVersion: { hash: 'n' },
      latestVersion: { hash: 'n1' },
    });
  }

  it('ac1_4_does_nothing_without_a_service_worker', () => {
    updates.isEnabled = false;

    service.init();
    versionReady();

    expect(updates.checkForUpdate).not.toHaveBeenCalled();
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('ac1_10_offers_a_downloaded_version_and_reloads_on_update', async () => {
    service.init();
    versionReady();

    expect(snackBar.open).toHaveBeenCalledWith(AVAILABLE, 'Оновити');
    expect(reload).not.toHaveBeenCalled();

    action.next();
    await Promise.resolve();
    await Promise.resolve();

    expect(updates.activateUpdate).toHaveBeenCalled();
    expect(reload).toHaveBeenCalled();
  });

  it('ac1_10_ignores_other_version_events', () => {
    service.init();
    versionUpdates.next({ type: 'VERSION_DETECTED', version: { hash: 'n1' } });

    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('ac1_10_checks_at_start_when_the_tab_becomes_visible_and_every_6_hours', () => {
    vi.useFakeTimers();
    try {
      service.init();
      expect(updates.checkForUpdate).toHaveBeenCalledTimes(1);

      document.dispatchEvent(new Event('visibilitychange'));
      expect(updates.checkForUpdate).toHaveBeenCalledTimes(2);

      vi.advanceTimersByTime(UPDATE_CHECK_INTERVAL_MS);
      expect(updates.checkForUpdate).toHaveBeenCalledTimes(3);
    } finally {
      vi.useRealTimers();
    }
  });

  it('ac1_10_reloads_on_an_unrecoverable_state_and_keeps_the_reason_for_the_reloaded_page', () => {
    service.init();
    unrecoverable.next({ type: 'UNRECOVERABLE_STATE', reason: 'hash mismatch' });

    expect(reload).toHaveBeenCalled();
    expect(sessionStorage.getItem(RELOADED_KEY)).not.toBeNull();
  });

  it('ac1_10_shows_the_notice_once_after_a_forced_reload', () => {
    sessionStorage.setItem(RELOADED_KEY, '1');

    service.init();

    expect(snackBar.open).toHaveBeenCalledWith(AVAILABLE, undefined, { duration: 6000 });
    expect(sessionStorage.getItem(RELOADED_KEY)).toBeNull();
  });
});
