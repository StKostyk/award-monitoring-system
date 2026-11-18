import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { attachmentName, saveFile } from '../../../shared/file-download';
import { IconComponent } from '../../../shared/icons/icon.component';
import { AnalyticsService, CV_FORMATS, CvFormat } from '../analytics.service';

/** Downloads the caller's approved awards as a CV list, PDF or text, in the language of the page. */
@Component({
  selector: 'app-cv-export',
  imports: [IconComponent, MatButton, MatMenu, MatMenuItem, MatMenuTrigger, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cv-export.component.html',
  styleUrl: './cv-export.component.scss',
})
export class CvExportComponent {
  private readonly service = inject(AnalyticsService);
  private readonly language = inject(LanguageService);

  readonly disabled = input(false);

  protected readonly formats = CV_FORMATS;
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);

  protected download(format: CvFormat): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failed.set(false);
    this.service.exportCv(format, this.language.current()).subscribe({
      next: (response) => {
        this.busy.set(false);
        saveFile(
          response.body ?? new Blob(),
          attachmentName(
            response.headers.get('Content-Disposition'),
            format === 'pdf' ? 'awards-cv.pdf' : 'awards-cv.txt',
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
