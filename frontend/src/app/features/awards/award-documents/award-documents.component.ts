import { HttpEventType, HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  concatMap,
  filter,
  of,
  switchMap,
  tap,
  throwError,
} from 'rxjs';

import { problemStatus, problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDate } from '../../../shared/date-format';
import { saveFile } from '../../../shared/file-download';
import { ConfirmDialogComponent } from '../confirm-dialog/confirm-dialog.component';
import { DocumentPreviewDialogComponent } from './document-preview-dialog.component';
import {
  ACCEPTED_FORMATS,
  AwardDocument,
  DOCUMENT_TYPES,
  DocumentType,
  DocumentsService,
  MAX_DOCUMENTS,
  MAX_DOCUMENT_SIZE,
  isAcceptedFile,
} from './documents.service';

/** Server refusals a second attempt cannot change. */
const FINAL_PROBLEMS = [
  'empty-file',
  'file-too-large',
  'unsupported-type',
  'content-mismatch',
  'duplicate-document',
  'document-limit',
  'malware-detected',
  'storage-quota',
  'award-not-editable',
];
/** Server refusals that pass after a wait: they can be retried and show their own reason. */
const WAIT_PROBLEMS = ['too-many-requests'];
const PERCENT = 100;
const KILOBYTE = 1024;
const MEGABYTE = KILOBYTE * KILOBYTE;

type QueueState = 'waiting' | 'uploading' | 'failed' | 'refused';

/** A file chosen for upload that is not stored yet. */
export interface QueuedFile {
  key: number;
  file: File;
  type: DocumentType;
  state: QueueState;
  progress: number;
  /** Translation key of the reason the file was not stored. */
  problem: string | null;
}

