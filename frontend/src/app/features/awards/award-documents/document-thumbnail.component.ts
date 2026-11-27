import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

import { IconComponent } from '../../../shared/icons/icon.component';
import { PdfRenderer } from '../../../shared/pdf/pdf-renderer';
import {
  DocumentViewerData,
  DocumentViewerDialogComponent,
} from './document-viewer-dialog.component';
import { DocumentsService } from './documents.service';

/** What the thumbnail shows: the file while it loads, its picture, or a tile with the reason there is none. */
export type ThumbnailState = 'loading' | 'image' | 'pdf' | 'type' | 'failed';

const PREVIEWABLE = new Set(['application/pdf', 'image/jpeg', 'image/png', 'image/webp']);
/** A small thumbnail in a document list, or the large first page on the stage of an award page. */
export type ThumbnailSize = 'small' | 'stage';

const WIDTHS: Record<ThumbnailSize, number> = { small: 72, stage: 640 };

/**
 * The first page or the picture of a document, read with the access rules of the download; a click opens the
 * viewer. Other file types and files that cannot be read show a plain tile with the reason.
 */
@Component({
  selector: 'app-document-thumbnail',
  imports: [IconComponent, TranslocoPipe],
  templateUrl: './document-thumbnail.component.html',
  styleUrl: './document-thumbnail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.app-thumbnail--stage]': "size() === 'stage'" },
})
export class DocumentThumbnailComponent implements OnInit, OnDestroy {
  /** The document to show. */
  readonly documentId = input.required<number>();
  /** Its file name, which also names the button. */
  readonly fileName = input.required<string>();
  /** Its media type. */
  readonly mimeType = input.required<string>();
  /** How large it is drawn. */
  readonly size = input<ThumbnailSize>('small');

  private readonly documents = inject(DocumentsService);
  private readonly renderer = inject(PdfRenderer);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('page');
  private blob: Blob | null = null;

  readonly state = signal<ThumbnailState>('loading');
  readonly imageUrl = signal<string | null>(null);
  /** Whether the file was read and the viewer can open it. */
  readonly ready = computed(() => this.state() === 'image' || this.state() === 'pdf');

  ngOnInit(): void {
    if (!PREVIEWABLE.has(this.mimeType())) {
      this.state.set('type');
      return;
    }
    this.documents
      .download(this.documentId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => this.show(blob),
        error: () => this.state.set('failed'),
      });
  }

  ngOnDestroy(): void {
    const url = this.imageUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
  }

  /** Opens the viewer with the file already read. */
  open(): void {
    if (!this.blob) {
      return;
    }
    const data: DocumentViewerData = {
      fileName: this.fileName(),
      mimeType: this.mimeType(),
      blob: this.blob,
    };
    this.dialog.open(DocumentViewerDialogComponent, {
      data,
      width: '960px',
      maxWidth: '95vw',
    });
  }

  private show(blob: Blob): void {
    this.blob = blob;
    if (this.mimeType().startsWith('image/')) {
      this.imageUrl.set(URL.createObjectURL(blob));
      this.state.set('image');
      return;
    }
    this.drawFirstPage(blob).then(
      () => this.state.set('pdf'),
      () => this.state.set('failed'),
    );
  }

  private async drawFirstPage(blob: Blob): Promise<void> {
    const file = await this.renderer.open(blob);
    try {
      const canvas = this.canvas()?.nativeElement;
      if (!canvas) {
        throw new Error('The thumbnail is no longer shown');
      }
      await file.render(1, canvas, WIDTHS[this.size()]);
      if (this.size() === 'stage') {
        canvas.style.width = '100%';
        canvas.style.height = 'auto';
      }
    } finally {
      file.close();
    }
  }
}
