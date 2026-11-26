import { TestBed } from '@angular/core/testing';

import { ColorSchemeService } from './color-scheme.service';

describe('ColorSchemeService', () => {
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.removeItem('color-scheme');
    root.removeAttribute('data-color-scheme');
  });

  it('ac4_follows_the_device_without_a_stored_choice', () => {
    const service = TestBed.inject(ColorSchemeService);
    service.init();

    expect(service.scheme()).toBe('system');
    expect(root.hasAttribute('data-color-scheme')).toBe(false);
  });

  it('ac4_restores_the_stored_choice', () => {
    localStorage.setItem('color-scheme', 'dark');
    const service = TestBed.inject(ColorSchemeService);
    service.init();

    expect(service.scheme()).toBe('dark');
    expect(root.getAttribute('data-color-scheme')).toBe('dark');
  });

  it('ac4_ignores_an_unknown_stored_value', () => {
    localStorage.setItem('color-scheme', 'sepia');
    const service = TestBed.inject(ColorSchemeService);
    service.init();

    expect(service.scheme()).toBe('system');
  });

  it('ac4_keeps_a_new_choice_and_returns_to_the_device_setting', () => {
    const service = TestBed.inject(ColorSchemeService);
    service.use('light');

    expect(root.getAttribute('data-color-scheme')).toBe('light');
    expect(localStorage.getItem('color-scheme')).toBe('light');

    service.use('system');

    expect(root.hasAttribute('data-color-scheme')).toBe(false);
    expect(localStorage.getItem('color-scheme')).toBe('system');
  });
});
