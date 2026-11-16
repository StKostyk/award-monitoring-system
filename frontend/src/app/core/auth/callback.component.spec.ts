import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { AuthService } from './auth.service';
import { CallbackComponent } from './callback.component';
import { SignInNoticeService } from './sign-in-notice.service';

describe('CallbackComponent', () => {
  const auth = { isAuthenticated: signal(true), targetUrl: vi.fn().mockReturnValue('/awards/3') };
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };
  const notice = { showPending: vi.fn() };

  beforeEach(async () => {
    router.navigateByUrl.mockClear();
    notice.showPending.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        CallbackComponent,
        TranslocoTestingModule.forRoot({ langs: { uk: { app: { loading: 'Loading' } } } }),
      ],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: SignInNoticeService, useValue: notice },
      ],
    }).compileComponents();
  });

  it('ac12 navigates to the url requested before login', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(CallbackComponent);
    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/awards/3');
  });

  it('falls back to the root when no token was obtained', () => {
    auth.isAuthenticated.set(false);
    const fixture = TestBed.createComponent(CallbackComponent);
    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('ac1_shows_the_notice_left_before_the_sign_in', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(CallbackComponent);
    fixture.detectChanges();

    expect(notice.showPending).toHaveBeenCalledTimes(1);
  });

  it('edge_keeps_the_notice_when_no_token_was_obtained', () => {
    auth.isAuthenticated.set(false);
    const fixture = TestBed.createComponent(CallbackComponent);
    fixture.detectChanges();

    expect(notice.showPending).not.toHaveBeenCalled();
  });
});
