import { DestroyRef, Injectable, InjectionToken, inject } from '@angular/core';
import { animate, stagger } from 'motion';

import { reducedMotion } from '../touch';

/** Seconds a count-up takes from zero to its final number. */
export const COUNT_UP_SECONDS = 0.8;

/** Seconds between the entries of a staggered list. */
export const STAGGER_SECONDS = 0.05;

/** Seconds one staggered entry takes to fade and slide in. */
export const ENTRY_SECONDS = 0.3;

/** The `motion` functions the helper drives; replaced by a fake in unit tests. */
export const MOTION = new InjectionToken<{ animate: typeof animate; stagger: typeof stagger }>(
  'MOTION',
  {
    providedIn: 'root',
    factory: () => ({ animate, stagger }),
  },
);

/**
 * Count-ups and staggered list entries over the `animate` function of `motion`. Each animation stops when the
 * component that started it is destroyed; under reduced motion the end state is set at once.
 */
@Injectable({ providedIn: 'root' })
export class MotionHelper {
  private readonly motion = inject(MOTION);

  /**
   * Counts the text of an element from one number to another. While it counts, the element is `aria-busy`, so
   * screen readers wait for the final number.
   *
   * @param element element whose text shows the number
   * @param from number the count starts at
   * @param to final number
   * @param destroyRef lifetime of the component that owns the element
   * @return controls that stop the count early, or null when the final number was set at once
   */
  countUp(
    element: HTMLElement,
    from: number,
    to: number,
    destroyRef: DestroyRef,
  ): { stop(): void } | null {
    if (reducedMotion() || from === to) {
      element.textContent = format(to);
      return null;
    }
    element.textContent = format(from);
    element.setAttribute('aria-busy', 'true');
    const settle = () => {
      element.removeAttribute('aria-busy');
      unregister();
    };
    const controls = this.motion.animate(from, to, {
      duration: COUNT_UP_SECONDS,
      ease: 'easeOut',
      onUpdate: (value) => (element.textContent = format(value)),
      onComplete: () => {
        element.textContent = format(to);
        settle();
      },
    });
    const unregister = destroyRef.onDestroy(() => controls.stop());
    return {
      stop: () => {
        controls.stop();
        settle();
      },
    };
  }

  /**
   * Fades and slides list entries in one after another.
   *
   * @param elements entries in their order on the screen
   * @param destroyRef lifetime of the component that owns the entries
   */
  stagger(elements: Element[], destroyRef: DestroyRef): void {
    if (reducedMotion() || elements.length === 0) {
      return;
    }
    const controls = this.motion.animate(
      elements,
      { opacity: [0, 1], transform: ['translateY(8px)', 'none'] },
      { duration: ENTRY_SECONDS, delay: this.motion.stagger(STAGGER_SECONDS), ease: 'easeOut' },
    );
    destroyRef.onDestroy(() => controls.stop());
  }
}

function format(value: number): string {
  return String(Math.round(value));
}
