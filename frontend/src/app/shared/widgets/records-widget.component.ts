import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { IconComponent } from '../icons/icon.component';
import { translateFact } from './facts';
import { Fact } from './widget-data';

/** H1: up to three records, each a translated sentence. */
@Component({
  selector: 'app-records-widget',
  imports: [EmptyStateComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (lines().length > 0) {
      <ul class="records" data-testid="records">
        @for (line of lines(); track $index) {
          <li class="records__item">
            <span class="records__icon" aria-hidden="true"><app-icon name="trophy" /></span>
            <span>{{ line }}</span>
          </li>
        }
      </ul>
    } @else {
      <app-empty-state icon="trophy" [text]="'widgets.empty' | transloco" />
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .records {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .records__item {
      display: flex;
      align-items: center;
      gap: 12px;
      font: var(--mat-sys-body-large);
    }

    .records__icon {
      --app-icon-size: 20px;

      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: none;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
  `,
})
export class RecordsWidgetComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** The records, from `records()` in `facts.ts`. */
  readonly records = input.required<Fact[]>();

  protected readonly lines = computed(() => {
    this.language.current();
    return this.records().map((fact) =>
      translateFact(fact, (key, params) => this.transloco.translate(key, params)),
    );
  });
}
