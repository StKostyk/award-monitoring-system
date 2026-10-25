import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { attachmentName, saveFile } from '../../../../shared/file-download';
import { AnalyticsPeriod } from '../../analytics.service';
import { UniversityAnalyticsService } from '../university-analytics.service';

/** Downloads the board report of the university for the shown period as a PDF. */
@Component({
  selector: 'app-board-report-button',
  imports: [MatButton, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './board-report-button.component.html',
  styleUrl: './board-report-button.component.scss',
})
export class BoardReportButtonComponent {
  private readonly service = inject(UniversityAnalyticsService);
  private readonly language = inject(LanguageService);

  readonly period = input.required<AnalyticsPeriod>();
  readonly disabled = input(false);

  protected readonly busy = signal(false);
  protected readonly failed = signal(false);

  protected download(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failed.set(false);
    this.service.exportReport(this.period(), this.language.current()).subscribe({
      next: (response) => {
        this.busy.set(false);
        saveFile(
          response.body ?? new Blob(),
          attachmentName(response.headers.get('Content-Disposition'), 'board-report.pdf'),
        );
      },
      error: () => {
        this.busy.set(false);
        this.failed.set(true);
      },
    });
  }
}
