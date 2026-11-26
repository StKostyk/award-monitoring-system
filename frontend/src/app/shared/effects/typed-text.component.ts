import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';

import { reducedMotion } from '../touch';

/**
 * A sentence typed out letter by letter with a blinking caret. Screen readers get the whole sentence from the first
 * render; the typed copy is hidden from them. Under reduced motion the whole sentence shows at once.
 */
@Component({
  selector: 'app-typed-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="cdk-visually-hidden" data-testid="typed-text-full">{{ text() }}</span>
    <span class="typed-text__shown" aria-hidden="true"
      >{{ shown() }}<span class="typed-text__caret" [class.typed-text__caret--done]="done()"></span
    ></span>
  `,
  styles: `
    :host {
      display: inline;
    }

    .typed-text__caret {
      display: inline-block;
      width: 2px;
      height: 1em;
      margin-inline-start: 2px;
      vertical-align: text-bottom;
      background: var(--app-accent-from);
      animation: typed-text-blink 1s steps(1) infinite;
    }

    .typed-text__caret--done {
      animation: none;
      opacity: 0;
    }

    @keyframes typed-text-blink {
      50% {
        opacity: 0;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .typed-text__caret {
        animation: none;
        opacity: 0;
      }
    }
  `,
  host: { class: 'app-effect' },
})
export class TypedTextComponent {
  readonly text = input.required<string>();
  readonly letterMs = input(40);

  private readonly typed = signal(0);
  private readonly letters = computed(() => letters(this.text()));

  protected readonly shown = computed(() => this.letters().slice(0, this.typed()).join(''));
  protected readonly done = computed(() => this.typed() >= this.letters().length);

  constructor() {
    effect((onCleanup) => {
      const length = this.letters().length;
      if (reducedMotion()) {
        this.typed.set(length);
        return;
      }
      this.typed.set(0);
      const timer = setInterval(() => {
        this.typed.update((count) => count + 1);
        if (this.typed() >= length) {
          clearInterval(timer);
        }
      }, this.letterMs());
      onCleanup(() => clearInterval(timer));
    });
  }
}

function letters(text: string): string[] {
  if (typeof Intl.Segmenter === 'function') {
    return Array.from(
      new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text),
      (part) => part.segment,
    );
  }
  return Array.from(text);
}
