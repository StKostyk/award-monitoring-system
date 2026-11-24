import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { IconComponent } from '../icons/icon.component';
import { IconName } from '../icons/icons';
import { Fact } from './widget-data';

/** One record as a card: what it is, its value and who or what holds it. */
export interface RecordCard {
  kind: string;
  icon: IconName;
  label: string;
  value: string;
  holder: string;
}

const ICONS: Record<string, IconName> = {
  peakYear: 'trophy',
  leader: 'building',
  highestLevel: 'medal',
};

/** Values longer than this get the smaller type, so a unit name does not push the card apart. */
const LONG_VALUE = 8;

/**
 * The card of a record of `records()` in `facts.ts`.
 *
 * @param fact the record
 * @param translate the translation function
 * @returns the card, its texts translated
 */
export function recordCard(
  fact: Fact,
  translate: (key: string, params?: Record<string, unknown>) => string,
): RecordCard {
  const kind = fact.key.split('.').at(-1) ?? '';
  const params = fact.params;
  const value =
    kind === 'highestLevel'
      ? translate(fact.keyParams?.['level'] ?? '')
      : String(kind === 'peakYear' ? params['year'] : (params['name'] ?? ''));
  const holder =
    kind === 'highestLevel'
      ? translate('widgets.records.period', { period: params['period'] })
      : translate('widgets.records.count', { count: params['count'] });
  return {
    kind,
    icon: ICONS[kind] ?? 'trophy',
    label: translate(`widgets.records.${kind}`),
    value,
    holder,
  };
}

/** H1: up to three records as equal cards with a large value, a label and the holder. */
@Component({
  selector: 'app-records-widget',
  imports: [EmptyStateComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (cards().length > 0) {
      <ul class="records" data-testid="records">
        @for (card of cards(); track card.kind) {
          <li class="record" data-testid="record-card">
            <span class="record__icon" aria-hidden="true"><app-icon [name]="card.icon" /></span>
            <small class="record__label">{{ card.label }}</small>
            <b class="record__value" [class.record__value--long]="card.value.length > longValue">
              {{ card.value }}
            </b>
            <span class="record__holder">{{ card.holder }}</span>
          </li>
        }
      </ul>
    } @else {
      <app-empty-state icon="trophy" [text]="'widgets.empty' | transloco" />
    }
  `,
  styles: `
    :host {
      display: block;
      container-type: inline-size;
    }

    .records {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 1px;
      margin: 0;
      padding: 0;
      list-style: none;
      background: var(--app-row-divider);
    }

    @container (width < 420px) {
      .records {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .record {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 0;
      padding: 16px 14px;
      background: var(--app-pane);
    }

    .record__icon {
      --app-icon-size: 18px;

      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      margin-bottom: 4px;
      border-radius: 10px;
      background: var(--app-brand-panel);
      color: var(--app-gold-soft);
    }

    .record__label {
      font: 700 0.6875rem / 1.3 var(--app-font-heading);
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--app-muted);
    }

    .record__value {
      font: 800 1.375rem / 1.15 var(--app-font-heading);
      overflow-wrap: break-word;
    }

    .record__value--long {
      font-size: 1rem;
      line-height: 1.3;
    }

    .record__holder {
      font: 400 0.8125rem / 1.4 var(--app-font-body);
      color: var(--app-muted);
    }
  `,
})
export class RecordsWidgetComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** The records, from `records()` in `facts.ts`. */
  readonly records = input.required<Fact[]>();

  protected readonly longValue = LONG_VALUE;
  protected readonly cards = computed(() => {
    this.language.current();
    return this.records().map((fact) =>
      recordCard(fact, (key, params) => this.transloco.translate(key, params)),
    );
  });
}
