import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { attachmentName, saveFile } from '../../../../shared/file-download';
import { AnalyticsPeriod } from '../../analytics.service';
import { REPORT_FORMATS, ReportFormat, UnitAnalyticsService } from '../unit-analytics.service';

/** Downloads the report of the shown unit and period as PDF, Excel or CSV. */
@Component({
  selector: 'app-export-menu',
  imports: [MatButton, MatMenu, MatMenuItem, MatMenuTrigger, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './export-menu.component.html',
  styleUrl: './export-menu.component.scss',
})
export class ExportMenuComponent {
  private readonly service = inject(UnitAnalyticsService);
  private readonly language = inject(LanguageService);

  readonly unitId = input.required<number | null>();
  readonly period = input.required<AnalyticsPeriod>();
  readonly disabled = input(false);

  protected readonly formats = REPORT_FORMATS;
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);

  protected download(format: ReportFormat): void {
    const unitId = this.unitId();
    if (unitId === null) {
      return;
    }
    this.busy.set(true);
    this.failed.set(false);
    this.service.exportReport(unitId, this.period(), format, this.language.current()).subscribe({
      next: (response) => {
        this.busy.set(false);
        saveFile(
          response.body ?? new Blob(),
          attachmentName(
            response.headers.get('Content-Disposition'),
            `analytics-${unitId}.${format}`,
          ),
        );
      },
      error: () => {
        this.busy.set(false);
        this.failed.set(true);
      },
    });
  }
}
