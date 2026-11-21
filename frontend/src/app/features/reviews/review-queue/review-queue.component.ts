import { HttpStatusCode } from '@angular/common/http';
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
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, catchError, filter, forkJoin, map, of, switchMap } from 'rxjs';

import { problemStatus, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import { StatusPageComponent } from '../../../shared/status-page/status-page.component';
import { AwardDocumentsComponent } from '../../awards/award-documents/award-documents.component';
import { AwardStatusComponent } from '../../awards/award-status/award-status.component';
import {
  APPROVAL_LEVELS,
  ApprovalLevel,
  Award,
  AwardsService,
  awardTitle,
  categoryName,
} from '../../awards/awards.service';
import {
  DecisionDialogComponent,
  DecisionDialogData,
  DecisionInput,
} from '../decision-dialog/decision-dialog.component';
import {
  HandOverDialogComponent,
  HandOverDialogData,
} from '../hand-over-dialog/hand-over-dialog.component';
import { HeldDecisionService } from '../held-decision.service';
import { reviewProblemKey } from '../review-problems';
import { DecisionType, ReviewItem, ReviewerCandidate, ReviewsService } from '../reviews.service';

/** The checklist of the queue; ticks are an aid and are neither sent nor kept. */
export const QUEUE_CHECKS = ['readable', 'match', 'level'] as const;

const KEYS: Record<string, 'next' | 'previous' | 'APPROVE' | 'RETURN'> = {
  KeyJ: 'next',
  KeyK: 'previous',
  KeyA: 'APPROVE',
  KeyR: 'RETURN',
};

/** The award of the shown request and its open request at the caller's level, null when there is none. */
interface Detail {
  awardId: number;
  award: Award | null;
  item: ReviewItem | null;
  /** The award could not be read for another reason than not existing (offline, a server error). */
  failed: boolean;
}

/**
 * The pane of the review queue: the selected request with its fields, documents, checklist and route, decided with
 * the buttons or with A and R, and moved through with J and K. A confirmed decision is held for ten seconds by
 * {@link HeldDecisionService}; a request no longer waiting at the caller's level shows its state without actions.
 */
@Component({
  selector: 'app-review-queue',
  imports: [
    AwardDocumentsComponent,
    AwardStatusComponent,
    EmptyStateComponent,
    MatButton,
    MatCheckbox,
    RouterLink,
    StatusPageComponent,
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
  private readonly reviews = inject(ReviewsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly heldDecisions = inject(HeldDecisionService);

  /** The open requests of the page, without those whose decision is held. */
  readonly items = input.required<ReviewItem[]>();
  /** The award shown in the pane; none shows nothing. */
  readonly selectedId = input<number | null>(null);
  readonly loading = input(false);
  /** Emits the request to show next, or null when the queue has none left. */
  readonly selectedChange = output<number | null>();
  /** Emits the award whose decision was confirmed and is now held. */
  readonly decided = output<number>();
  /** Emits after the request changed hands, so the queue is read again. */
  readonly changed = output<void>();

  protected readonly checks = QUEUE_CHECKS;
  readonly busy = signal(false);
  private readonly shownId = this.selectedId;
  private readonly reloads = signal(0);
  /** The award and request of the shown id, read again whenever the queue page changes. */
  readonly detail = toSignal(
    toObservable(
      computed(() => ({ id: this.shownId(), reload: this.reloads(), page: this.items() })),
    ).pipe(switchMap(({ id }) => (id === null ? of(null) : this.load(id)))),
    { initialValue: null },
  );
  /** The shown request while it waits at the caller's level; null when it does not. */
  readonly selected = computed(() => {
    const id = this.shownId();
    const detail = this.detail();
    const fresh = detail?.awardId === id ? detail.item : null;
    const listed = this.items().find((item) => item.awardId === id);
    if (!listed) {
      return fresh;
    }
    return fresh?.requestId === listed.requestId
      ? { ...listed, canEscalate: fresh.canEscalate, canTakeOver: fresh.canTakeOver }
      : listed;
  });
  /** The shown request's decision is held and may still be undone; it is not decided twice. */
  readonly held = computed(() => {
    const id = this.shownId();
    return id !== null && this.heldDecisions.heldIds().has(id);
  });
  readonly failed = computed(() => {
    const detail = this.detail();
    return this.selected() === null && detail?.awardId === this.shownId() && detail.failed;
  });
  readonly award = computed(() => {
    const detail = this.detail();
    return detail && detail.awardId === this.shownId() ? detail.award : null;
  });
  /** The award is known but its request no longer waits for the caller. */
  readonly settled = computed(() => this.selected() === null && this.award() !== null);
  /** The award in the URL is neither waiting nor readable. */
  readonly missing = computed(() => {
    const detail = this.detail();
    return (
      this.selected() === null &&
      detail !== null &&
      detail.awardId === this.shownId() &&
      detail.award === null &&
      !detail.failed
    );
  });
  /** The ticked checks of the selected request, cleared when another one is selected. */
  readonly ticked = linkedSignal<number | null, ReadonlySet<string>>({
    source: () => this.selected()?.awardId ?? null,
    computation: () => new Set<string>(),
  });
  readonly mine = computed(() => {
    const reviewer = this.selected()?.reviewer ?? null;
    return reviewer !== null && String(reviewer.id) === this.auth.userId();
  });
  readonly canDecide = computed(() => {
    const item = this.selected();
    return item !== null && !this.held() && (item.reviewer === null || this.mine());
  });
  /** The level an escalation goes to; null at the top or when the caller's role already covers it. */
  readonly nextLevel = computed<ApprovalLevel | null>(() => {
    const item = this.selected();
    if (item === null || item.canEscalate === false) {
      return null;
    }
    const index = APPROVAL_LEVELS.indexOf(item.level);
    return index >= 0 && index < APPROVAL_LEVELS.length - 1 ? APPROVAL_LEVELS[index + 1] : null;
  });

  /** Moves to the next request, or the previous one with a negative step. */
  move(step: number): void {
    const items = this.items();
    const index = items.findIndex((item) => item.awardId === this.shownId());
    if (index < 0 && step < 0) {
      return;
    }
    const next = index < 0 ? items[0] : items[index + step];
    if (next) {
      this.selectedChange.emit(next.awardId);
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
    if (item === null || !this.canDecide() || (decision === 'ESCALATE' && !this.nextLevel())) {
      return;
    }
    const data: DecisionDialogData<DecisionInput> = {
      decision,
      target: decision === 'ESCALATE' ? this.nextLevel() : null,
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
        const next = index < 0 ? items[0] : (items[index + 1] ?? items[index - 1]);
        this.selectedChange.emit(next?.awardId ?? null);
        this.heldDecisions.hold(item.awardId, {
          decision,
          requestVersion: item.requestVersion,
          ...entered,
        });
        this.decided.emit(item.awardId);
      });
  }

  /** Hands the request to a colleague, claiming it first when nobody holds it. */
  handOver(): void {
    const item = this.selected();
    if (item === null || !this.canDecide()) {
      return;
    }
    const data: HandOverDialogData = { awardId: item.awardId };
    this.dialog
      .open<HandOverDialogComponent, HandOverDialogData, ReviewerCandidate>(
        HandOverDialogComponent,
        { data, width: '420px' },
      )
      .afterClosed()
      .pipe(
        filter((candidate): candidate is ReviewerCandidate => !!candidate),
        switchMap((candidate) => {
          this.busy.set(true);
          const held: Observable<ReviewItem> = this.mine()
            ? of(item)
            : this.reviews.claim(item.awardId, item.requestVersion);
          return held.pipe(
            switchMap((claimed) =>
              this.reviews.handOver(item.awardId, claimed.requestVersion, candidate.id),
            ),
          );
        }),
      )
      .subscribe({
        next: () => this.after('reviews.messages.handedOver'),
        error: (error: unknown) => this.after(reviewProblemKey(problemType(error))),
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

  title(item: Pick<Award, 'title' | 'titleUk'>): string {
    return awardTitle(item, this.language.current());
  }

  recipient(item: ReviewItem): string {
    const unit = item.recipient.organization;
    return unit ? organizationName(unit, this.language.current()) : item.owner.name;
  }

  unit(item: ReviewItem): string {
    return item.organization ? organizationName(item.organization, this.language.current()) : '—';
  }

  category(item: ReviewItem): string {
    return item.category ? categoryName(item.category, this.language.current()) : '—';
  }

  private load(awardId: number): Observable<Detail> {
    let failed = false;
    return forkJoin({
      award: this.awards.get(awardId).pipe(
        catchError((error: unknown) => {
          failed = problemStatus(error) !== HttpStatusCode.NotFound;
          return of(null);
        }),
      ),
      item: this.reviews.item(awardId).pipe(catchError(() => of(null))),
    }).pipe(map((loaded) => ({ awardId, ...loaded, failed })));
  }

  private after(message: string): void {
    this.busy.set(false);
    this.snackBar.open(this.transloco.translate(message), undefined, { duration: 5000 });
    this.reloads.update((count) => count + 1);
    this.changed.emit();
  }
}

const NON_TEXT_INPUTS = new Set([
  'checkbox',
  'radio',
  'button',
  'submit',
  'reset',
  'range',
  'color',
  'file',
  'image',
]);

/** Shortcuts stay out of the way of typing, of pickers and menus, and of open dialogs. */
function ignored(target: EventTarget | null): boolean {
  if (document.querySelector('mat-dialog-container, .mat-mdc-select-panel, .mat-mdc-menu-panel')) {
    return true;
  }
  const element = target instanceof HTMLElement ? target : null;
  return (
    !!element &&
    ((element instanceof HTMLInputElement && !NON_TEXT_INPUTS.has(element.type)) ||
      ['TEXTAREA', 'SELECT'].includes(element.tagName) ||
      element.isContentEditable ||
      !!element.closest('[role="combobox"], [role="listbox"], [role="menu"]'))
  );
}
