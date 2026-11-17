import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { TypedTextComponent } from '../effects/typed-text.component';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { translateFact } from './facts';
import { Fact } from './widget-data';

/** H4: one generated fact, typed out letter by letter. */
@Component({
  selector: 'app-fact-widget',
  imports: [EmptyStateComponent, TranslocoPipe, TypedTextComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (text(); as sentence) {
      <p class="fact" data-testid="fact"><app-typed-text [text]="sentence" /></p>
    } @else {
      <app-empty-state icon="award" [text]="'widgets.empty' | transloco" />
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .fact {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }
  `,
})
export class FactWidgetComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** The fact, from `pickFact()` in `facts.ts`, or null when no generator has data. */
  readonly fact = input.required<Fact | null>();

  protected readonly text = computed(() => {
    this.language.current();
    const fact = this.fact();
    return fact ? translateFact(fact, (key, params) => this.transloco.translate(key, params)) : '';
  });
}
