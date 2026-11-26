import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { ConnectivityService } from './connectivity.service';
import { OfflineNoticeComponent } from './offline-notice.component';

const NOTICE =
  "Немає з'єднання з сервером. Сторінки, яким потрібні дані, відкриються, щойно з'єднання відновиться.";

describe('OfflineNoticeComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        OfflineNoticeComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: { app: { offlineNotice: NOTICE } } },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
    });
  });

  it('ac1_7_ac1_9_shows_the_notice_only_while_the_server_cannot_be_reached', () => {
    const connectivity = TestBed.inject(ConnectivityService);
    const fixture = TestBed.createComponent(OfflineNoticeComponent);
    const notice = () =>
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="offline-notice"]');
    fixture.detectChanges();
    expect(notice()).toBeNull();

    connectivity.unreachable();
    fixture.detectChanges();
    expect(notice()?.textContent).toContain(NOTICE);
    expect(notice()?.getAttribute('role')).toBe('status');

    connectivity.reachable();
    fixture.detectChanges();
    expect(notice()).toBeNull();
  });
});
