import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

import { MotionHelper } from './motion.helper';

/** Brings the host's child elements in one after another on the first render. */
@Directive({ selector: '[appStagger]' })
export class StaggerDirective {
  constructor() {
    const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const destroyRef = inject(DestroyRef);
    const motion = inject(MotionHelper);
    afterNextRender(() => motion.stagger(Array.from(element.children), destroyRef));
  }
}
