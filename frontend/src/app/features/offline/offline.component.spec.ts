import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { ConnectivityService } from '../../core/connectivity/connectivity.service';
import { OfflineComponent } from './offline.component';

describe('OfflineComponent', () => {
  const auth = { login: vi.fn() };

  function create(next: string | null) {
    auth.login.mockClear();
    TestBed.configureTestingModule({
      imports: [
        OfflineComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              offline: {
                title: "Немає з'єднання",
                text: "Увійти можна, щойно з'єднання відновиться.",
                retry: 'Спробувати ще раз',
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(next ? { next } : {}) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(OfflineComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ac1_8_says_there_is_no_connection_and_offers_to_try_again', () => {
    const page = create('/awards');

    expect(page.querySelector('h1')?.textContent).toContain("Немає з'єднання");
    expect(page.textContent).toContain("Увійти можна, щойно з'єднання відновиться.");
    page.querySelector<HTMLButtonElement>('[data-testid="offline-retry"]')?.click();

    expect(auth.login).toHaveBeenCalledWith('/awards');
  });

  it('ac1_8_starts_the_sign_in_when_the_connection_returns', () => {
    create('/awards/new');
    const connectivity = TestBed.inject(ConnectivityService);

    connectivity.unreachable();
    connectivity.reachable();

    expect(auth.login).toHaveBeenCalledWith('/awards/new');
  });

  for (const target of ['//evil.example/', '/\\evil.example', 'https://evil.example/']) {
    it(`ac1_8_signs_in_to_the_start_page_for_the_foreign_target_${target}`, () => {
      const page = create(target);

      page.querySelector<HTMLButtonElement>('[data-testid="offline-retry"]')?.click();

      expect(auth.login).toHaveBeenCalledWith('/');
    });
  }
});
