import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { translations } from '../../analytics/analytics.testing';
import { OnboardingTourComponent, placeBubble } from './onboarding-tour.component';

describe('OnboardingTourComponent', () => {
  let fixture: ComponentFixture<OnboardingTourComponent>;
  let ended: ReturnType<typeof vi.fn<() => void>>;
  let search: HTMLButtonElement;

  beforeEach(async () => {
    search = document.createElement('button');
    search.dataset['testid'] = 'palette-open';
    vi.spyOn(search, 'getBoundingClientRect').mockReturnValue(
      DOMRect.fromRect({ x: 16, y: 80, width: 240, height: 40 }),
    );
    search.scrollIntoView = vi.fn();
    document.body.appendChild(search);
    TestBed.configureTestingModule({ imports: [OnboardingTourComponent, translations('en')] });
    fixture = TestBed.createComponent(OnboardingTourComponent);
    ended = vi.fn<() => void>();
    fixture.componentInstance.ended.subscribe(ended);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => search.remove());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function next(): void {
    element('tour-next')?.click();
    fixture.detectChanges();
  }

  it('ac9_5_lights_up_search_first_with_the_step_count', () => {
    expect(element('tour-count')?.textContent).toContain('Step 1 of 4');
    expect(element('tour-bubble')?.textContent).toContain('Ctrl K');
    expect(element('tour-spotlight')?.style.top).toBe('74px');
    expect(element('tour-back')).toBeNull();
    expect(document.activeElement).toBe(element('tour-next'));
  });

  it('ac9_5_keeps_the_lit_area_inside_the_window', () => {
    vi.mocked(search.getBoundingClientRect).mockReturnValue(
      DOMRect.fromRect({ x: 0, y: 2, width: 240, height: 40 }),
    );

    fixture.componentInstance.measure();
    fixture.detectChanges();

    expect(element('tour-spotlight')?.style.left).toBe('3px');
    expect(element('tour-spotlight')?.style.top).toBe('3px');
  });

  it('ac9_5_walks_navigation_new_award_and_the_switch_and_back', () => {
    next();
    expect(element('tour-bubble')?.dataset['stop']).toBe('navigation');
    expect(element('tour-spotlight')).toBeNull();
    next();
    next();
    expect(element('tour-count')?.textContent).toContain('Step 4 of 4');
    expect(element('tour-bubble')?.textContent).toContain('Chart / Table');
    expect(element('tour-next')?.textContent).toContain('Done');

    element('tour-back')?.click();
    fixture.detectChanges();

    expect(element('tour-count')?.textContent).toContain('Step 3 of 4');
    expect(ended).not.toHaveBeenCalled();
  });

  it('ac9_5_ends_after_the_last_step', () => {
    next();
    next();
    next();
    next();

    expect(ended).toHaveBeenCalledTimes(1);
  });

  it('ac9_5_ends_on_skip_and_on_escape_once', () => {
    element('tour-skip')?.click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(ended).toHaveBeenCalledTimes(1);
  });

  it('ac9_5_ends_on_escape', () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(ended).toHaveBeenCalledTimes(1);
  });
});

describe('placeBubble', () => {
  const viewport = { width: 1280, height: 800 };
  const size = { width: 320, height: 160 };

  it('puts_the_bubble_beside_a_tall_element', () => {
    expect(placeBubble({ top: 100, left: 0, width: 260, height: 500 }, size, viewport)).toEqual({
      top: 100,
      left: 272,
    });
  });

  it('puts_the_bubble_below_a_short_element_inside_the_window', () => {
    expect(placeBubble({ top: 20, left: 1200, width: 60, height: 40 }, size, viewport)).toEqual({
      top: 72,
      left: 944,
    });
  });

  it('puts_the_bubble_above_an_element_at_the_bottom', () => {
    expect(placeBubble({ top: 700, left: 40, width: 200, height: 40 }, size, viewport)).toEqual({
      top: 528,
      left: 40,
    });
  });
});
