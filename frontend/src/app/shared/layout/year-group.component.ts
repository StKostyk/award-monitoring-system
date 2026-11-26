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
    }

    .year-group__heading {
      margin: 0;
      padding: 14px 18px 4px;
      font: 800 0.875rem / 1.3 var(--app-font-heading);
      color: var(--mat-sys-primary);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YearGroupComponent {
  /** The year or the translated period that heads the group. */
  readonly heading = input.required<string>();
  protected readonly headingId = inject(_IdGenerator).getId('app-year-group-');
}
