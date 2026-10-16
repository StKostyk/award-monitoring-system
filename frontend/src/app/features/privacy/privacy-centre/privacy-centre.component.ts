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
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import {
  MatExpansionPanel,
  MatExpansionPanelHeader,
  MatExpansionPanelTitle,
} from '@angular/material/expansion';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemStatus, problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDate, kyivDateTime } from '../../../shared/date-format';
import { attachmentName, saveFile } from '../../../shared/file-download';
import { PagedList } from '../../../shared/paged-list';
import { ProfileService } from '../../profile/profile.service';
import { ConsentOverview, ConsentsService } from '../consents.service';
import { ErasureDialogComponent } from '../erasure-dialog/erasure-dialog.component';
import {
  ActivityEntry,
  ErasureRequest,
  PrivacyCentreService,
  PrivacySummary,
  Visibility,
} from '../privacy-centre.service';

const DEFAULT_EXPORT_NAME = 'award-monitoring-export.json';

const PAGE_SIZE = 20;

/** Audit actions with a label under `privacy.activity.actions`; any other code is shown as it is. */
export const LABELLED_ACTIONS: ReadonlySet<string> = new Set([
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'ACCOUNT_LOCKED',
  'LOGOUT',
  'PASSWORD_RESET_REQUESTED',
  'PASSWORD_RESET',
  'EMAIL_VERIFIED',
  'SECURITY_REVOKE',
  'REGISTRATION_REPLACED',
  'ACCESS_DENIED',
  'ROLE_ASSIGNED',
  'ROLE_REVOKED',
  'DELEGATION_CREATED',
  'DELEGATION_REVOKED',
  'AWARD_SUBMITTED',
  'REVIEW_CLAIMED',
  'REVIEW_RELEASED',
  'REVIEW_HANDED_OVER',
  'REVIEW_TAKEN_OVER',
  'REVIEW_DECISION',
  'AWARD_WITHDRAWN',
  'REVIEW_BATCH',
  'REVIEW_PERIOD_CHANGED',
  'REVIEW_OVERDUE_NOTICED',
  'AWARD_CORRECTED',
  'AWARD_VISIBILITY_CHANGED',
  'PROFILE_UPDATED',
  'EMAIL_CHANGE_REQUESTED',
  'EMAIL_CHANGED',
  'DATA_EXPORT',
  'AUDIT_EXPORT',
  'DOCUMENT_DOWNLOAD',
  'DOCUMENT_REJECTED',
  'DOCUMENT_SHARING_CHANGED',
  'NOTIFICATION_PREFERENCES_CHANGED',
  'PRIVACY_NOTICE_ACKNOWLEDGED',
  'CONSENT_GRANTED',
  'CONSENT_WITHDRAWN',
  'LEGAL_HOLD_PLACED',
  'LEGAL_HOLD_LIFTED',
  'ERASURE_REQUESTED',
  'ERASURE_CANCELLED',
]);

export const VISIBILITIES: readonly Visibility[] = ['PRIVATE', 'UNIVERSITY', 'PUBLIC'];

/** The attachment name of a Content-Disposition header, or a default. */
export function fileName(disposition: string | null): string {
  return attachmentName(disposition, DEFAULT_EXPORT_NAME);
}

