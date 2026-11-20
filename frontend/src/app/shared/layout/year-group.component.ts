import { _IdGenerator } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

/** A run of list items under one year (or another period) as a heading, as on the story feed and in histories. */
@Component({
  selector: 'app-year-group',
  template: `
    <section class="year-group" [attr.aria-labelledby]="headingId">
      <h2 class="year-group__heading" [id]="headingId">{{ heading() }}</h2>
      <ng-content />
    </section>
  `,
  styles: `
    :host {
      display: block;
    }

    .year-group {
      display: grid;
      gap: 8px;
    }

    .year-group__heading {
      margin: 8px 0 0;
      font: var(--mat-sys-title-small);
      font-family: var(--app-font-heading);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YearGroupComponent {
  /** The year or the translated period that heads the group. */
  readonly heading = input.required<string>();
  protected readonly headingId = inject(_IdGenerator).getId('app-year-group-');
}
