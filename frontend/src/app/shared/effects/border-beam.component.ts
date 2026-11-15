import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A light that runs around the border of its positioned parent, drawn by a turning conic gradient cut to the border.
 * Decorative: hidden from assistive technology and from the pointer, still under reduced motion.
 */
@Component({
  selector: 'app-border-beam',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<span class="border-beam__light"></span>',
  styles: `
    :host {
      position: absolute;
      inset: 0;
      overflow: hidden;
      padding: var(--border-beam-width);
      border-radius: inherit;
      pointer-events: none;
      container-type: size;
      mask:
        linear-gradient(currentcolor 0 0) content-box exclude,
        linear-gradient(currentcolor 0 0);
    }

    .border-beam__light {
      position: absolute;
      top: 50%;
      left: 50%;
      width: max(200cqw, 200cqh);
      aspect-ratio: 1;
      background: conic-gradient(
        from 0deg,
        transparent 0 75%,
        var(--app-gold),
        var(--app-cyan),
        transparent
      );
      transform: translate(-50%, -50%) rotate(0deg);
      animation: border-beam-turn var(--border-beam-duration) linear infinite;
    }

    @keyframes border-beam-turn {
      to {
        transform: translate(-50%, -50%) rotate(360deg);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .border-beam__light {
        animation: none;
      }
    }
  `,
  host: {
    'aria-hidden': 'true',
    class: 'app-effect',
    '[style.--border-beam-duration]': "duration() + 's'",
    '[style.--border-beam-width]': "width() + 'px'",
  },
})
export class BorderBeamComponent {
  readonly duration = input(8);
  readonly width = input(1);
}
