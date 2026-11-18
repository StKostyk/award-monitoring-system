import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { FocusCard } from './widget-data';

/** P6: focus cards, each a title, a line and one action. */
@Component({
  selector: 'app-focus-cards-widget',
  imports: [EmptyStateComponent, MatButton, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (cards().length > 0) {
      <ul class="focus" data-testid="focus-cards">
        @for (card of cards(); track $index) {
          <li class="focus__card" [attr.data-testid]="card.id ? 'focus-' + card.id : null">
            <h3 class="focus__title">{{ card.title }}</h3>
            <p class="focus__line">{{ card.line }}</p>
            @if (card.action; as action) {
              @if (action.link; as link) {
                <a mat-button [routerLink]="link">{{ action.label }}</a>
              } @else {
                <button
                  mat-stroked-button
                  type="button"
                  class="focus__command"
                  [disabled]="action.disabled ?? false"
                  (click)="command.emit(action.command ?? '')"
                >
                  {{ action.label }}
                </button>
              }
            }
          </li>
        }
      </ul>
    } @else {
      <app-empty-state icon="check-check" [text]="'widgets.focus.empty' | transloco" />
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .focus {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .focus__card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 8px;
      padding: 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container);
    }

    .focus__title {
      margin: 0;
      font: var(--mat-sys-title-medium);
    }

    .focus__command {
      min-height: 44px;
    }

    .focus__line {
      flex: 1;
      margin: 0;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class FocusCardsWidgetComponent {
  /** The cards, translated by the caller. */
  readonly cards = input.required<FocusCard[]>();
  /** The command of the card whose button was pressed. */
  readonly command = output<string>();
}
