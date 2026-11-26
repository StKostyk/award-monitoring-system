import { TestBed } from '@angular/core/testing';
import { MatDatepickerIntl } from '@angular/material/datepicker';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';

import { TranslatedDatepickerIntl } from './translated-datepicker-intl';

describe('TranslatedDatepickerIntl', () => {
  let intl: MatDatepickerIntl;
  let transloco: TranslocoService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: {
            uk: { app: { datepicker: { open: 'Відкрити календар', close: 'Закрити календар' } } },
            en: { app: { datepicker: { open: 'Open calendar', close: 'Close calendar' } } },
          },
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'uk' },
        }),
      ],
      providers: [{ provide: MatDatepickerIntl, useClass: TranslatedDatepickerIntl }],
    });
    intl = TestBed.inject(MatDatepickerIntl);
    transloco = TestBed.inject(TranslocoService);
  });

  it('labels_the_calendar_toggle_in_the_active_language', () => {
    const changed = vi.fn();
    intl.changes.subscribe(changed);

    expect(intl.openCalendarLabel).toBe('Відкрити календар');
    expect(intl.closeCalendarLabel).toBe('Закрити календар');

    transloco.setActiveLang('en');

    expect(intl.openCalendarLabel).toBe('Open calendar');
    expect(changed).toHaveBeenCalled();
  });
});
