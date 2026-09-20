import { Injectable, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

export type AppLanguage = 'uk' | 'en';

const STORAGE_KEY = 'lang';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);

  init(): void {
    this.transloco.setActiveLang(this.stored() ?? 'uk');
  }

  current(): AppLanguage {
    return this.transloco.getActiveLang() as AppLanguage;
  }

  toggle(): void {
    this.use(this.current() === 'uk' ? 'en' : 'uk');
  }

  use(lang: AppLanguage): void {
    this.transloco.setActiveLang(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // storage may be unavailable in private mode; the choice then lasts for the page only
    }
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