@Component({
  selector: 'app-award-documents',
  imports: [
    MatButton,
    MatFormField,
    MatLabel,
    MatSelect,
    MatSelectTrigger,
    MatOption,
    MatProgressBar,
    TranslocoPipe,
  ],
  templateUrl: './award-documents.component.html',
  styleUrl: './award-documents.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardDocumentsComponent {
  private readonly service = inject(DocumentsService);
  private readonly dialog = inject(MatDialog);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pending = new Subject<number>();

  private nextKey = 0;
  private savedId: number | null = null;
  private loadedFor: number | null = null;
  private typeChosen = false;

  /** The award; null for a new award that is saved when the first file is added. */
  readonly awardId = input<number | null>(null);
  /** Whether files can be added. */
  readonly uploads = input(false);
  /** Whether documents can be removed. */
  readonly removable = input(false);
  /** Saves the new award and answers its id; called before the first upload when `awardId` is null. */
  readonly prepare = input<(() => Observable<number>) | null>(null);
  /** The number of stored documents, after every load and change. */
  readonly countChange = output<number>();

  readonly types = DOCUMENT_TYPES;
  readonly accept = Object.entries(ACCEPTED_FORMATS)
    .flatMap(([mime, extensions]) => [mime, ...extensions.map((extension) => `.${extension}`)])
    .join(',');
  readonly touch = typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches;
  readonly documents = signal<AwardDocument[]>([]);
  readonly queue = signal<QueuedFile[]>([]);
  readonly loading = signal(false);
  readonly notice = signal<string | null>(null);
  readonly announcement = signal('');
  readonly dragging = signal(false);
  readonly nextType = signal<DocumentType>('CERTIFICATE');
  readonly empty = computed(() => !this.documents().length && !this.queue().length);

  constructor() {
    effect(() => {
      const id = this.awardId();
      untracked(() => {
        if (id !== null && id !== this.loadedFor) {
          this.load(id);
        }
      });
    });
    this.pending
      .pipe(
        concatMap((key) => this.send(key)),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  /**
   * Queues the chosen files: those the server would refuse are shown with their reason and never sent.
   *
   * @param files the files of a drop or a file picker
   */
  add(files: FileList | File[] | null): void {
    const chosen = Array.from(files ?? []);
    if (!chosen.length) {
      return;
    }
    this.notice.set(null);
    let slots = MAX_DOCUMENTS - this.documents().length - this.active();
    let certificate = !this.typeChosen && this.documents().length === 0 && this.active() === 0;
    const added = chosen.map((file) => {
      const problem = this.refusal(file, slots);
      if (problem === null) {
        slots--;
      }
      const type = certificate
        ? 'CERTIFICATE'
        : this.typeChosen
          ? this.nextType()
          : 'SUPPORTING_DOCUMENT';
      certificate = certificate && problem !== null;
      return this.queued(file, type, problem);
    });
    this.queue.update((queue) => [...queue, ...added]);
    this.typeChosen = false;
    this.nextType.set(this.defaultType());
    added.filter((item) => item.state === 'waiting').forEach((item) => this.pending.next(item.key));
  }

  /** Remembers the type the user picked for the next files. */
  chooseType(type: DocumentType): void {
    this.typeChosen = true;
    this.nextType.set(type);
  }

  /** Sends a failed file again. */
  retry(item: QueuedFile): void {
    this.patch(item.key, { state: 'waiting', progress: 0, problem: null });
    this.pending.next(item.key);
  }

  /** Drops a file that was not stored from the list. */
  dismiss(item: QueuedFile): void {
    this.queue.update((queue) => queue.filter((queued) => queued.key !== item.key));
    this.resetType();
  }

  /** Opens the file picker from the keyboard. */
  pick(input: HTMLInputElement, event: Event): void {
    event.preventDefault();
    input.click();
  }

  /** Takes the files of a picker and clears it, so that the same file can be chosen again. */
  picked(input: HTMLInputElement): void {
    this.add(input.files);
    input.value = '';
  }

  dragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  dropped(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.add(event.dataTransfer?.files ?? null);
  }

  /** Saves a document under its name. */
  download(document: AwardDocument): void {
    this.content(document).subscribe({
      next: (blob) => saveFile(blob, document.fileName),
      error: (error: unknown) => this.unavailable(error),
    });
  }

  /** Shows an image document in a dialog. */
  preview(document: AwardDocument): void {
    this.content(document).subscribe({
      next: (blob) =>
        this.dialog.open(DocumentPreviewDialogComponent, {
          data: { name: document.fileName, url: URL.createObjectURL(blob) },
          maxWidth: '90vw',
        }),
      error: (error: unknown) => this.unavailable(error),
    });
  }

  /** Asks before removing a document from the draft. */
  remove(document: AwardDocument): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'awards.documents.remove.title',
          text: 'awards.documents.remove.text',
          confirm: 'awards.documents.remove.confirm',
          cancel: 'awards.documents.remove.cancel',
          params: { name: document.fileName },
        },
        width: '420px',
      })
      .afterClosed()
      .pipe(
        filter((confirmed?: boolean) => confirmed === true),
        switchMap(() => this.service.remove(document.id)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.removed(document.id),
        error: (error: unknown) => {
          if (problemStatus(error) === HttpStatusCode.NotFound) {
            this.removed(document.id);
          } else {
            this.notice.set(this.problemKey(problemType(error)));
          }
        },
      });
  }

  isImage(document: AwardDocument): boolean {
    return document.mimeType.startsWith('image/');
  }

  /** The size in kilobytes below a megabyte, in megabytes from there on, in the interface language. */
  sizeValue(bytes: number): string {
    const locale = this.language.current() === 'en' ? 'en-GB' : 'uk-UA';
    const format = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
    return bytes < MEGABYTE
      ? format.format(Math.max(1, Math.round(bytes / KILOBYTE)))
      : format.format(bytes / MEGABYTE);
  }

  /** Translation key of the unit of `sizeValue`. */
  sizeUnit(bytes: number): string {
    return bytes < MEGABYTE ? 'awards.documents.kilobytes' : 'awards.documents.megabytes';
  }

  date(document: AwardDocument): string {
    return kyivDate(document.uploadedAt, this.language.current());
  }

  /** The content of a document, dropped when the section is left before it arrives. */
  private content(document: AwardDocument): Observable<Blob> {
    return this.service.download(document.id).pipe(takeUntilDestroyed(this.destroyRef));
  }

  private load(id: number): void {
    this.loadedFor = id;
    this.loading.set(true);
    this.service.list(id).subscribe({
      next: (documents) => {
        this.loading.set(false);
        this.show(documents);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.notice.set(
          problemStatus(error) === HttpStatusCode.NotFound
            ? 'awards.documents.problems.gone'
            : 'awards.documents.problems.list-failed',
        );
      },
    });
  }

  private send(key: number): Observable<unknown> {
    const item = this.queue().find((queued) => queued.key === key);
    if (!item || item.state !== 'waiting') {
      return EMPTY;
    }
    this.patch(key, { state: 'uploading', progress: 0 });
    return this.target().pipe(
      catchError(() => {
        this.patch(key, { state: 'failed', problem: 'awards.documents.problems.not-saved' });
        return EMPTY;
      }),
      concatMap((awardId) =>
        this.service.upload(awardId, item.file, item.type).pipe(
          tap((event) => {
            if (event.type === HttpEventType.UploadProgress && event.total) {
              this.patch(key, { progress: Math.round((PERCENT * event.loaded) / event.total) });
            } else if (event.type === HttpEventType.Response && event.body) {
              this.stored(key, event.body);
            }
          }),
          catchError((error: unknown) => {
            this.refused(key, error);
            return of(null);
          }),
        ),
      ),
    );
  }

  /** The award to upload to, saving a new award first. */
  private target(): Observable<number> {
    const id = this.awardId() ?? this.savedId;
    if (id !== null) {
      return of(id);
    }
    const prepare = this.prepare();
    if (prepare === null) {
      return throwError(() => new Error('No award to upload to'));
    }
    return prepare().pipe(
      tap((saved) => {
        this.savedId = saved;
        this.loadedFor = saved;
      }),
    );
  }

  private stored(key: number, document: AwardDocument): void {
    this.queue.update((queue) => queue.filter((queued) => queued.key !== key));
    this.show([...this.documents(), document]);
    this.announcement.set(
      this.transloco.translate('awards.documents.uploaded', { name: document.fileName }),
    );
  }

  private refused(key: number, error: unknown): void {
    const type = problemType(error);
    const final = FINAL_PROBLEMS.includes(type);
    this.patch(key, {
      state: final ? 'refused' : 'failed',
      problem: this.problemKey(type),
    });
    if (type === 'document-limit' || type === 'duplicate-document') {
      const id = this.awardId() ?? this.savedId;
      if (id !== null) {
        this.load(id);
      }
    }
  }

  private removed(id: number): void {
    this.show(this.documents().filter((document) => document.id !== id));
  }

  /** A document that cannot be read any more: the list is read again. */
  private unavailable(error: unknown): void {
    this.notice.set(
      problemStatus(error) === HttpStatusCode.NotFound
        ? 'awards.documents.problems.gone'
        : 'awards.documents.problems.download-failed',
    );
    const id = this.awardId() ?? this.savedId;
    if (id !== null && problemStatus(error) === HttpStatusCode.NotFound) {
      this.service.list(id).subscribe({
        next: (documents) => this.show(documents),
        error: () => this.show([]),
      });
    }
  }

  private show(documents: AwardDocument[]): void {
    this.documents.set(documents);
    this.countChange.emit(documents.length);
    this.resetType();
  }

  private refusal(file: File, slots: number): string | null {
    if (!isAcceptedFile(file)) {
      return 'awards.documents.problems.unsupported-type';
    }
    if (file.size > MAX_DOCUMENT_SIZE) {
      return 'awards.documents.problems.file-too-large';
    }
    if (file.size === 0) {
      return 'awards.documents.problems.empty-file';
    }
    return slots > 0 ? null : 'awards.documents.problems.document-limit';
  }

  private queued(file: File, type: DocumentType, problem: string | null): QueuedFile {
    return {
      key: this.nextKey++,
      file,
      type,
      state: problem === null ? 'waiting' : 'refused',
      progress: 0,
      problem,
    };
  }

  private patch(key: number, change: Partial<QueuedFile>): void {
    this.queue.update((queue) =>
      queue.map((queued) => (queued.key === key ? { ...queued, ...change } : queued)),
    );
  }

  private active(): number {
    return this.queue().filter((queued) => queued.state !== 'refused').length;
  }

  private defaultType(): DocumentType {
    return this.documents().length === 0 && this.active() === 0
      ? 'CERTIFICATE'
      : 'SUPPORTING_DOCUMENT';
  }

  private problemKey(type: string): string {
    return FINAL_PROBLEMS.includes(type) || WAIT_PROBLEMS.includes(type)
      ? `awards.documents.problems.${type}`
      : 'awards.documents.problems.failed';
  }

  private resetType(): void {
    if (!this.typeChosen) {
      this.nextType.set(this.defaultType());
    }
  }
}
