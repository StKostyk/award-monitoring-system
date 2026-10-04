import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { AppLanguage } from '../i18n/language.service';

export type BrandId = 'chnu' | 'neutral';

export interface LocalizedText {
  uk: string;
  en: string;
}

export interface Brand {
  id: BrandId;
  name: LocalizedText;
  organization: LocalizedText;
  logo: string;
}

const BRAND_IDS: readonly BrandId[] = ['chnu', 'neutral'];

export const BRAND_URL = 'brand/brand.json';

export const NEUTRAL_BRAND: Brand = {
  id: 'neutral',
  name: { uk: 'Облік нагород', en: 'Award Registry' },
  organization: { uk: 'Університет', en: 'University' },
  logo: 'brand/neutral/logo.svg',
};

/** The university brand of this deployment: theme class, product name, organization and logo. */
@Injectable({ providedIn: 'root' })
export class BrandService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly brand = signal<Brand>(NEUTRAL_BRAND);

  /**
   * Loads the brand file and applies it; a missing, unreadable or unknown brand falls back to the neutral one.
   *
   * @return resolves once the theme class is set
   */
  async init(): Promise<void> {
    let brand = NEUTRAL_BRAND;
    try {
      brand = parseBrand(await firstValueFrom(this.http.get<unknown>(BRAND_URL))) ?? NEUTRAL_BRAND;
    } catch {
      brand = NEUTRAL_BRAND;
    }
    this.apply(brand);
    this.transloco.langChanges$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((lang) => this.title.setTitle(this.text(brand.name, lang)));
  }

  /**
   * The text in the active language.
   *
   * @param text the text in both languages
   * @param lang the language, the active one when omitted
   * @return the text in that language
   */
  text(text: LocalizedText, lang = this.transloco.getActiveLang()): string {
    return (lang as AppLanguage) === 'en' ? text.en : text.uk;
  }

  private apply(brand: Brand): void {
    const root = this.document.documentElement;
    BRAND_IDS.forEach((id) => root.classList.remove(`brand-${id}`));
    root.classList.add(`brand-${brand.id}`);
    this.brand.set(brand);
    this.title.setTitle(this.text(brand.name));
  }
}

function parseBrand(value: unknown): Brand | null {
  if (!isRecord(value) || !BRAND_IDS.includes(value['id'] as BrandId)) {
    return null;
  }
  const name = localized(value['name']);
  const organization = localized(value['organization']);
  const logo = value['logo'];
  if (!name || !organization || typeof logo !== 'string' || !isLocalPath(logo)) {
    return null;
  }
  return { id: value['id'] as BrandId, name, organization, logo };
}

function localized(value: unknown): LocalizedText | null {
  return isRecord(value) && typeof value['uk'] === 'string' && typeof value['en'] === 'string'
    ? { uk: value['uk'], en: value['en'] }
    : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isLocalPath(path: string): boolean {
  return /^[a-z0-9_-][a-z0-9/_.-]*\.(svg|png)$/i.test(path) && !/\.\.|\/\//.test(path);
}
