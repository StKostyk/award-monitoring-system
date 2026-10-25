import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { kyivDateTime } from '../../../../shared/date-format';
import { ComplianceSummary } from '../university-analytics.service';

/** A figure of the compliance panel: a label, a value and an optional note under it. */
export interface ComplianceTile {
  id: string;
  label: string;
  value: string;
  note: string | null;
  warn: boolean;
}

const PERCENT = 100;

/** The compliance summary of the university: status, the reasons for attention and the figures behind them. */
@Component({
  selector: 'app-compliance-panel',
  imports: [MatButton, MatIcon, MatProgressBar, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './compliance-panel.component.html',
  styleUrl: './compliance-panel.component.scss',
})
export class CompliancePanelComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly compliance = input.required<ComplianceSummary | null>();
  readonly loading = input.required<boolean>();
  readonly problem = input.required<string | null>();
  readonly refresh = output();

  protected readonly asOf = computed(() => {
    const language = this.language.current();
    const summary = this.compliance();
    return summary ? kyivDateTime(summary.generatedAt, language) : '';
  });
  protected readonly reasons = computed(() => {
    this.language.current();
    return (this.compliance()?.attention ?? []).map((reason) => ({
      key: reason,
      text: this.transloco.translate(`universityAnalytics.compliance.reasons.${reason}`),
    }));
  });
  protected readonly tiles = computed<ComplianceTile[]>(() => {
    const language = this.language.current();
    const summary = this.compliance();
    if (!summary) {
      return [];
    }
    const t = (key: string, params?: Record<string, unknown>): string =>
      this.transloco.translate(`universityAnalytics.compliance.${key}`, params);
    const run = summary.lastRetentionRun;
    return [
      {
        id: 'notice',
        label: t('tiles.notice', { version: summary.privacyNotice.version }),
        value: `${summary.privacyNotice.acknowledged} / ${summary.privacyNotice.accounts}`,
        note: percent(summary.privacyNotice.acknowledged, summary.privacyNotice.accounts),
        warn: false,
      },
      {
        id: 'consent',
        label: t('tiles.consent'),
        value: `${summary.analyticsConsent.granted} / ${summary.analyticsConsent.accounts}`,
        note: percent(summary.analyticsConsent.granted, summary.analyticsConsent.accounts),
        warn: false,
      },
      {
        id: 'erasure-pending',
        label: t('tiles.erasurePending'),
        value: String(summary.erasure.pending),
        note: null,
        warn: false,
      },
      {
        id: 'erasure-postponed',
        label: t('tiles.erasurePostponed'),
        value: String(summary.erasure.postponed),
        note: null,
        warn: false,
      },
      {
        id: 'erasure-overdue',
        label: t('tiles.erasureOverdue'),
        value: String(summary.erasure.overdue),
        note: null,
        warn: summary.erasure.overdue > 0,
      },
      {
        id: 'legal-holds',
        label: t('tiles.legalHolds'),
        value: String(summary.legalHolds.active),
        note: null,
        warn: false,
      },
      {
        id: 'retention',
        label: t('tiles.retention'),
        value: run ? t(`runStatus.${run.status}`) : t('noRun'),
        note: run ? kyivDateTime(run.finishedAt ?? run.startedAt, language) : null,
        warn: summary.attention.some((reason) => reason.startsWith('RETENTION_')),
      },
      {
        id: 'reviews-open',
        label: t('tiles.reviewsOpen'),
        value: String(summary.reviews.open),
        note: null,
        warn: false,
      },
      {
        id: 'reviews-overdue',
        label: t('tiles.reviewsOverdue'),
        value: String(summary.reviews.overdue),
        note: null,
        warn: summary.reviews.overdue > 0,
      },
    ];
  });
}

function percent(part: number, whole: number): string | null {
  return whole === 0 ? null : `${Math.round((part * PERCENT) / whole)} %`;
}
