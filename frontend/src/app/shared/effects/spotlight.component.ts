import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  input,
} from '@angular/core';

import { reducedMotion } from '../touch';

/**
 * A soft glow in the accent colour that follows the pointer over its positioned parent and goes
 * back to the centre when the pointer leaves. Decorative: hidden from
 * assistive technology and from the pointer; under reduced motion it stays in the centre.
 */
@Component({
  selector: 'app-spotlight',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<span class="spotlight__glow"></span>',
  styles: `
    :host {
      position: absolute;
      inset: 0;
      overflow: hidden;
      pointer-events: none;
      border-radius: inherit;
      container-type: size;
    }

    .spotlight__glow {
      position: absolute;
      top: 0;
      left: 0;
      width: var(--spotlight-size);
      aspect-ratio: 1;
      margin: calc(var(--spotlight-size) / -2) 0 0 calc(var(--spotlight-size) / -2);
      border-radius: 50%;
      background: radial-gradient(circle, var(--app-aurora-2), transparent 70%);
      transform: translate(50cqw, 50cqh);
      transition: transform 0.15s ease-out;
    }
  `,
  host: {
    'aria-hidden': 'true',
    class: 'app-effect',
    '[style.--spotlight-size]': "size() + 'px'",
  },
})
export class SpotlightComponent {
  readonly size = input(320);

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const area = host.parentElement;
      const glow = host.firstElementChild as HTMLElement | null;
      if (!area || !glow || reducedMotion()) {
        return;
      }
      const follow = (event: PointerEvent) => {
        const box = area.getBoundingClientRect();
        const x = event.clientX - box.left - area.clientLeft;
        const y = event.clientY - box.top - area.clientTop;
        glow.style.transform = `translate(${x}px, ${y}px)`;
      };
      const centre = () => glow.style.removeProperty('transform');
      area.addEventListener('pointermove', follow);
      area.addEventListener('pointerleave', centre);
      destroyRef.onDestroy(() => {
        area.removeEventListener('pointermove', follow);
        area.removeEventListener('pointerleave', centre);
      });
    });
  }
}
