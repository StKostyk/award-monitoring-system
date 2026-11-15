import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A streak of light that sweeps across its positioned parent in the accent colours. Decorative: hidden from assistive
 * technology and from the pointer, still under reduced motion.
 */
@Component({
  selector: 'app-beam',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<span class="beam__streak"></span>',
  styles: `
    :host {
      position: absolute;
      inset: 0;
      overflow: hidden;
      pointer-events: none;
      border-radius: inherit;
    }

    .beam__streak {
      position: absolute;
      top: 0;
      left: 0;
      width: 40%;
      height: 1px;
      background: linear-gradient(
        90deg,
        transparent,
        var(--app-accent-from),
        var(--app-accent-to),
        transparent
      );
      transform: translateX(-100%);
      animation: beam-sweep var(--beam-duration) linear infinite;
    }

    :host(.beam--vertical) .beam__streak {
      width: 1px;
      height: 40%;
      background: linear-gradient(
        180deg,
        transparent,
        var(--app-accent-from),
        var(--app-accent-to),
        transparent
      );
      transform: translateY(-100%);
      animation-name: beam-fall;
    }

    @keyframes beam-sweep {
      to {
        transform: translateX(250%);
      }
    }

    @keyframes beam-fall {
      to {
        transform: translateY(250%);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .beam__streak,
      :host(.beam--vertical) .beam__streak {
        animation: none;
        transform: none;
      }
    }
  `,
  host: {
    'aria-hidden': 'true',
    class: 'app-effect',
    '[class.beam--vertical]': "direction() === 'vertical'",
    '[style.--beam-duration]': "duration() + 's'",
  },
})
export class BeamComponent {
  readonly direction = input<'horizontal' | 'vertical'>('horizontal');
  readonly duration = input(6);
}
