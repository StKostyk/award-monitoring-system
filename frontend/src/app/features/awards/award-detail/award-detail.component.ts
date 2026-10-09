import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTab, MatTabContent, MatTabGroup, MatTabLabel } from '@angular/material/tabs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, switchMap, tap } from 'rxjs';

import { problemStatus, problemType, readProblem } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { canEditOwnAwards, canReview } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import { ReviewPanelComponent } from '../../reviews/review-panel/review-panel.component';
import { DecisionOutcome } from '../../reviews/reviews.service';
import { AwardAuditTrailComponent } from '../award-audit-trail/award-audit-trail.component';
import { AwardDocumentsComponent } from '../award-documents/award-documents.component';
import { AwardHistoryComponent } from '../award-history/award-history.component';
import { AwardStatusComponent } from '../award-status/award-status.component';
import {
  ApprovalLevel,
  Award,
  AwardsService,
  UnitRef,
  WITHDRAWABLE_REQUEST_STATUSES,
  awardTitle,
  categoryName,
  isOwnAward,
} from '../awards.service';
import { confirmAction } from '../confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-award-detail',
  imports: [
    RouterLink,
    MatButton,
    MatChip,
    MatProgressBar,
    MatTabGroup,
    MatTab,
    MatTabLabel,
    MatTabContent,
    AwardHistoryComponent,
    AwardAuditTrailComponent,
    AwardStatusComponent,
    AwardDocumentsComponent,
    ReviewPanelComponent,
    TranslocoPipe,
    KyivDatePipe,
  ],
  templateUrl: './award-detail.component.html',
  styleUrl: './award-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly service = inject(AwardsService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  readonly award = signal<Award | null>(null);
  readonly loading = signal(false);
  readonly notFound = signal(false);
  readonly failed = signal(false);
  readonly notice = signal<string | null>(null);
  /** The result of the caller's last decision, with the level the request now waits at. */
  readonly decision = signal<{ key: string; level: ApprovalLevel } | null>(null);
  private readonly statusPanel = viewChild(AwardStatusComponent);
  private readonly historyPanel = viewChild(AwardHistoryComponent);
  readonly canAudit = computed(() => this.auth.permissions().hasPermission('audit:read'));

  ngOnInit(): void {
    const state = history.state as { problem?: string; notice?: string } | null;
    if (state?.problem) {
      this.notice.set(`awards.problems.${state.problem}`);
    } else if (state?.notice) {
      this.notice.set(state.notice);
    }
    const param = this.route.snapshot.paramMap.get('id') ?? '';
    if (!/^\d+$/.test(param)) {
      this.notFound.set(true);
      return;
    }
    this.loading.set(true);
    this.service.get(Number(param)).subscribe({
      next: (award) => {
        this.award.set(award);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (problemStatus(error) === HttpStatusCode.NotFound) {
          this.notFound.set(true);
        } else {
          this.failed.set(true);
        }
      },
    });
  }

  remove(award: Award): void {
    confirmAction(this.dialog, 'awards.remove')
      .pipe(
        filter(Boolean),
        tap(() => this.loading.set(true)),
        switchMap(() => this.service.remove(award.id)),
      )
      .subscribe({
        next: () => this.removed(),
        error: (error: unknown) => {
          this.loading.set(false);
          if (problemStatus(error) === HttpStatusCode.NotFound) {
            this.removed();
          } else {
            this.notice.set(`awards.problems.${problemType(error)}`);
          }
        },
      });
  }

  /**
   * Reloads the award and its history after its review status changed or was lost; an award no longer readable
   * is replaced by the not-found notice, any other failure keeps what is shown.
   */
  refresh(id: number): void {
    this.service.get(id).subscribe({
      next: (award) => {
        this.award.set(award);
        this.historyPanel()?.reload();
      },
      error: (error: unknown) => {
        if (readProblem(error) !== 'failed') {
          this.award.set(null);
          this.notFound.set(true);
        }
      },
    });
  }

  /**
   * Shows where a decision took the request and reloads the award and its status; a returned award is a draft
   * again and readable only by its owner, so the reviewer goes back to the queue.
   */
  onDecided(outcome: DecisionOutcome): void {
    if (outcome.status === 'DRAFT') {
      void this.router.navigate(['/reviews'], {
        replaceUrl: true,
        state: { notice: `reviews.messages.${outcome.requestStatus}` },
      });
      return;
    }
    this.decision.set({ key: `reviews.messages.${outcome.requestStatus}`, level: outcome.level });
    this.refresh(outcome.awardId);
    this.statusPanel()?.load();
  }

  /** Drafts are private to their owner; everybody else who may open the award sees it from the submission on. */
  showHistory(award: Award): boolean {
    return award.status !== 'DRAFT' || isOwnAward(award, this.auth.userId());
  }

  /** A pending award of somebody else, which an approver may be able to review. */
  reviewable(award: Award): boolean {
    return (
      award.status === 'PENDING' &&
      canReview(this.auth.permissions()) &&
      !isOwnAward(award, this.auth.userId())
    );
  }

  /** The caller's own draft, which the caller may delete. */
  ownDraft(award: Award): boolean {
    return award.status === 'DRAFT' && isOwnAward(award, this.auth.userId());
  }

  /** An own draft never submitted; a returned or withdrawn draft keeps its request and decisions. */
  removable(award: Award): boolean {
    return this.ownDraft(award) && !award.request;
  }

  /** The caller's pending award that no reviewer has claimed yet. */
  withdrawable(award: Award): boolean {
    return (
      award.status === 'PENDING' &&
      isOwnAward(award, this.auth.userId()) &&
      canEditOwnAwards(this.auth.permissions()) &&
      !!award.request &&
      WITHDRAWABLE_REQUEST_STATUSES.includes(award.request.status)
    );
  }

  /**
   * Withdraws the award after confirmation and opens it in the form; a refusal shows why and reloads the award,
   * since a reviewer may have claimed it in the meantime.
   */
  withdraw(award: Award): void {
    confirmAction(this.dialog, 'awards.withdraw')
      .pipe(
        filter(Boolean),
        tap(() => this.loading.set(true)),
        switchMap(() => this.service.withdraw(award.id, award.version)),
      )
      .subscribe({
        next: (draft) => {
          this.loading.set(false);
          void this.router.navigate(['/awards', draft.id, 'edit']);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.notice.set(`awards.problems.${problemType(error)}`);
          this.refresh(award.id);
        },
      });
  }

  /** An own draft the caller may also change. */
  editable(award: Award): boolean {
    return this.ownDraft(award) && canEditOwnAwards(this.auth.permissions());
  }

  title(award: Award): string {
    return awardTitle(award, this.language.current());
  }

  category(award: Award): string {
    return award.category ? categoryName(award.category, this.language.current()) : '';
  }

  unitName(unit: UnitRef): string {
    return organizationName(unit, this.language.current());
  }

  organization(award: Award): string {
    return organizationName(award.organization, this.language.current());
  }

  private removed(): void {
    this.loading.set(false);
    void this.router.navigate(['/awards'], {
      replaceUrl: true,
      state: { notice: 'awards.messages.removed' },
    });
  }
}
