import { _IdGenerator } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

/**
 * A surface of a split or bento page: a card with an optional heading that names its region, and the projected
 * content. Used for the side column's summaries and for the detail in the main column.
 */
@Component({
  selector: 'app-pane',
  template: `
    <section class="pane" [attr.aria-labelledby]="heading() ? headingId : null">
      @if (heading()) {
        <h2 class="pane__heading" [id]="headingId">{{ heading() }}</h2>
      }
      <ng-content />
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }

    .pane {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 12px;
      padding: 20px;
      border-radius: var(--app-card-radius);
      background: var(--mat-sys-surface-container-low);
      border: 1px solid var(--mat-sys-outline-variant);
    }

    .pane__heading {
      margin: 0;
      font: var(--mat-sys-title-medium);
      font-family: var(--app-font-heading);
    }

    @media (max-width: 599.98px) {
      .pane {
        padding: 16px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaneComponent {
  /** The translated heading; without it the pane is a plain surface. */
  readonly heading = input<string | null>(null);
  protected readonly headingId = inject(_IdGenerator).getId('app-pane-');
}
