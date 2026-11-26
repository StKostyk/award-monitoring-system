import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The head of a page: a context line (unit, period or counts), the page title as the page's only h1, and the chips
 * and actions the caller projects with the pageChips and pageActions attributes. Chips and actions sit beside the
 * title on a wide screen and wrap below it on a phone.
 */
@Component({
  selector: 'app-page-header',
  template: `
    <div class="page-header__text">
      @if (context()) {
        <p class="page-header__context" data-testid="page-context">{{ context() }}</p>
      }
      <h1 class="page-header__title" data-testid="page-heading">{{ heading() }}</h1>
    </div>
    <div class="page-header__side">
      <ng-content select="[pageChips]" />
      <ng-content select="[pageActions]" />
      <ng-content />
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: 12px 24px;
      margin-bottom: 24px;
    }

    .page-header__text {
      display: grid;
      gap: 4px;
      min-width: 0;
      flex: 1 1 auto;
    }

    .page-header__context {
      margin: 0;
      font: 400 0.8125rem / 1.4 var(--app-font-body);
      color: var(--app-muted);
      overflow-wrap: anywhere;
    }

    .page-header__title {
      margin: 0;
      font: 800 1.75rem / 1.2 var(--app-font-heading);
      letter-spacing: -0.01em;
      overflow-wrap: anywhere;
    }

    .page-header__side {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
      min-width: 0;
      max-width: 100%;
    }

    @media (max-width: 599.98px) {
      .page-header__title {
        font: 800 1.5rem / 1.2 var(--app-font-heading);
      }

      .page-header__side {
        flex-basis: 100%;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeaderComponent {
  /** The translated page title. */
  readonly heading = input.required<string>();
  /** The translated context line above the title: unit, period or counts. */
  readonly context = input<string | null>(null);
}
