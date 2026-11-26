import { HttpEventType, HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, concatMap, map, of, tap } from 'rxjs';

import { knownProblem, problemStatus, problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { IconComponent } from '../../../shared/icons/icon.component';
import { TITLE_LIMIT } from '../award-fields';
import {
  ACCEPTED_FORMATS,
  AwardDocument,
  DocumentsService,
  MAX_DOCUMENT_SIZE,
  isAcceptedFile,
} from '../award-documents/documents.service';
import { AwardForm, AwardsService } from '../awards.service';

const PERCENT = 100;
const KNOWN_PROBLEMS = [
  'empty-file',
  'file-too-large',
  'unsupported-type',
  'content-mismatch',
  'malware-detected',
  'storage-quota',
  'too-many-requests',
];

/** Where the step stands: waiting for a file, sending it, or stopped by a problem. */
export type UploadStepState = 'idle' | 'uploading' | 'unavailable' | 'failed';

/** The award the step saved and the document it attached; both null when the person goes on without a file. */
export interface UploadStepResult {
  awardId: number | null;
  document: AwardDocument | null;
}

/** The title a draft takes from its document: the file name without extension, separators turned into spaces. */
export function titleFromFile(name: string): string {
  const base = name
    .replace(/\.[^.]+$/, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return (base || name).slice(0, TITLE_LIMIT);
}

/**
 * The first step of a new award: the person drops or picks the document, the step saves a draft titled after the
 * file and uploads the file to it with progress, then hands over to the form. The person may also go on without a
 * file.
 */
@Component({
  selector: 'app-award-upload-step',
  imports: [MatButton, MatProgressBar, IconComponent, TranslocoPipe],
  templateUrl: './award-upload-step.component.html',
  styleUrl: './award-upload-step.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardUploadStepComponent {
  private readonly awards = inject(AwardsService);
  private readonly documents = inject(DocumentsService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);
  private awardId: number | null = null;
  private creationKey: string | null = null;

  /** Emits when the form should open, with the saved draft and its document when there is one. */
  readonly done = output<UploadStepResult>();
  readonly accept = Object.entries(ACCEPTED_FORMATS)
    .flatMap(([mime, extensions]) => [mime, ...extensions.map((extension) => `.${extension}`)])
    .join(',');
  readonly state = signal<UploadStepState>('idle');
  readonly progress = signal(0);
  readonly fileName = signal<string | null>(null);
  readonly problem = signal<string | null>(null);
  readonly dragging = signal(false);

  picked(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      this.send(file);
    }
  }

  dragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  dropped(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files[0];
    if (file) {
      this.send(file);
    }
  }

  /** Opens the form without a document, on the draft already saved when there is one. */
  skip(): void {
    this.done.emit({ awardId: this.awardId, document: null });
  }

  /**
   * Checks the file against the upload rules, saves the draft once and uploads the file to it.
   *
   * @param file the chosen document
   */
  send(file: File): void {
    if (this.state() === 'uploading') {
      return;
    }
    const refusal = this.refusal(file);
    this.fileName.set(file.name);
    if (refusal !== null) {
      this.state.set('failed');
      this.problem.set(refusal);
      return;
    }
    this.state.set('uploading');
    this.problem.set(null);
    this.progress.set(0);
    this.target(file)
      .pipe(
        concatMap((awardId) => this.documents.upload(awardId, file, 'CERTIFICATE')),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((PERCENT * event.loaded) / event.total));
          } else if (event.type === HttpEventType.Response && event.body) {
            this.done.emit({ awardId: this.awardId, document: event.body });
          }
        },
        error: (error: unknown) => this.failed(error),
      });
  }

  /** The draft to upload to, saved on the first file. */
  private target(file: File): Observable<number> {
    if (this.awardId !== null) {
      return of(this.awardId);
    }
    this.creationKey ??= crypto.randomUUID();
    return this.awards.create(this.draft(file), this.creationKey).pipe(
      map(({ award }) => award.id),
      tap((id) => {
        this.awardId = id;
        this.creationKey = null;
      }),
    );
  }

  private draft(file: File): AwardForm {
    const title = titleFromFile(file.name);
    const ukrainian = this.language.current() === 'uk';
    return {
      title: ukrainian ? null : title,
      titleUk: ukrainian ? title : null,
      description: null,
      descriptionUk: null,
      categoryId: null,
      awardingOrganization: null,
      awardDate: null,
      externalUrl: null,
      recipientOrganizationId: null,
    };
  }

  private failed(error: unknown): void {
    if (problemStatus(error) === HttpStatusCode.ServiceUnavailable) {
      this.state.set('unavailable');
      this.problem.set('awards.uploadStep.unavailable');
      return;
    }
    this.state.set('failed');
    this.problem.set(
      `awards.documents.problems.${knownProblem(problemType(error), KNOWN_PROBLEMS, 'failed')}`,
    );
  }

  private refusal(file: File): string | null {
    if (!isAcceptedFile(file)) {
      return 'awards.documents.problems.unsupported-type';
    }
    if (file.size > MAX_DOCUMENT_SIZE) {
      return 'awards.documents.problems.file-too-large';
    }
    return file.size === 0 ? 'awards.documents.problems.empty-file' : null;
  }
}
