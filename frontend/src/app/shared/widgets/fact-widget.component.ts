import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { TypedTextComponent } from '../effects/typed-text.component';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { translateFact } from './facts';
import { Fact } from './widget-data';

/** H4: the generated facts as a list, each typed out letter by letter. */
@Component({
  selector: 'app-fact-widget',
  imports: [EmptyStateComponent, TranslocoPipe, TypedTextComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sentences().length > 0) {
      <ul class="facts">
        @for (sentence of sentences(); track $index) {
          <li class="fact" data-testid="fact"><app-typed-text [text]="sentence" /></li>
        }
      </ul>
    } @else {
      <app-empty-state icon="award" [text]="'widgets.empty' | transloco" />
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .facts {
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .fact {
      padding: 13px 0;
      border-bottom: 1px solid var(--app-fact-divider, var(--app-row-divider));
      font: 700 0.9375rem / 1.45 var(--app-font-heading);
    }

    .fact:first-child {
      padding-top: 0;
    }

    .fact:last-child {
      padding-bottom: 0;
      border-bottom: 0;
    }

    :host-context(.pane--ink) .fact {
      padding-inline: 18px;
      border-color: var(--app-ink-divider);
    }

    :host-context(.pane--ink) .fact:first-child {
      padding-top: 13px;
    }

    :host-context(.pane--ink) .fact:last-child {
      padding-bottom: 13px;
    }
  `,
})
export class FactWidgetComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** The facts, from `facts()` in `facts.ts`; empty when no generator has data. */
  readonly facts = input.required<Fact[]>();

  protected readonly sentences = computed(() => {
    this.language.current();
    return this.facts().map((fact) =>
      translateFact(fact, (key, params) => this.transloco.translate(key, params)),
    );
  });
}
