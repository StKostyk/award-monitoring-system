import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { TopPerson } from './widget-data';

/** People P2 shows at most. */
export const MAX_TOP = 5;

const PERCENT = 100;

/** The initials of a name: the first letter of its first two words. */
export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => Array.from(word)[0] ?? '')
    .join('')
    .toLocaleUpperCase();
}

/** P2: up to five people as bars with their initials, never a photo. */
@Component({
  selector: 'app-top-widget',
  imports: [EmptyStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rows().length > 0) {
      <ol class="top" data-testid="top">
        @for (row of rows(); track $index) {
          <li class="top__row">
            <span class="top__initials" aria-hidden="true">{{ row.initials }}</span>
            <span class="top__name">{{ row.name }}</span>
            <span class="top__bar" aria-hidden="true"
              ><span class="top__fill" [style.width.%]="row.width"></span
            ></span>
            <span class="top__count">
              <span class="cdk-visually-hidden">{{ 'widgets.awards' | transloco }}:</span>
              {{ row.count }}
            </span>
          </li>
        }
      </ol>
    } @else {
      <app-empty-state icon="users" [text]="'widgets.empty' | transloco" />
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .top {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .top__row {
      display: grid;
      grid-template-columns: 36px minmax(0, 1fr) auto;
      grid-template-areas: 'initials name count' 'initials bar bar';
      align-items: center;
      column-gap: 12px;
      row-gap: 4px;
    }

    .top__initials {
      grid-area: initials;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font: var(--mat-sys-label-large);
    }

    .top__name {
      grid-area: name;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font: var(--mat-sys-body-large);
    }

    .top__count {
      grid-area: count;
      font: var(--mat-sys-title-medium);
      font-variant-numeric: tabular-nums;
    }

    .top__bar {
      grid-area: bar;
      display: block;
      height: 8px;
      border-radius: 4px;
      background: var(--mat-sys-surface-container-high);
    }

    .top__fill {
      display: block;
      height: 100%;
      border-radius: 4px;
      background: var(--app-chart-bar);
    }
  `,
})
export class TopWidgetComponent {
  /** The people, most awards first. */
  readonly people = input.required<TopPerson[]>();

  protected readonly rows = computed(() => {
    const shown = this.people()
      .filter((person) => person.count > 0)
      .slice(0, MAX_TOP);
    const max = Math.max(...shown.map((person) => person.count), 1);
    return shown.map((person) => ({
      ...person,
      initials: initials(person.name),
      width: Math.round((person.count * PERCENT) / max),
    }));
  });
}
