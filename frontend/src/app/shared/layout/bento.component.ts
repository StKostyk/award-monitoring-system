import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A bento page: tiles in four columns from 960 px, two columns from 600 px and one column below, in their template
 * order (a wide tile never lets a later one move ahead of it).
 */
@Component({
  selector: 'app-bento',
  template: `<ng-content />`,
  styles: `
    :host {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 16px;
    }

    @media (max-width: 959.98px) {
      :host {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

    @media (max-width: 599.98px) {
      :host {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BentoComponent {}

/**
 * One tile of a bento page, spanning one to four columns and one or two rows; the span shrinks to the columns a narrow
 * screen has, and a tile spans two rows only on the four-column grid.
 */
@Component({
  selector: 'app-bento-tile',
  host: { '[attr.data-span]': 'span()', '[attr.data-rows]': 'rows()' },
  template: `<ng-content />`,
  styles: `
    :host {
      display: block;
      min-width: 0;
      grid-column: span 1;
    }

    :host([data-span='2']) {
      grid-column: span 2;
    }

    :host([data-span='3']) {
      grid-column: span 3;
    }

    :host([data-span='4']) {
      grid-column: span 4;
    }

    :host([data-rows='2']) {
      grid-row: span 2;
    }

    @media (max-width: 959.98px) {
      :host([data-rows='2']) {
        grid-row: auto;
      }

      :host([data-span='3']),
      :host([data-span='4']) {
        grid-column: span 2;
      }
    }

    @media (max-width: 599.98px) {
      :host([data-span]) {
        grid-column: span 1;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BentoTileComponent {
  /** The number of columns the tile spans on a wide screen, 1 to 4. */
  readonly span = input<1 | 2 | 3 | 4>(1);
  /** The number of rows the tile spans on the four-column grid, 1 or 2. */
  readonly rows = input<1 | 2>(1);
}
