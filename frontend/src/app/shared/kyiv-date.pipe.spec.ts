import { TestBed } from '@angular/core/testing';

import { LanguageService } from '../core/i18n/language.service';
import { KyivDatePipe } from './kyiv-date.pipe';

describe('KyivDatePipe', () => {
  let language: 'uk' | 'en';
  let pipe: KyivDatePipe;

  beforeEach(() => {
    language = 'uk';
    TestBed.configureTestingModule({
      providers: [
        KyivDatePipe,
        { provide: LanguageService, useValue: { current: () => language } },
      ],
    });
    pipe = TestBed.inject(KyivDatePipe);
  });

  it('shows_a_day_in_the_interface_language', () => {
    expect(pipe.transform('2026-10-09')).toBe('09.10.2026');
    language = 'en';
    expect(pipe.transform('2026-10-09')).toBe('09/10/2026');
  });

  it('shows_an_instant_on_the_kyiv_calendar', () => {
    expect(pipe.transform('2026-10-08T22:30:00Z')).toBe('09.10.2026');
  });

  it('shows_a_dash_when_there_is_no_value', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
  });
});
