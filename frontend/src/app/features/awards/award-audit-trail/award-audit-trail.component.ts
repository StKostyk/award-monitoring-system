import { HttpStatusCode } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MatAccordion,
  MatExpansionPanel,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader,
  MatExpansionPanelTitle,
} from '@angular/material/expansion';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemStatus } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDateTime } from '../../../shared/date-format';
import { attachmentName, saveFile } from '../../../shared/file-download';
import { PagedList } from '../../../shared/paged-list';
import { AuditTrailEntry, AwardsService, TRUNCATED_HEADER } from '../awards.service';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-award-audit-trail',
  imports: [
    MatAccordion,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatExpansionPanelTitle,
    MatExpansionPanelDescription,
    MatButton,
    MatProgressBar,
    TranslocoPipe,
  ],
  templateUrl: './award-audit-trail.component.html',
  styleUrl: './award-audit-trail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardAuditTrailComponent implements OnInit {
  private readonly service = inject(AwardsService);
  private readonly language = inject(LanguageService);

  readonly awardId = input.required<number>();

  private readonly list = new PagedList<AuditTrailEntry, number>({
    fetch: (page) => this.service.auditTrail(this.awardId(), page, PAGE_SIZE),
    key: (row) => row.id,
    notFoundIsEmpty: true,
  });

  readonly rows = this.list.items;
  readonly loading = this.list.loading;
  readonly problem = this.list.problem;
  readonly hasMore = this.list.hasMore;
  readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.list.load();
  }

  exportCsv(): void {
    const id = this.awardId();
    this.notice.set(null);
    this.service.exportAuditTrail(id).subscribe({
      next: (response) => {
        saveFile(
          response.body ?? new Blob(),
          attachmentName(response.headers.get('Content-Disposition'), `award-${id}-audit.csv`),
        );
        if (response.headers.get(TRUNCATED_HEADER) === 'true') {
          this.notice.set('awards.audit.truncated');
        }
        this.list.reload();
      },
      error: (error: unknown) =>
        this.notice.set(
          problemStatus(error) === HttpStatusCode.Forbidden
            ? 'awards.audit.problems.denied'
            : 'awards.audit.exportFailed',
        ),
    });
  }

  time(row: AuditTrailEntry): string {
    return kyivDateTime(row.createdAt, this.language.current());
  }

  actor(row: AuditTrailEntry): string {
    return row.actorName ?? row.actorEmail ?? (row.actorId === null ? '—' : `#${row.actorId}`);
  }

  json(values: Record<string, unknown>): string {
    return Object.keys(values).length ? JSON.stringify(values, null, 2) : '—';
  }
}
