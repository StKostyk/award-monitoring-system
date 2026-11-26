import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Grey placeholder rows shown in place of a list while it loads. The host is a status region named by the caller's
 * label; the rows themselves are hidden from assistive technology.
 */
@Component({
  selector: 'app-skeleton-rows',
  template: `
    @for (row of rows(); track row) {
      <span class="skeleton__row" aria-hidden="true" data-testid="skeleton-row">
        <span class="skeleton__bar skeleton__bar--title"></span>
        <span class="skeleton__bar skeleton__bar--meta"></span>
      </span>
    }
  `,
  host: { role: 'status', '[attr.aria-label]': 'label()' },
  styles: `
    :host {
      display: block;
    }

    .skeleton__row {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 14px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }

    .skeleton__bar {
      display: block;
      height: 14px;
      border-radius: var(--app-radius-sm);
      background: var(--mat-sys-surface-container-highest);
      animation: skeleton-pulse 1.4s ease-in-out infinite;
    }

    .skeleton__bar--title {
      width: min(60%, 420px);
      height: 18px;
    }

    .skeleton__bar--meta {
      width: min(40%, 260px);
    }

    @keyframes skeleton-pulse {
      50% {
        opacity: 0.5;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .skeleton__bar {
        animation: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkeletonRowsComponent {
  /** How many rows to show. */
  readonly count = input(5);
  /** The translated name of the region, read out while the list loads. */
  readonly label = input.required<string>();
  protected readonly rows = computed(() =>
    Array.from({ length: this.count() }, (_, index) => index),
  );
}
