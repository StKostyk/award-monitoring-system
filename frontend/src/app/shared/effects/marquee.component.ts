import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A row of short items that scrolls sideways without end; pauses while the pointer is over it or it has
 * the focus (Tab, or a tap on a touch screen). The second copy that
 * closes the loop is hidden from screen readers. Under reduced motion the row stands still and wraps.
 */
@Component({
  selector: 'app-marquee',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="marquee__track">
      <ul class="marquee__list">
        @for (item of items(); track $index) {
          <li class="marquee__item">{{ item }}</li>
        }
      </ul>
      <ul class="marquee__list marquee__list--clone" aria-hidden="true">
        @for (item of items(); track $index) {
          <li class="marquee__item">{{ item }}</li>
        }
      </ul>
    </div>
  `,
  styles: `
    :host {
      display: block;
      overflow: hidden;
      mask: linear-gradient(90deg, transparent, currentcolor 8%, currentcolor 92%, transparent);
    }

    .marquee__track {
      display: flex;
      width: max-content;
      animation: marquee-scroll var(--marquee-duration) linear infinite;
    }

    :host(:hover) .marquee__track,
    :host(:focus-within) .marquee__track {
      animation-play-state: paused;
    }

    .marquee__list {
      display: flex;
      gap: 24px;
      margin: 0;
      padding: 0 12px;
      list-style: none;
    }

    .marquee__item {
      white-space: nowrap;
    }

    @keyframes marquee-scroll {
      to {
        transform: translateX(-50%);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      :host {
        mask: none;
      }

      .marquee__track {
        width: auto;
        animation: none;
      }

      .marquee__list {
        flex-wrap: wrap;
      }

      .marquee__list--clone {
        display: none;
      }
    }
  `,
  host: {
    class: 'app-effect',
    tabindex: '0',
    '[style.--marquee-duration]': "duration() + 's'",
  },
})
export class MarqueeComponent {
  readonly items = input.required<string[]>();
  readonly duration = input(30);
}
