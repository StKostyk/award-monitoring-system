import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatAnchor, MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, catchError, filter, of, switchMap, tap, throwError } from 'rxjs';

import { problemStatus, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { reviewableLevels } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDate } from '../../../shared/date-format';
import { APPROVAL_LEVELS, ApprovalLevel } from '../../awards/awards.service';
import { confirmAction } from '../../awards/confirm-dialog/confirm-dialog.component';
import {
  DecisionDialogComponent,
  DecisionDialogData,
  DecisionInput,
} from '../decision-dialog/decision-dialog.component';
import {
  HandOverDialogComponent,
  HandOverDialogData,
} from '../hand-over-dialog/hand-over-dialog.component';
import { reviewerOf } from '../review-problems';
import {
  DecisionOutcome,
  DecisionType,
  ReviewItem,
  ReviewerCandidate,
  ReviewsService,
  UserRef,
} from '../reviews.service';

/** A translated message with its placeholder values. */
interface Notice {
  key: string;
  params?: Record<string, string>;
}

/** Conflicts after which the request is read again. */
const RELOADING = ['request-claimed', 'request-stale', 'request-closed'];
/** Problem types with a message of their own. */
const KNOWN = ['reviewer-not-eligible', 'no-higher-level', 'validation-failed', 'network'];

/**
 * Who reviews the request of an award and until when, with claim, release, hand-over, take-over, the four
 * decisions and the correction of the award; hidden when the caller may not review the request.
 */
