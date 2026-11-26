import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

import { saveFile } from '../../../shared/file-download';
import { IconComponent } from '../../../shared/icons/icon.component';
import { PdfFile, PdfRenderer } from '../../../shared/pdf/pdf-renderer';

/** The file the viewer shows, already read with the access rules of the download. */
export interface DocumentViewerData {
  fileName: string;
  mimeType: string;
  blob: Blob;
}

const ZOOM_STEPS = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];
const FIT = ZOOM_STEPS.indexOf(1);
const FALLBACK_WIDTH = 640;

/** Shows an image or a PDF page by page, with zoom and the download; Esc closes it. */
@Component({
  selector: 'app-document-viewer-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    MatIconButton,
    IconComponent,
    TranslocoPipe,
  ],
  templateUrl: './document-viewer-dialog.component.html',
  styleUrl: './document-viewer-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentViewerDialogComponent implements OnInit, OnDestroy {
  protected readonly data = inject<DocumentViewerData>(MAT_DIALOG_DATA);
  private readonly renderer = inject(PdfRenderer);
  private readonly stage = viewChild.required<ElementRef<HTMLElement>>('stage');
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('sheet');
  private file: PdfFile | null = null;
  private closed = false;

  readonly isImage = this.data.mimeType.startsWith('image/');
  readonly imageUrl = this.isImage ? URL.createObjectURL(this.data.blob) : null;
  readonly page = signal(1);
  readonly pages = signal(0);
  readonly failed = signal(false);
  readonly zoomStep = signal(FIT);
  readonly zoom = computed(() => ZOOM_STEPS[this.zoomStep()]);

  ngOnInit(): void {
    if (this.isImage) {
      return;
    }
    this.renderer.open(this.data.blob).then(
      (file) => {
        if (this.closed) {
          file.close();
          return;
        }
        this.file = file;
        this.pages.set(file.pages);
        this.draw();
      },
      () => this.failed.set(true),
    );
  }

  ngOnDestroy(): void {
    this.closed = true;
    this.file?.close();
    if (this.imageUrl) {
      URL.revokeObjectURL(this.imageUrl);
    }
  }

  /** Shows the next page. */
  next(): void {
    if (this.page() < this.pages()) {
      this.page.update((page) => page + 1);
      this.draw();
    }
  }

  /** Shows the previous page. */
  previous(): void {
    if (this.page() > 1) {
      this.page.update((page) => page - 1);
      this.draw();
    }
  }

  /** Draws the file one step larger. */
  zoomIn(): void {
    this.zoomStep.update((step) => Math.min(step + 1, ZOOM_STEPS.length - 1));
    this.draw();
  }

  /** Draws the file one step smaller. */
  zoomOut(): void {
    this.zoomStep.update((step) => Math.max(step - 1, 0));
    this.draw();
  }

  /** Whether the file is drawn at its largest step. */
  largest(): boolean {
    return this.zoomStep() === ZOOM_STEPS.length - 1;
  }

  /** Saves the file under its name. */
  download(): void {
    saveFile(this.data.blob, this.data.fileName);
  }

  private draw(): void {
    const canvas = this.canvas()?.nativeElement;
    if (!this.file || !canvas) {
      return;
    }
    const width = (this.stage().nativeElement.clientWidth || FALLBACK_WIDTH) * this.zoom();
    this.file.render(this.page(), canvas, width).catch(() => this.failed.set(true));
  }
}
