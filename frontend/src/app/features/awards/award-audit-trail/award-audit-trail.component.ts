import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
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
import { attachmentName, saveFile } from '../../../shared/file-download';
import { kyivDateTime } from '../award-history/version-values';
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

  readonly rows = signal<AuditTrailEntry[]>([]);
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly notice = signal<string | null>(null);
  private readonly loadedPages = signal(0);
  private readonly totalPages = signal(0);

  readonly hasMore = computed(() => this.loadedPages() < this.totalPages());

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.service.auditTrail(this.awardId(), this.loadedPages(), PAGE_SIZE).subscribe({
      next: (page) => {
        this.rows.update((shown) => [
          ...shown,
          ...page.content.filter((row) => !shown.some((known) => known.id === row.id)),
        ]);
        this.loadedPages.update((pages) => pages + 1);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.failed.set(problemStatus(error) !== HttpStatusCode.NotFound);
      },
    });
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
      },
      error: () => this.notice.set('awards.audit.exportFailed'),
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
