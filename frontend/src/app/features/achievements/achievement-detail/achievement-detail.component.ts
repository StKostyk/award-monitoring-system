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
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { ReadProblem, readProblem } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDateTime } from '../../../shared/date-format';
import { saveFile } from '../../../shared/file-download';
import { IconComponent } from '../../../shared/icons/icon.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import { initials } from '../../../shared/widgets/top-widget.component';
import { DocumentThumbnailComponent } from '../../awards/award-documents/document-thumbnail.component';
import {
  DocumentsService,
  sizeUnit,
  sizeValue,
} from '../../awards/award-documents/documents.service';
import {
  AwardVersion,
  AwardsService,
  ReviewDecisionType,
  awardTitle,
  categoryName,
} from '../../awards/awards.service';
import { AchievementDetail, AchievementsService, SharedDocument } from '../achievements.service';
import { ReviewPathStep, reviewPath } from './review-path';

const AWARD_ID = /^\d{1,18}$/;
const VERSIONS_READ = 50;
const MAX_VERSION_PAGES = 20;
const TONES: Record<ReviewDecisionType, string> = {
  APPROVED: 'ok',
  REJECTED: 'bad',
  RETURNED: 'wait',
  ESCALATED: 'neutral',
};

/** The review history while it loads, as the owner and reviewers see it, or only the final state for others. */
export type PathView =
  { state: 'loading' } | { state: 'steps'; steps: ReviewPathStep[] } | { state: 'final' };

/**
 * A shared award as a signed-in colleague sees it: the first shown document on a stage, the facts beside it and the
 * review path for the owner and the reviewers.
 */
@Component({
  selector: 'app-achievement-detail',
  imports: [
    MatButton,
    IconComponent,
    MatProgressBar,
    RouterLink,
    TranslocoPipe,
    KyivDatePipe,
    DocumentThumbnailComponent,
  ],
  templateUrl: './achievement-detail.component.html',
  styleUrl: './achievement-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AchievementDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(AchievementsService);
  private readonly documents = inject(DocumentsService);
  private readonly awards = inject(AwardsService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);

  private id = 0;

  protected readonly detail = signal<AchievementDetail | null>(null);
  protected readonly loading = signal(false);
  protected readonly problem = signal<ReadProblem | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly path = signal<PathView>({ state: 'loading' });
  protected readonly steps = computed(() => {
    const path = this.path();
    return path.state === 'steps' ? path.steps : [];
  });

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

  protected readonly initials = computed(() => {
    const recipient = this.detail()?.achievement.recipient;
    return recipient?.type === 'PERSON' ? initials(recipient.personName ?? '') : '';
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

  /** How a step is marked: approved and rejected in their colours, a return as a warning. */
  tone(step: ReviewPathStep): string {
    if (step.kind !== 'decision') {
      return 'neutral';
    }
    return TONES[step.decision];
  }

  /** Date and time in Kyiv in the interface language. */
  time(iso: string): string {
    return kyivDateTime(iso, this.language.current());
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
          const first = this.detail() === null;
          this.detail.set(detail);
          if (first) {
            this.loadPath();
          }
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.detail.set(null);
          this.problem.set(readProblem(error));
        },
      });
  }

  /** The review path reads the status of the award, which only its owner and its reviewers may. */
  private loadPath(): void {
    this.path.set({ state: 'loading' });
    forkJoin({
      status: this.awards.status(this.id),
      versions: this.allVersions().pipe(catchError(() => of([]))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ status, versions }) => {
          const steps = reviewPath(status, versions);
          this.path.set(steps.length ? { state: 'steps', steps } : { state: 'final' });
        },
        error: () => this.path.set({ state: 'final' }),
      });
  }

  /** Every saved version: the first page tells how many more there are, up to `MAX_VERSION_PAGES`. */
  private allVersions(): Observable<AwardVersion[]> {
    return this.awards.versions(this.id, 0, VERSIONS_READ).pipe(
      switchMap((first) => {
        const pages = Math.min(first.totalPages, MAX_VERSION_PAGES);
        if (pages <= 1) {
          return of(first.content);
        }
        const rest = Array.from({ length: pages - 1 }, (_, index) =>
          this.awards.versions(this.id, index + 1, VERSIONS_READ).pipe(map((page) => page.content)),
        );
        return forkJoin(rest).pipe(map((contents) => [first.content, ...contents].flat()));
      }),
    );
  }
}
