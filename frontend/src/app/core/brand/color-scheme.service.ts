import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ColorScheme = 'system' | 'light' | 'dark';

export const COLOR_SCHEMES: readonly ColorScheme[] = ['system', 'light', 'dark'];

const STORAGE_KEY = 'color-scheme';

/** Light, dark or system colour scheme, kept in the browser. */
@Injectable({ providedIn: 'root' })
export class ColorSchemeService {
  private readonly document = inject(DOCUMENT);

  readonly scheme = signal<ColorScheme>('system');

  init(): void {
    this.apply(this.stored());
  }

  /**
   * Switches the colour scheme and remembers the choice.
   *
   * @param scheme the scheme; `system` follows the device setting
   */
  use(scheme: ColorScheme): void {
    this.apply(scheme);
    try {
      localStorage.setItem(STORAGE_KEY, scheme);
    } catch {
      // storage may be unavailable in private mode; the choice then lasts for the page only
    }
  }

  private apply(scheme: ColorScheme): void {
    const root = this.document.documentElement;
    if (scheme === 'system') {
      root.removeAttribute('data-color-scheme');
    } else {
      root.setAttribute('data-color-scheme', scheme);
    }
    this.scheme.set(scheme);
  }

  private stored(): ColorScheme {
    try {
      const value = localStorage.getItem(STORAGE_KEY) as ColorScheme | null;
      return value && COLOR_SCHEMES.includes(value) ? value : 'system';
    } catch {
      return 'system';
    }
  }
}
