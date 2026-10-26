import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { afterEach, beforeEach, vi } from 'vitest';

import { SEARCH_DEBOUNCE, SearchFieldComponent, searchTerm } from './search-field.component';

@Component({
  imports: [SearchFieldComponent],
  template: `<app-search-field
    label="awards.filters.search"
    [value]="value()"
    (search)="searched($event)"
  />`,
})
class HostComponent {
  readonly value = signal<string | null>(null);
  readonly searches: (string | null)[] = [];

  searched(term: string | null): void {
    this.searches.push(term);
    this.value.set(term);
  }
}

describe('SearchFieldComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [
        HostComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              awards: { filters: { search: 'Search my awards' } },
              app: { clearSearch: 'Clear search' },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function input(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="filter-q"]') as HTMLInputElement;
  }

  function type(text: string): void {
    input().value = text;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function clearButton(): HTMLButtonElement | null {
    return fixture.nativeElement.querySelector('[data-testid="filter-q-clear"]');
  }

  it('ac1_10_searches_after_a_pause_in_typing', () => {
    type('hor');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE - 1);
    type('horizon');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE - 1);
    expect(host.searches).toEqual([]);

    vi.advanceTimersByTime(1);

    expect(host.searches).toEqual(['horizon']);
  });

  it('ac1_10_never_searches_by_a_text_under_two_characters', () => {
    type(' h ');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE);

    expect(host.searches).toEqual([]);
  });

  it('ac1_10_lists_without_a_search_once_the_text_gets_too_short', () => {
    type('horizon');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE);
    type('h');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE);

    expect(host.searches).toEqual(['horizon', null]);
  });

  it('ac1_10_does_not_repeat_the_search_shown', () => {
    type('horizon');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE);
    type('horizon ');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE);

    expect(host.searches).toEqual(['horizon']);
    expect(input().value).toBe('horizon ');
  });

  it('ac1_10_clear_button_empties_the_field_and_lists_at_once', async () => {
    expect(clearButton()).toBeNull();
    type('horizon');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE);

    fixture.detectChanges();
    clearButton()?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(host.searches).toEqual(['horizon', null]);
    expect(input().value).toBe('');
    expect(clearButton()).toBeNull();
  });

  it('ac1_12_labels_the_field_and_names_the_clear_button', () => {
    type('horizon');

    expect(fixture.nativeElement.querySelector('mat-label').textContent).toContain(
      'Search my awards',
    );
    expect(clearButton()?.getAttribute('aria-label')).toBe('Clear search');
    expect(input().maxLength).toBe(200);
  });

  it('ac1_11_shows_a_search_set_from_outside', async () => {
    host.value.set('шевченко');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(input().value).toBe('шевченко');

    host.value.set(null);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(input().value).toBe('');
  });

  it('trims_and_drops_texts_the_api_would_refuse', () => {
    expect(searchTerm('  премія ')).toBe('премія');
    expect(searchTerm('a')).toBeNull();
    expect(searchTerm(null)).toBeNull();
    expect(searchTerm('x'.repeat(200))).toHaveLength(200);
    expect(searchTerm('x'.repeat(201))).toBeNull();
  });
});
