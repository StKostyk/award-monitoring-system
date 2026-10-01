import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
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
import { canEditOwnAwards } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../../shared/organization-name';
import { AwardAuditTrailComponent } from '../award-audit-trail/award-audit-trail.component';
import { AwardHistoryComponent } from '../award-history/award-history.component';
import { AwardStatusComponent } from '../award-status/award-status.component';
import { Award, AwardsService, awardTitle, categoryName, isOwnAward } from '../awards.service';
import { confirmRemoval } from '../confirm-dialog/confirm-dialog.component';

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
    TranslocoPipe,
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
  readonly canAudit = computed(() => this.auth.permissions().hasPermission('audit:read'));

  ngOnInit(): void {
    const problem = (history.state as { problem?: string } | null)?.problem;
    if (problem) {
      this.notice.set(`awards.problems.${problem}`);
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
    confirmRemoval(this.dialog)
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
   * Reloads the award after its review status changed or was lost; an award no longer readable is replaced by
   * the not-found notice, any other failure keeps what is shown.
   */
  refresh(id: number): void {
    this.service.get(id).subscribe({
      next: (award) => this.award.set(award),
      error: (error: unknown) => {
        if (readProblem(error) !== 'failed') {
          this.award.set(null);
          this.notFound.set(true);
        }
      },
    });
  }

  /** Drafts are private to their owner; everybody else who may open the award sees it from the submission on. */
  showHistory(award: Award): boolean {
    return award.status !== 'DRAFT' || isOwnAward(award, this.auth.userId());
  }

  /** The caller's own draft, which the caller may delete. */
  ownDraft(award: Award): boolean {
    return award.status === 'DRAFT' && isOwnAward(award, this.auth.userId());
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
