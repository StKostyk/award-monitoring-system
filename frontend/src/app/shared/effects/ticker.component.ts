import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';

import { reducedMotion } from '../touch';

/**
 * Short lines shown one at a time, each sliding up to make room for the next; pauses while the pointer is over it
 * or it has the focus (Tab, or a tap on a touch screen).
 * Screen readers get every line as a list; the moving copy is hidden from them. Under reduced motion the first line
 * stays.
 */
@Component({
  selector: 'app-ticker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="cdk-visually-hidden">
      @for (item of items(); track $index) {
        <li>{{ item }}</li>
      }
    </ul>
    <div class="ticker__window" aria-hidden="true">
      <div class="ticker__track" [style.transform]="offset()">
        @for (item of items(); track $index) {
          <div class="ticker__line">{{ item }}</div>
        }
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .ticker__window {
      height: 1.5em;
      overflow: hidden;
      line-height: 1.5;
    }

    .ticker__track {
      transition: transform 0.4s ease-out;
    }

    .ticker__line {
      height: 1.5em;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    @media (prefers-reduced-motion: reduce) {
      .ticker__track {
        transition: none;
      }
    }
  `,
  host: {
    class: 'app-effect',
    tabindex: '0',
    '(pointerenter)': 'hovered.set(true)',
    '(pointerleave)': 'hovered.set(false)',
    '(focusin)': 'focused.set(true)',
    '(focusout)': 'focused.set(false)',
  },
})
export class TickerComponent {
  readonly items = input.required<string[]>();
  readonly intervalSeconds = input(4);

  protected readonly hovered = signal(false);
  protected readonly focused = signal(false);
  private readonly paused = computed(() => this.hovered() || this.focused());
  private readonly index = signal(0);

  protected readonly offset = computed(() => `translateY(${-this.index() * 1.5}em)`);

  constructor() {
    effect((onCleanup) => {
      const count = this.items().length;
      this.index.set(0);
      if (reducedMotion() || count < 2) {
        return;
      }
      const timer = setInterval(() => {
        if (!this.paused()) {
          this.index.update((index) => (index + 1) % count);
        }
      }, this.intervalSeconds() * 1000);
      onCleanup(() => clearInterval(timer));
    });
  }
}
