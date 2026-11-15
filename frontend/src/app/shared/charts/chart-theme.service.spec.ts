import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ColorSchemeService } from '../../core/brand/color-scheme.service';
import { escapeHtml } from './chart-style';
import { ChartThemeService, SERIES_COLOURS } from './chart-theme.service';

describe('ChartThemeService', () => {
  const listeners = new Map<string, (event: { matches: boolean }) => void>();

  beforeEach(() => {
    listeners.clear();
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query.includes('reduced-motion'),
        addEventListener: (_type: string, listener: (event: { matches: boolean }) => void) =>
          listeners.set(query, listener),
        removeEventListener: vi.fn(),
      })),
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  it('ac2_6_follows_the_reduced_motion_setting', () => {
    const theme = TestBed.inject(ChartThemeService);

    expect(theme.reducedMotion()).toBe(true);

    listeners.get('(prefers-reduced-motion: reduce)')?.({ matches: false });

    expect(theme.reducedMotion()).toBe(false);
  });

  it('ac2_6_resolves_the_colour_tokens_again_when_the_scheme_changes', () => {
    document.documentElement.style.setProperty('--app-chart-bar', 'rgb(42, 120, 214)');
    const spy = vi.spyOn(document, 'createElement');
    const theme = TestBed.inject(ChartThemeService);

    const first = theme.palette();

    expect(first.bands).toHaveLength(5);
    const probes = spy.mock.calls.length;

    expect(theme.palette()).toBe(first);
    TestBed.inject(ColorSchemeService).scheme.set('dark');
    theme.palette();

    expect(spy.mock.calls.length).toBeGreaterThan(probes);
    listeners.get('(prefers-color-scheme: dark)')?.({ matches: true });
    theme.palette();

    expect(spy.mock.calls.length).toBeGreaterThan(probes * 2 - 1);
    spy.mockRestore();
    expect(document.body.querySelectorAll('span').length).toBe(0);
  });

  it('ac2_4_resolves_the_eight_series_colour_tokens_on_every_scheme', () => {
    const spy = vi.spyOn(CSSStyleDeclaration.prototype, 'setProperty');
    const theme = TestBed.inject(ChartThemeService);
    const series = Array.from(
      { length: SERIES_COLOURS },
      (_, index) => `var(--app-chart-${index + 1})`,
    );
    const probed = () =>
      spy.mock.calls.map(([, value]) => String(value)).filter((value) => series.includes(value));

    expect(theme.palette().series).toHaveLength(SERIES_COLOURS);
    const first = probed().length;

    expect([...new Set(probed())]).toEqual(series);
    TestBed.inject(ColorSchemeService).scheme.set('dark');
    theme.palette();

    expect(probed()).toHaveLength(first * 2);
    spy.mockRestore();
  });

  it('ac2_4_uses_one_probe_element_per_palette', () => {
    const spy = vi.spyOn(document, 'createElement');
    TestBed.inject(ChartThemeService).palette();

    expect(spy.mock.calls.filter(([tag]) => tag === 'span')).toHaveLength(1);
    spy.mockRestore();
    expect(document.body.querySelectorAll('span').length).toBe(0);
  });

  it('ac2_6_escapes_markup_in_tooltip_text', () => {
    expect(escapeHtml(`<b>"Tom" & 'Jerry'</b>`)).toBe(
      '&lt;b&gt;&quot;Tom&quot; &amp; &#39;Jerry&#39;&lt;/b&gt;',
    );
  });
});