@Component({
  selector: 'app-review-panel',
  imports: [MatAnchor, MatButton, MatChip, MatProgressBar, RouterLink, TranslocoPipe],
  templateUrl: './review-panel.component.html',
  styleUrl: './review-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewPanelComponent implements OnInit {
  readonly awardId = input.required<number>();
  /** Emitted after the request changed, so the award page reads it again. */
  readonly changed = output<void>();
  /** Emitted after a decision, with where the request went. */
  readonly decided = output<DecisionOutcome>();

  private readonly reviews = inject(ReviewsService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly language = inject(LanguageService);
  private readonly levels = reviewableLevels(this.auth.permissions());

  readonly item = signal<ReviewItem | null>(null);
  readonly busy = signal(false);
  readonly notice = signal<Notice | null>(null);

  readonly mine = computed(() => this.holds(this.item()?.reviewer ?? null));
  /** A request held by a colleague the caller outranks; a peer is refused by the server unless the holder left. */
  readonly canTakeOver = computed(() => {
    const item = this.item();
    return (
      item !== null &&
      item.reviewer !== null &&
      !this.mine() &&
      this.levels.indexOf(item.level) < this.levels.length - 1
    );
  });

  /** The holder decides; an unclaimed request is claimed by the decision itself. */
  readonly canDecide = computed(() => {
    const item = this.item();
    return item !== null && (item.reviewer === null || this.mine());
  });
  /** The level an escalation goes to; null at the top. */
  readonly nextLevel = computed<ApprovalLevel | null>(() => {
    const level = this.item()?.level;
    const index = level ? APPROVAL_LEVELS.indexOf(level) : -1;
    return index >= 0 && index < APPROVAL_LEVELS.length - 1 ? APPROVAL_LEVELS[index + 1] : null;
  });

  ngOnInit(): void {
    this.load();
  }

  claim(item: ReviewItem): void {
    this.assign(this.reviews.claim(item.awardId, item.requestVersion));
  }

  release(item: ReviewItem): void {
    this.start();
    this.reviews.release(item.awardId, item.requestVersion).subscribe({
      next: () => {
        this.notice.set({ key: 'reviews.messages.released' });
        this.load();
        this.changed.emit();
      },
      error: (error: unknown) => this.failed(error),
    });
  }

  takeOver(item: ReviewItem): void {
    confirmAction(this.dialog, 'reviews.takeOver', { name: item.reviewer?.name ?? '' })
      .pipe(filter(Boolean))
      .subscribe(() => this.assign(this.reviews.claim(item.awardId, item.requestVersion, true)));
  }

  handOver(item: ReviewItem): void {
    const data: HandOverDialogData = { awardId: item.awardId };
    this.dialog
      .open<HandOverDialogComponent, HandOverDialogData, ReviewerCandidate>(
        HandOverDialogComponent,
        { data, width: '420px' },
      )
      .afterClosed()
      .pipe(
        filter((candidate): candidate is ReviewerCandidate => !!candidate),
        tap(() => this.start()),
        switchMap((candidate) =>
          this.reviews.handOver(item.awardId, item.requestVersion, candidate.id),
        ),
      )
      .subscribe({
        next: (updated) => this.done(updated, 'reviews.messages.handedOver'),
        error: (error: unknown) => this.failed(error),
      });
  }

  decide(item: ReviewItem, decision: DecisionType): void {
    const data: DecisionDialogData<DecisionOutcome> = {
      decision,
      target: this.nextLevel(),
      documents: item.documentCount,
      draft: String(item.awardId),
      submit: (input: DecisionInput) => {
        this.notice.set(null);
        const current = this.item();
        return this.reviews
          .decide(item.awardId, {
            decision,
            requestVersion:
              current?.level === item.level ? current.requestVersion : item.requestVersion,
            ...input,
          })
          .pipe(catchError((error: unknown) => this.reloadAfter(error)));
      },
    };
    this.dialog
      .open<DecisionDialogComponent, DecisionDialogData<DecisionOutcome>, DecisionOutcome>(
        DecisionDialogComponent,
        { data, width: '480px' },
      )
      .afterClosed()
      .pipe(filter((outcome): outcome is DecisionOutcome => !!outcome))
      .subscribe((outcome) => {
        this.item.set(null);
        this.decided.emit(outcome);
      });
  }

  day(value: string | null): string {
    return value ? kyivDate(value, this.language.current()) : '—';
  }

  private assign(call: Observable<ReviewItem>): void {
    this.start();
    call.subscribe({
      next: (updated) => this.done(updated, 'reviews.messages.claimed'),
      error: (error: unknown) => this.failed(error),
    });
  }

  private start(): void {
    this.busy.set(true);
    this.notice.set(null);
  }

  private done(updated: ReviewItem, message: string): void {
    this.busy.set(false);
    this.item.set(updated);
    this.notice.set({ key: message });
    this.changed.emit();
  }

  private reloadAfter(error: unknown): Observable<never> {
    if (!reloads(error)) {
      return throwError(() => error);
    }
    return this.reload().pipe(
      tap(() => this.changed.emit()),
      switchMap(() => throwError(() => error)),
    );
  }

  private failed(error: unknown): void {
    this.busy.set(false);
    const type = problemType(error);
    if (reloads(error)) {
      const holder = type === 'request-claimed' ? reviewerOf(error) : null;
      this.notice.set(
        holder && !this.holds(holder)
          ? { key: 'reviews.problems.request-claimed', params: { name: holder.name } }
          : { key: 'reviews.problems.request-stale' },
      );
      this.load();
      this.changed.emit();
    } else {
      this.notice.set({ key: `reviews.problems.${KNOWN.includes(type) ? type : 'unknown'}` });
    }
  }

  private load(): void {
    this.reload().subscribe();
  }

  private reload(): Observable<ReviewItem | null> {
    this.busy.set(true);
    return this.reviews.item(this.awardId()).pipe(
      catchError(() => of(null)),
      tap((item) => {
        this.item.set(item);
        this.busy.set(false);
      }),
    );
  }

  private holds(reviewer: UserRef | null): boolean {
    return reviewer !== null && String(reviewer.id) === this.auth.userId();
  }
}

function reloads(error: unknown): boolean {
  return RELOADING.includes(problemType(error)) || problemStatus(error) === HttpStatusCode.NotFound;
}
