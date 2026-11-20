import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { IconComponent } from '../icons/icon.component';
import { IconName } from '../icons/icons';

/**
 * A page that only explains where the user stands (not found, forbidden, offline): an icon, the heading, one
 * sentence and the way on, which the caller projects as a button or a link.
 */
@Component({
  selector: 'app-status-page',
  imports: [IconComponent],
  template: `
    <section class="status-page__card">
      <span class="status-page__icon" aria-hidden="true"><app-icon [name]="icon()" /></span>
      @if (level() === 2) {
        <h2 class="status-page__title">{{ title() }}</h2>
      } @else {
        <h1 class="status-page__title">{{ title() }}</h1>
      }
      <p class="status-page__text">{{ text() }}</p>
      <div class="status-page__actions"><ng-content /></div>
    </section>
  `,
  styles: `
    :host {
      display: flex;
      justify-content: center;
      padding: 48px 0;
    }

    .status-page__card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      max-width: 520px;
      padding: 40px 24px;
      border-radius: var(--app-card-radius);
      background: var(--mat-sys-surface-container-low);
      border: 1px solid var(--mat-sys-outline-variant);
      text-align: center;
    }

    .status-page__icon {
      --app-icon-size: 32px;

      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-primary);
    }

    .status-page__title {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }

    .status-page__text {
      max-width: 40ch;
      margin: 0;
      font: var(--mat-sys-body-large);
      color: var(--mat-sys-on-surface-variant);
    }

    .status-page__actions {
      margin-top: 8px;
    }

    @media (max-width: 599.98px) {
      :host {
        padding: 16px 0;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusPageComponent {
  /** The icon above the heading. */
  readonly icon = input.required<IconName>();
  /** The translated heading. */
  readonly title = input.required<string>();
  /** The translated sentence. */
  readonly text = input.required<string>();
  /** The heading level: 1 for a page of its own, 2 inside a pane of a page that has its heading. */
  readonly level = input<1 | 2>(1);
}
