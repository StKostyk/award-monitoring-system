import { DestroyRef, Directive, ElementRef, effect, inject, input, untracked } from '@angular/core';

import { MotionHelper } from './motion.helper';

/** Counts the host's text to a whole number, from zero at first and from the number on screen after a change. */
@Directive({ selector: '[appCountUp]' })
export class CountUpDirective {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly destroyRef = inject(DestroyRef);
  private readonly motion = inject(MotionHelper);
  private running: { stop(): void } | null = null;

  readonly appCountUp = input.required<number>();

  constructor() {
    effect(() => {
      const to = this.appCountUp();
      untracked(() => {
        this.running?.stop();
        const shown = Number(this.element.textContent);
        this.running = this.motion.countUp(
          this.element,
          Number.isFinite(shown) ? shown : 0,
          to,
          this.destroyRef,
        );
      });
    });
  }
}
