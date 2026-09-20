import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { AuthService } from './auth.service';
import { CallbackComponent } from './callback.component';

describe('CallbackComponent', () => {
  const auth = { isAuthenticated: signal(true), targetUrl: vi.fn().mockReturnValue('/awards/3') };
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };

  beforeEach(async () => {
    router.navigateByUrl.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        CallbackComponent,
        TranslocoTestingModule.forRoot({ langs: { uk: { app: { loading: 'Loading' } } } }),
      ],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
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
});
