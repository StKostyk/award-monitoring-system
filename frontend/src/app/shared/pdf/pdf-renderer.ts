import { Injectable } from '@angular/core';

/** An opened PDF file: its page count and the drawing of one page. */
export interface PdfFile {
  readonly pages: number;
  /**
   * Draws one page into a canvas, sharp on the screen's pixel density.
   *
   * @param page the page, from 1
   * @param canvas the canvas to draw into
   * @param width the width the page takes on the screen, in CSS pixels
   */
  render(page: number, canvas: HTMLCanvasElement, width: number): Promise<void>;
  /** Releases the file and its worker. */
  close(): void;
}

const ASSETS = '/pdfjs/';

type PdfJs = typeof import('pdfjs-dist');

/**
 * Opens PDF files with pdf.js. The library loads on the first file, so screens without documents never fetch it;
 * pages are drawn to a canvas and nothing inside the file runs (no forms, no scripts).
 */
@Injectable({ providedIn: 'root' })
export class PdfRenderer {
  private library: Promise<PdfJs> | null = null;

  /**
   * Opens a PDF file.
   *
   * @param file the content of the file
   * @return the opened file; rejected when the content is not a readable PDF
   */
  async open(file: Blob): Promise<PdfFile> {
    const [pdfjs, data] = await Promise.all([this.load(), file.arrayBuffer()]);
    const task = pdfjs.getDocument({
      data: new Uint8Array(data),
      enableXfa: false,
      cMapUrl: `${ASSETS}cmaps/`,
      standardFontDataUrl: `${ASSETS}standard_fonts/`,
      wasmUrl: `${ASSETS}wasm/`,
    });
    const document = await task.promise;
    const drawing = new WeakMap<HTMLCanvasElement, { cancel(): void }>();
    return {
      pages: document.numPages,
      render: async (page, canvas, width) => {
        drawing.get(canvas)?.cancel();
        const content = await document.getPage(page);
        const scale = width / content.getViewport({ scale: 1 }).width;
        const density = window.devicePixelRatio || 1;
        const viewport = content.getViewport({ scale: scale * density });
        canvas.width = Math.floor(viewport.width);
        canvas.height = Math.floor(viewport.height);
        canvas.style.width = `${Math.floor(viewport.width / density)}px`;
        canvas.style.height = `${Math.floor(viewport.height / density)}px`;
        const task = content.render({ canvas, viewport });
        drawing.set(canvas, task);
        try {
          await task.promise;
        } catch (error: unknown) {
          if (!(error instanceof pdfjs.RenderingCancelledException)) {
            throw error;
          }
        }
      },
      close: () => void task.destroy(),
    };
  }

  private load(): Promise<PdfJs> {
    this.library ??= import('pdfjs-dist').then((pdfjs) => {
      pdfjs.GlobalWorkerOptions.workerSrc = `${ASSETS}pdf.worker.min.mjs`;
      return pdfjs;
    });
    return this.library;
  }
}
