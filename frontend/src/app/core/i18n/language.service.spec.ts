import { computed } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Translation, TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

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

  it('scrum79 lets a computed value follow a language switch', () => {
    service.init();
    const label = computed(() => service.current());
    expect(label()).toBe('uk');

    service.use('en');

    expect(label()).toBe('en');
  });

  it('scrum112 moves the current language only once its translation has loaded', () => {
    service.init();
    const file = new Subject<Translation>();
    vi.spyOn(transloco, 'load').mockReturnValue(file);
    const label = computed(() => service.current());

    service.use('en');

    expect(label()).toBe('uk');
    file.next({});
    expect(label()).toBe('en');
  });

  it('scrum112 ignores a translation that arrives after a newer switch', () => {
    service.init();
    const english = new Subject<Translation>();
    vi.spyOn(transloco, 'load').mockReturnValueOnce(english).mockReturnValueOnce(of({}));

    service.use('en');
    service.use('uk');
    english.next({});

    expect(service.current()).toBe('uk');
  });

  it('scrum112 toggles back when pressed again while the translation loads', () => {
    service.init();
    vi.spyOn(transloco, 'load')
      .mockReturnValueOnce(new Subject<Translation>())
      .mockReturnValue(of({}));

    service.toggle();
    service.toggle();

    expect(transloco.getActiveLang()).toBe('uk');
    expect(service.current()).toBe('uk');
  });

  it('scrum112 resolves the start once the stored language has loaded', async () => {
    localStorage.setItem('lang', 'en');
    const file = new Subject<Translation>();
    vi.spyOn(transloco, 'load').mockReturnValue(file);
    let started = false;

    const start = service.init().then(() => (started = true));
    await Promise.resolve();
    expect(started).toBe(false);
    file.next({});
    await start;

    expect(service.current()).toBe('en');
  });

  it('scrum112 still switches when the translation fails to load', () => {
    service.init();
    vi.spyOn(transloco, 'load').mockReturnValue(throwError(() => new Error('offline')));

    service.use('en');

    expect(service.current()).toBe('en');
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