@Component({
  selector: 'app-privacy-centre',
  imports: [
    MatButton,
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatExpansionPanelTitle,
    MatProgressBar,
    MatSlideToggle,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './privacy-centre.component.html',
  styleUrl: './privacy-centre.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrivacyCentreComponent implements OnInit {
  private readonly consents = inject(ConsentsService);
  private readonly centre = inject(PrivacyCentreService);
  private readonly profile = inject(ProfileService);
  private readonly language = inject(LanguageService);
  private readonly dialog = inject(MatDialog);

  readonly visibilities = VISIBILITIES;
  readonly overview = signal<ConsentOverview | null>(null);
  readonly overviewFailed = signal(false);
  readonly summary = signal<PrivacySummary | null>(null);
  readonly summaryFailed = signal(false);
  readonly savingConsent = signal(false);
  readonly consentProblem = signal<string | null>(null);
  readonly exporting = signal(false);
  readonly exportMessage = signal<string | null>(null);
  readonly exportProblem = signal<string | null>(null);
  readonly erasure = this.centre.erasure;
  readonly cancellingErasure = signal(false);
  readonly erasureMessage = signal<string | null>(null);
  readonly erasureProblem = signal<string | null>(null);

  readonly analytics = computed(
    () =>
      this.overview()?.consents.find((consent) => consent.type === 'ANALYTICS')?.granted ?? false,
  );
  readonly history = computed(() => [...(this.overview()?.history ?? [])].reverse());

  private readonly list = new PagedList<ActivityEntry, string>({
    fetch: (page) => this.centre.activity(page, PAGE_SIZE),
    key: (entry) => `${entry.occurredAt}|${entry.action}|${entry.entityType}|${entry.entityId}`,
  });

  readonly activity = this.list.items;
  readonly activityLoading = this.list.loading;
  readonly activityProblem = this.list.problem;
  readonly activityHasMore = this.list.hasMore;

  ngOnInit(): void {
    this.consents
      .overview()
      .then((overview) => this.overview.set(overview))
      .catch(() => this.overviewFailed.set(true));
    this.loadSummary();
    this.list.load();
  }

  loadMore(): void {
    this.list.load();
  }

  chooseAnalytics(change: MatSlideToggleChange): void {
    this.savingConsent.set(true);
    this.consentProblem.set(null);
    this.consents.choose('ANALYTICS', change.checked).subscribe({
      next: (overview) => {
        this.overview.set(overview);
        this.savingConsent.set(false);
      },
      error: () => {
        change.source.checked = !change.checked;
        this.consentProblem.set('privacy.centre.consents.failed');
        this.savingConsent.set(false);
      },
    });
  }

  downloadData(): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.exportMessage.set(null);
    this.exportProblem.set(null);
    this.profile.exportData().subscribe({
      next: (response) => {
        saveFile(
          response.body ?? new Blob(),
          fileName(response.headers.get('Content-Disposition')),
        );
        this.exportMessage.set('privacy.centre.data.downloaded');
        this.exporting.set(false);
        this.loadSummary();
        this.list.reload();
      },
      error: (err: unknown) => {
        this.exportProblem.set(
          problemStatus(err) === HttpStatusCode.TooManyRequests
            ? 'privacy.centre.data.tooMany'
            : 'privacy.centre.data.failed',
        );
        this.exporting.set(false);
      },
    });
  }

  openErasure(): void {
    this.erasureMessage.set(null);
    this.erasureProblem.set(null);
    this.dialog
      .open<ErasureDialogComponent, unknown, ErasureRequest>(ErasureDialogComponent, {
        width: '520px',
        maxWidth: '95vw',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((request) => {
        if (request) {
          this.erasureMessage.set('privacy.erasure.requested');
          this.list.reload();
        } else {
          this.loadSummary();
        }
      });
  }

  cancelErasure(): void {
    if (this.cancellingErasure()) {
      return;
    }
    this.cancellingErasure.set(true);
    this.erasureMessage.set(null);
    this.erasureProblem.set(null);
    this.centre.cancelErasure().subscribe({
      next: () => {
        this.erasureMessage.set('privacy.erasure.cancelled');
        this.cancellingErasure.set(false);
        this.list.reload();
      },
      error: (err: unknown) => {
        this.cancellingErasure.set(false);
        if (problemType(err) === 'erasure-not-pending') {
          this.erasureProblem.set('privacy.erasure.problems.erasure-not-pending');
          this.loadSummary();
        } else {
          this.erasureProblem.set('privacy.erasure.problems.cancelFailed');
        }
      },
    });
  }

  date(iso: string): string {
    return kyivDate(iso, this.language.current());
  }

  time(iso: string): string {
    return kyivDateTime(iso, this.language.current());
  }

  actionKey(action: string): string | null {
    return LABELLED_ACTIONS.has(action) ? `privacy.activity.actions.${action}` : null;
  }

  private loadSummary(): void {
    this.summaryFailed.set(false);
    this.centre.summary().subscribe({
      next: (summary) => this.summary.set(summary),
      error: () => this.summaryFailed.set(true),
    });
  }
}
