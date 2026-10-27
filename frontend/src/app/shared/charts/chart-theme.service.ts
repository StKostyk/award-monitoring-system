import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

import { ColorSchemeService } from '../../core/brand/color-scheme.service';
import { REDUCED_MOTION } from '../touch';

/** Chart colours resolved from the theme tokens of the active colour scheme. */
export interface ChartPalette {
  text: string;
  mutedText: string;
  grid: string;
  surface: string;
  bar: string;
  bands: string[];
  fontFamily: string;
}

const TOKENS = {
  text: '--mat-sys-on-surface',
  mutedText: '--mat-sys-on-surface-variant',
  grid: '--mat-sys-outline-variant',
  surface: '--mat-sys-surface-container',
  bar: '--app-chart-bar',
};

const BAND_TOKENS = [1, 2, 3, 4, 5].map((step) => `--app-chart-band-${step}`);

/** Theme colours and motion preference for charts, following the colour scheme and the device settings. */
@Injectable({ providedIn: 'root' })
export class ChartThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly scheme = inject(ColorSchemeService).scheme;
  private readonly systemDark = signal(this.matches('(prefers-color-scheme: dark)'));

  readonly reducedMotion = signal(this.matches(REDUCED_MOTION));

  readonly palette = computed<ChartPalette>(() => {
    this.scheme();
    this.systemDark();
    return {
      text: this.resolve(TOKENS.text),
      mutedText: this.resolve(TOKENS.mutedText),
      grid: this.resolve(TOKENS.grid),
      surface: this.resolve(TOKENS.surface),
      bar: this.resolve(TOKENS.bar),
      bands: BAND_TOKENS.map((token) => this.resolve(token)),
      fontFamily: this.document.defaultView?.getComputedStyle(this.document.body).fontFamily ?? '',
    };
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.watch('(prefers-color-scheme: dark)', this.systemDark, destroyRef);
    this.watch(REDUCED_MOTION, this.reducedMotion, destroyRef);
  }

  private resolve(token: string): string {
    const probe = this.document.createElement('span');
    probe.style.color = `var(${token})`;
    probe.style.display = 'none';
    this.document.body.appendChild(probe);
    const color = this.document.defaultView?.getComputedStyle(probe).color ?? '';
    probe.remove();
    return color;
  }

  private matches(query: string): boolean {
    return this.document.defaultView?.matchMedia?.(query).matches ?? false;
  }

  private watch(
    query: string,
    target: ReturnType<typeof signal<boolean>>,
    destroyRef: DestroyRef,
  ): void {
    const list = this.document.defaultView?.matchMedia?.(query);
    if (!list) {
      return;
    }
    const listener = (event: MediaQueryListEvent) => target.set(event.matches);
    list.addEventListener('change', listener);
    destroyRef.onDestroy(() => list.removeEventListener('change', listener));
  }
}
