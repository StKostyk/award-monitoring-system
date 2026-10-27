import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { DateAdapter } from '@angular/material/core';
import { TranslocoService } from '@jsverse/transloco';
import { take } from 'rxjs';

import { DATE_LOCALES } from './iso-date-adapter';

export type AppLanguage = 'uk' | 'en';

const STORAGE_KEY = 'lang';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  private readonly document = inject(DOCUMENT);
  private readonly dates = inject(DateAdapter, { optional: true });
  private readonly active = signal(this.transloco.getActiveLang() as AppLanguage);

  /**
   * Activates the stored language, Ukrainian by default.
   *
   * @return resolves when the translation of that language has loaded or failed to load
   */
  init(): Promise<void> {
    return this.activate(this.stored() ?? 'uk');
  }

  current(): AppLanguage {
    return this.active();
  }

  toggle(): void {
    this.use(this.transloco.getActiveLang() === 'uk' ? 'en' : 'uk');
  }

  use(lang: AppLanguage): void {
    void this.activate(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // storage may be unavailable in private mode; the choice then lasts for the page only
    }
  }

  private activate(lang: AppLanguage): Promise<void> {
    this.transloco.setActiveLang(lang);
    return new Promise((resolve) => {
      const settle = (): void => {
        if (this.transloco.getActiveLang() === lang) {
          this.active.set(lang);
          this.dates?.setLocale(DATE_LOCALES[lang]);
          this.document.documentElement.lang = lang;
        }
        resolve();
      };
      this.transloco.load(lang).pipe(take(1)).subscribe({ next: settle, error: settle });
    });
  }

  private stored(): AppLanguage | null {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      return value === 'uk' || value === 'en' ? value : null;
    } catch {
      return null;
    }
  }
}
