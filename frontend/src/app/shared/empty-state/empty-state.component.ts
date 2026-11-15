import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { IconComponent } from '../icons/icon.component';
import { IconName } from '../icons/icons';

/**
 * What a list or a search shows when it has nothing: an icon, one sentence and the next action, which the caller
 * projects as a button or a link.
 */
@Component({
  selector: 'app-empty-state',
  imports: [IconComponent],
  template: `
    <span class="empty-state__icon" aria-hidden="true"><app-icon [name]="icon()" /></span>
    <p class="empty-state__text">{{ text() }}</p>
    <ng-content />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 32px 16px;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }

    .empty-state__icon {
      --app-icon-size: 28px;

      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-primary);
    }

    .empty-state__text {
      max-width: 40ch;
      margin: 0;
      font: var(--mat-sys-body-large);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  /** The icon above the sentence. */
  readonly icon = input.required<IconName>();
  /** The translated sentence. */
  readonly text = input.required<string>();
}
