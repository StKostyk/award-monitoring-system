import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ReadProblem, readProblem } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { saveFile } from '../../../shared/file-download';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import {
  DocumentsService,
  sizeUnit,
  sizeValue,
} from '../../awards/award-documents/documents.service';
import { awardTitle, categoryName } from '../../awards/awards.service';
import { AchievementDetail, AchievementsService, SharedDocument } from '../achievements.service';

const AWARD_ID = /^\d{1,18}$/;

/** A shared award as a signed-in colleague sees it, with the documents its owner shows. */
@Component({
  selector: 'app-achievement-detail',
  imports: [MatButton, MatIcon, MatProgressBar, RouterLink, TranslocoPipe, KyivDatePipe],
  templateUrl: './achievement-detail.component.html',
  styleUrl: './achievement-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AchievementDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(AchievementsService);
  private readonly documents = inject(DocumentsService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);

  private id = 0;

  protected readonly detail = signal<AchievementDetail | null>(null);
  protected readonly loading = signal(false);
  protected readonly problem = signal<ReadProblem | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly title = computed(() => {
    const detail = this.detail();
    return detail ? awardTitle(detail.achievement, this.language.current()) : '';
  });
  protected readonly description = computed(() => {
    const achievement = this.detail()?.achievement;
    if (!achievement) {
      return null;
    }
    return this.language.current() === 'en'
      ? (achievement.description ?? achievement.descriptionUk)
      : (achievement.descriptionUk ?? achievement.description);
  });
  protected readonly unit = computed(() => {
    const detail = this.detail();
    return detail
      ? organizationName(detail.achievement.recipient.unit, this.language.current())
      : '';
  });
  protected readonly category = computed(() => {
    const detail = this.detail();
    return detail ? categoryName(detail.achievement.category, this.language.current()) : '';
  });

  ngOnInit(): void {
    const param = this.route.snapshot.paramMap.get('id') ?? '';
    if (!AWARD_ID.test(param)) {
      this.problem.set('gone');
      return;
    }
    this.id = Number(param);
    this.load();
  }

  /** Reads the achievement again after a failure. */
  retry(): void {
    this.load();
  }

  /** Saves a shown document under its name; one hidden meanwhile reads the achievement again. */
  download(document: SharedDocument): void {
    this.notice.set(null);
    this.documents
      .download(document.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => saveFile(blob, document.fileName),
        error: (error: unknown) => {
          const gone = readProblem(error) === 'gone';
          this.notice.set(
            gone ? 'awards.documents.problems.gone' : 'awards.documents.problems.download-failed',
          );
          if (gone) {
            this.load();
          }
        },
      });
  }

  /** The size in kilobytes below a megabyte, in megabytes from there on, in the interface language. */
  sizeValue(bytes: number): string {
    return sizeValue(bytes, this.language.current());
  }

  /** Translation key of the unit of `sizeValue`. */
  sizeUnit(bytes: number): string {
    return sizeUnit(bytes);
  }

  private load(): void {
    this.loading.set(true);
    this.problem.set(null);
    this.service
      .detail(this.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.loading.set(false);
          this.detail.set(detail);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.detail.set(null);
          this.problem.set(readProblem(error));
        },
      });
  }
}
