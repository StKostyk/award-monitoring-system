import { TestBed } from '@angular/core/testing';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';

import { LanguageService } from './language.service';

describe('LanguageService', () => {
  let service: LanguageService;
  let transloco: TranslocoService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { uk: {}, en: {} },
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'uk' },
        }),
      ],
    });
    service = TestBed.inject(LanguageService);
    transloco = TestBed.inject(TranslocoService);
  });

  it('ac17 starts in Ukrainian and toggles to English, remembering the choice', () => {
    service.init();
    expect(service.current()).toBe('uk');

    service.toggle();

    expect(transloco.getActiveLang()).toBe('en');
    expect(localStorage.getItem('lang')).toBe('en');
    service.toggle();
    expect(service.current()).toBe('uk');
  });

  it('ac17 restores the stored language on start', () => {
    localStorage.setItem('lang', 'en');

    service.init();

    expect(service.current()).toBe('en');
  });
});
