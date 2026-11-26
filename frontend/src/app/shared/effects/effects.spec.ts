import { Component, Type, inputBinding } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { BeamComponent } from './beam.component';
import { BorderBeamComponent } from './border-beam.component';
import { MarqueeComponent } from './marquee.component';
import { SpotlightComponent } from './spotlight.component';
import { TickerComponent } from './ticker.component';
import { TypedTextComponent } from './typed-text.component';

const SENTENCE = 'Faculty of Mathematics leads this year';
const LINES = ['First line', 'Second line', 'Third line'];
const LETTER_MS = 40;
const TICK_MS = 4000;

@Component({
  imports: [SpotlightComponent],
  template: '<div class="area"><app-spotlight /></div>',
})
class SpotlightHostComponent {}

describe('Effect components', () => {
  function reduce(matches: boolean): void {
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({ matches: matches && query.includes('reduced-motion') })),
    );
  }

  function create<T>(type: Type<T>, inputs: Record<string, unknown> = {}): ComponentFixture<T> {
    const fixture = TestBed.createComponent(type, {
      bindings: Object.entries(inputs).map(([name, value]) => inputBinding(name, () => value)),
    });
    fixture.detectChanges();
    return fixture;
  }

  function styles(type: Type<unknown>): string {
    const definition = (type as unknown as { ɵcmp: { styles: string[] } }).ɵcmp;
    return definition.styles.join('\n');
  }

  beforeEach(() => reduce(false));

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it.each<Type<unknown>>([BeamComponent, BorderBeamComponent, SpotlightComponent])(
    'ac2_1_hides_the_decorative_%o_from_assistive_technology',
    (type) => {
      const fixture = create(type);

      expect(fixture.nativeElement.getAttribute('aria-hidden')).toBe('true');
    },
  );

  it.each<Type<unknown>>([
    BeamComponent,
    BorderBeamComponent,
    SpotlightComponent,
    TickerComponent,
    MarqueeComponent,
    TypedTextComponent,
  ])('ac2_1_takes_its_colours_from_tokens_only_%o', (type) => {
    const css = styles(type);

    expect(css).not.toMatch(/#[0-9a-f]{3,8}\b/i);
    expect(css).not.toMatch(/rgba?\(/);
  });

  it('ac2_1_draws_beams_in_the_accent_and_gold_tokens', () => {
    expect(styles(BeamComponent)).toContain('var(--app-accent-from)');
    expect(styles(BorderBeamComponent)).toContain('var(--app-gold)');
    expect(styles(SpotlightComponent)).toContain('var(--app-aurora-2)');
  });

  it('ac2_1_gives_screen_readers_the_whole_typed_sentence_from_the_first_render', () => {
    vi.useFakeTimers();
    const fixture = create(TypedTextComponent, { text: SENTENCE });
    const full = fixture.nativeElement.querySelector('[data-testid="typed-text-full"]');
    const shown = fixture.nativeElement.querySelector('.typed-text__shown');

    expect(full.textContent).toBe(SENTENCE);
    expect(shown.getAttribute('aria-hidden')).toBe('true');
    expect(shown.textContent).toBe('');

    vi.advanceTimersByTime(LETTER_MS * 3);
    fixture.detectChanges();

    expect(shown.textContent).toBe(SENTENCE.slice(0, 3));
    vi.advanceTimersByTime(LETTER_MS * SENTENCE.length);
    fixture.detectChanges();

    expect(shown.textContent).toBe(SENTENCE);
  });

  it('ac2_2_shows_the_whole_typed_sentence_at_once_under_reduced_motion', () => {
    reduce(true);
    const fixture = create(TypedTextComponent, { text: SENTENCE });

    expect(fixture.nativeElement.querySelector('.typed-text__shown').textContent).toBe(SENTENCE);
  });

  it('ac2_1_lists_every_ticker_line_for_screen_readers_and_moves_the_hidden_copy', () => {
    vi.useFakeTimers();
    const fixture = create(TickerComponent, { items: LINES });
    const listed = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('ul li')).map(
      (item) => item.textContent,
    );
    const track = fixture.nativeElement.querySelector('.ticker__track');

    expect(listed).toEqual(LINES);
    expect(fixture.nativeElement.querySelector('.ticker__window').getAttribute('aria-hidden')).toBe(
      'true',
    );
    expect(track.style.transform).toBe('translateY(0em)');

    vi.advanceTimersByTime(TICK_MS);
    fixture.detectChanges();

    expect(track.style.transform).toBe('translateY(-1.5em)');
  });

  it('ac2_1_pauses_the_ticker_while_the_pointer_is_over_it', () => {
    vi.useFakeTimers();
    const fixture = create(TickerComponent, { items: LINES });
    fixture.nativeElement.dispatchEvent(new Event('pointerenter'));

    vi.advanceTimersByTime(TICK_MS);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.ticker__track').style.transform).toBe(
      'translateY(0em)',
    );
  });

  it('ac2_1_pauses_the_ticker_while_it_has_the_focus', () => {
    vi.useFakeTimers();
    const fixture = create(TickerComponent, { items: LINES });

    expect(fixture.nativeElement.getAttribute('tabindex')).toBe('0');
    fixture.nativeElement.dispatchEvent(new FocusEvent('focusin'));
    vi.advanceTimersByTime(TICK_MS);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.ticker__track').style.transform).toBe(
      'translateY(0em)',
    );
    fixture.nativeElement.dispatchEvent(new FocusEvent('focusout'));
    vi.advanceTimersByTime(TICK_MS);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.ticker__track').style.transform).toBe(
      'translateY(-1.5em)',
    );
  });

  it('ac2_1_types_whole_letters_when_a_letter_is_two_code_points', () => {
    vi.useFakeTimers();
    const word = 'Мій'.normalize('NFD');
    const fixture = create(TypedTextComponent, { text: word });
    const shown = fixture.nativeElement.querySelector('.typed-text__shown');

    vi.advanceTimersByTime(LETTER_MS * 3);
    fixture.detectChanges();

    expect(word).toHaveLength(4);
    expect(shown.textContent).toBe(word);
    vi.useRealTimers();
    const partial = create(TypedTextComponent, { text: word });
    expect(partial.nativeElement.querySelector('.typed-text__shown').textContent).toBe('');
  });

  it('ac2_2_keeps_the_ticker_on_its_first_line_under_reduced_motion', () => {
    reduce(true);
    vi.useFakeTimers();
    const fixture = create(TickerComponent, { items: LINES });

    vi.advanceTimersByTime(TICK_MS * 2);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.ticker__track').style.transform).toBe(
      'translateY(0em)',
    );
  });

  it('ac2_1_hides_the_marquee_loop_copy_from_screen_readers', () => {
    const fixture = create(MarqueeComponent, { items: LINES });
    const lists = fixture.nativeElement.querySelectorAll('ul');

    expect(lists).toHaveLength(2);
    expect(lists[0].hasAttribute('aria-hidden')).toBe(false);
    expect(lists[1].getAttribute('aria-hidden')).toBe('true');
  });

  it('ac2_2_stops_every_css_effect_under_reduced_motion', () => {
    for (const type of [BeamComponent, BorderBeamComponent, MarqueeComponent, TypedTextComponent]) {
      expect(styles(type)).toMatch(/prefers-reduced-motion:\s*reduce[\s\S]*animation:\s*none/);
    }
  });

  it('ac2_1_moves_the_spotlight_with_the_pointer', async () => {
    const fixture = TestBed.createComponent(SpotlightHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const area: HTMLElement = fixture.nativeElement.querySelector('.area');

    area.dispatchEvent(Object.assign(new Event('pointermove'), { clientX: 30, clientY: 40 }));

    expect(area.querySelector<HTMLElement>('.spotlight__glow')!.style.transform).toBe(
      'translate(30px, 40px)',
    );
  });

  it('ac2_1_brings_the_spotlight_back_to_the_centre_when_the_pointer_leaves', async () => {
    const fixture = TestBed.createComponent(SpotlightHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const area: HTMLElement = fixture.nativeElement.querySelector('.area');
    const glow = area.querySelector<HTMLElement>('.spotlight__glow')!;

    area.dispatchEvent(Object.assign(new Event('pointermove'), { clientX: 30, clientY: 40 }));
    area.dispatchEvent(new Event('pointerleave'));

    expect(glow.style.transform).toBe('');
  });

  it('ac2_2_keeps_the_spotlight_still_under_reduced_motion', async () => {
    reduce(true);
    const fixture = TestBed.createComponent(SpotlightHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const area: HTMLElement = fixture.nativeElement.querySelector('.area');

    area.dispatchEvent(Object.assign(new Event('pointermove'), { clientX: 30, clientY: 40 }));

    expect(area.querySelector<HTMLElement>('.spotlight__glow')!.style.transform).toBe('');
  });
});
