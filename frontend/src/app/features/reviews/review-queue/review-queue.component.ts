import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, filter, of, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import { AwardDocumentsComponent } from '../../awards/award-documents/award-documents.component';
import { AwardsService, awardTitle, categoryName } from '../../awards/awards.service';
import {
  DecisionDialogComponent,
  DecisionDialogData,
  DecisionInput,
} from '../decision-dialog/decision-dialog.component';
import { HeldDecisionService } from '../held-decision.service';
import { DecisionType, ReviewItem } from '../reviews.service';

/** The checklist of the queue; ticks are an aid and are neither sent nor kept. */
export const QUEUE_CHECKS = ['readable', 'match', 'level'] as const;

const KEYS: Record<string, 'next' | 'previous' | 'APPROVE' | 'RETURN'> = {
  KeyJ: 'next',
  KeyK: 'previous',
  KeyA: 'APPROVE',
  KeyR: 'RETURN',
};

/**
 * The review queue: one open request at a time with its document, fields and checklist, moved through with J and
 * K, decided with A and R. A confirmed decision is held for ten seconds by {@link HeldDecisionService}.
 */
@Component({
  selector: 'app-review-queue',
  imports: [
    AwardDocumentsComponent,
    EmptyStateComponent,
    MatButton,
    MatCheckbox,
    RouterLink,
    TranslocoPipe,
    KyivDatePipe,
  ],
  templateUrl: './review-queue.component.html',
  styleUrl: './review-queue.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewQueueComponent {
  private readonly auth = inject(AuthService);
  private readonly awards = inject(AwardsService);
  private readonly dialog = inject(MatDialog);
  private readonly language = inject(LanguageService);
  private readonly held = inject(HeldDecisionService);

  /** The open requests of the page, without those whose decision is held. */
  readonly items = input.required<ReviewItem[]>();
  /** How many open requests are left in the queue. */
  readonly left = input.required<number>();
  readonly loading = input(false);
  /** Emits the award whose decision was confirmed and is now held. */
  readonly decided = output<number>();

  protected readonly checks = QUEUE_CHECKS;
  private readonly chosen = signal<number | null>(null);
  readonly selected = computed(() => {
    const items = this.items();
    return items.find((item) => item.awardId === this.chosen()) ?? items[0] ?? null;
  });
  /** The ticked checks of the selected request, cleared when another one is selected. */
  readonly ticked = linkedSignal<number | null, ReadonlySet<string>>({
    source: () => this.selected()?.awardId ?? null,
    computation: () => new Set<string>(),
  });
  readonly award = toSignal(
    toObservable(computed(() => this.selected()?.awardId ?? null)).pipe(
      switchMap((id) =>
        id === null ? of(null) : this.awards.get(id).pipe(catchError(() => of(null))),
      ),
    ),
    { initialValue: null },
  );
  readonly canDecide = computed(() => {
    const item = this.selected();
    return (
      item !== null && (item.reviewer === null || String(item.reviewer.id) === this.auth.userId())
    );
  });

  /** Moves to the next request, or the previous one with a negative step. */
  move(step: number): void {
    const items = this.items();
    const index = items.findIndex((item) => item.awardId === this.selected()?.awardId);
    const next = items[index + step];
    if (next) {
      this.chosen.set(next.awardId);
    }
  }

  tick(check: string, on: boolean): void {
    this.ticked.update((ticked) => {
      const next = new Set(ticked);
      if (on) {
        next.add(check);
      } else {
        next.delete(check);
      }
      return next;
    });
  }

  /**
   * Opens the decision dialog; a confirmed decision is held and the next request is selected.
   *
   * @param decision what the reviewer decides
   */
  decide(decision: DecisionType): void {
    const item = this.selected();
    if (item === null || !this.canDecide()) {
      return;
    }
    const data: DecisionDialogData<DecisionInput> = {
      decision,
      target: null,
      documents: item.documentCount,
      draft: String(item.awardId),
      submit: (entered: DecisionInput) => of(entered),
    };
    this.dialog
      .open<DecisionDialogComponent, DecisionDialogData<DecisionInput>, DecisionInput>(
        DecisionDialogComponent,
        { data, width: '480px' },
      )
      .afterClosed()
      .pipe(filter((entered): entered is DecisionInput => !!entered))
      .subscribe((entered) => {
        const items = this.items();
        const index = items.findIndex((each) => each.awardId === item.awardId);
        this.chosen.set((items[index + 1] ?? items[index - 1])?.awardId ?? null);
        this.held.hold(item.awardId, { decision, requestVersion: item.requestVersion, ...entered });
        this.decided.emit(item.awardId);
      });
  }

  @HostListener('document:keydown', ['$event'])
  shortcut(event: KeyboardEvent): void {
    const action = KEYS[event.code];
    if (!action || event.ctrlKey || event.altKey || event.metaKey || ignored(event.target)) {
      return;
    }
    event.preventDefault();
    if (action === 'next' || action === 'previous') {
      this.move(action === 'next' ? 1 : -1);
    } else {
      this.decide(action);
    }
  }

  title(item: ReviewItem): string {
    return awardTitle(item, this.language.current());
  }

  recipient(item: ReviewItem): string {
    const unit = item.recipient.organization;
    return unit ? organizationName(unit, this.language.current()) : item.owner.name;
  }

  category(item: ReviewItem): string {
    return item.category ? categoryName(item.category, this.language.current()) : '—';
  }
}

/** Shortcuts stay out of the way of typing, of pickers and menus, and of open dialogs. */
function ignored(target: EventTarget | null): boolean {
  if (document.querySelector('mat-dialog-container, .mat-mdc-select-panel, .mat-mdc-menu-panel')) {
    return true;
  }
  const element = target instanceof HTMLElement ? target : null;
  return (
    !!element &&
    (['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName) ||
      element.isContentEditable ||
      !!element.closest('[role="combobox"], [role="listbox"], [role="menu"]'))
  );
}
