import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  input,
} from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

import { ICONS, IconName } from './icons';

/**
 * A Lucide icon drawn as inline SVG in the current text colour, sized by `--app-icon-size`. Icons are decorative: the
 * control around an icon-only icon carries the accessible name. The host carries the `mat-icon` class, so Material
 * buttons, menu items and lists size and space it like their own icons.
 */
@Component({
  selector: 'app-icon',
  imports: [LucideAngularModule],
  template: `<lucide-icon [img]="data()" [strokeWidth]="strokeWidth()" />`,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      align-items: center;
      justify-content: center;
      width: var(--app-icon-size);
      height: var(--app-icon-size);
      line-height: 1;
      vertical-align: middle;
    }

    :host(.app-icon--inline) {
      --app-icon-size: 1em;
    }

    lucide-icon {
      display: inline-flex;
      width: 100%;
      height: 100%;
    }

    :host ::ng-deep svg {
      width: 100%;
      height: 100%;
    }
  `,
  host: {
    class: 'mat-icon',
    'aria-hidden': 'true',
    '[attr.data-icon]': 'name()',
    '[class.app-icon--inline]': 'inline()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconComponent {
  /** The icon to draw. */
  readonly name = input.required<IconName>();
  /** Whether the icon takes the size of the surrounding text. */
  readonly inline = input(false, { transform: booleanAttribute });
  /** The stroke width in viewBox units. */
  readonly strokeWidth = input(2);

  protected readonly data = computed(() => ICONS[this.name()]);
}
