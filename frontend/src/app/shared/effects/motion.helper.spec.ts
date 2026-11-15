import { DestroyRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { COUNT_UP_SECONDS, MOTION, MotionHelper, STAGGER_SECONDS } from './motion.helper';

const motion = {
  stop: vi.fn(),
  animate: vi.fn(),
  stagger: vi.fn((seconds: number) => `stagger ${seconds}`),
};

describe('MotionHelper', () => {
  let destroyCallbacks: (() => void)[];
  let destroyRef: DestroyRef;

  function reduce(matches: boolean): void {
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({ matches: matches && query.includes('reduced-motion') })),
    );
  }

  beforeEach(() => {
    motion.animate.mockReset().mockReturnValue({ stop: motion.stop });
    motion.stop.mockReset();
    destroyCallbacks = [];
    destroyRef = {
      onDestroy: (callback: () => void) => {
        destroyCallbacks.push(callback);
        return () => undefined;
      },
    } as unknown as DestroyRef;
    reduce(false);
    TestBed.configureTestingModule({ providers: [{ provide: MOTION, useValue: motion }] });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('ac2_3_counts_up_through_animate_and_stops_on_destroy', () => {
    const element = document.createElement('span');

    TestBed.inject(MotionHelper).countUp(element, 0, 12, destroyRef);

    expect(element.textContent).toBe('0');
    expect(element.getAttribute('aria-busy')).toBe('true');
    expect(motion.animate).toHaveBeenCalledWith(
      0,
      12,
      expect.objectContaining({ duration: COUNT_UP_SECONDS }),
    );
    const options = motion.animate.mock.calls[0][2] as {
      onUpdate(value: number): void;
      onComplete(): void;
    };
    options.onUpdate(6.4);

    expect(element.textContent).toBe('6');
    options.onComplete();

    expect(element.textContent).toBe('12');
    expect(element.hasAttribute('aria-busy')).toBe(false);
    destroyCallbacks.forEach((callback) => callback());

    expect(motion.stop).toHaveBeenCalledTimes(1);
  });

  it('ac2_2_shows_the_final_number_at_once_under_reduced_motion', () => {
    reduce(true);
    const element = document.createElement('span');

    const controls = TestBed.inject(MotionHelper).countUp(element, 0, 12, destroyRef);

    expect(element.textContent).toBe('12');
    expect(controls).toBeNull();
    expect(motion.animate).not.toHaveBeenCalled();
  });

  it('ac2_3_staggers_entries_through_animate_and_stops_on_destroy', () => {
    const entries = [document.createElement('li'), document.createElement('li')];

    TestBed.inject(MotionHelper).stagger(entries, destroyRef);

    expect(motion.stagger).toHaveBeenCalledWith(STAGGER_SECONDS);
    expect(motion.animate).toHaveBeenCalledWith(
      entries,
      expect.objectContaining({ opacity: [0, 1] }),
      expect.objectContaining({ delay: `stagger ${STAGGER_SECONDS}` }),
    );
    destroyCallbacks.forEach((callback) => callback());

    expect(motion.stop).toHaveBeenCalledTimes(1);
  });

  it('ac2_2_shows_entries_without_a_stagger_under_reduced_motion', () => {
    reduce(true);

    TestBed.inject(MotionHelper).stagger([document.createElement('li')], destroyRef);

    expect(motion.animate).not.toHaveBeenCalled();
  });
});
