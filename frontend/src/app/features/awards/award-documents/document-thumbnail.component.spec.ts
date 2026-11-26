import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PdfFile, PdfRenderer } from '../../../shared/pdf/pdf-renderer';
import { DocumentThumbnailComponent, ThumbnailSize } from './document-thumbnail.component';
import { DocumentViewerDialogComponent } from './document-viewer-dialog.component';
import { DocumentsService } from './documents.service';

describe('DocumentThumbnailComponent', () => {
  let fixture: ComponentFixture<DocumentThumbnailComponent>;
  const service = { download: vi.fn((): Observable<Blob> => of(new Blob())) };
  const dialog = { open: vi.fn() };
  const pdf: PdfFile = { pages: 3, render: vi.fn(() => Promise.resolve()), close: vi.fn() };
  const renderer = { open: vi.fn(() => Promise.resolve(pdf)) };

  async function render(
    fileName: string,
    mimeType: string,
    size: ThumbnailSize = 'small',
  ): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        DocumentThumbnailComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: DocumentsService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: PdfRenderer, useValue: renderer },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DocumentThumbnailComponent);
    fixture.componentRef.setInput('documentId', 7);
    fixture.componentRef.setInput('fileName', fileName);
    fixture.componentRef.setInput('mimeType', mimeType);
    fixture.componentRef.setInput('size', size);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  beforeEach(() => {
    service.download.mockReset();
    service.download.mockReturnValue(of(new Blob(['%PDF-'], { type: 'application/pdf' })));
    dialog.open.mockReset();
    renderer.open.mockReset();
    renderer.open.mockResolvedValue(pdf);
    vi.mocked(pdf.render).mockClear();
    vi.mocked(pdf.close).mockClear();
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:thumb'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('ac1_pdf_first_page_is_drawn_to_a_canvas', async () => {
    await render('диплом.pdf', 'application/pdf');

    expect(service.download).toHaveBeenCalledWith(7);
    expect(renderer.open).toHaveBeenCalledTimes(1);
    const canvas = byTestId('document-thumbnail-page');
    expect(pdf.render).toHaveBeenCalledWith(1, canvas, expect.any(Number));
    expect(pdf.close).toHaveBeenCalled();
    expect(byTestId('document-thumbnail')?.classList).toContain('thumbnail--ready');
  });

  it('stage_draws_the_first_page_large_and_is_ready_to_open', async () => {
    await render('диплом.pdf', 'application/pdf', 'stage');

    const canvas = byTestId('document-thumbnail-page');
    expect(pdf.render).toHaveBeenCalledWith(1, canvas, 640);
    expect(canvas?.style.width).toBe('100%');
    expect((fixture.nativeElement as HTMLElement).classList).toContain('app-thumbnail--stage');
    expect(fixture.componentInstance.ready()).toBe(true);
  });

  it('ac1_image_shows_inline', async () => {
    service.download.mockReturnValue(of(new Blob(['x'], { type: 'image/png' })));
    await render('фото.png', 'image/png');

    expect(byTestId('document-thumbnail-image')?.getAttribute('src')).toBe('blob:thumb');
    expect(renderer.open).not.toHaveBeenCalled();
  });

  it('ac2_click_opens_the_viewer_with_the_loaded_file', async () => {
    await render('диплом.pdf', 'application/pdf');

    byTestId('document-thumbnail')?.click();

    expect(dialog.open).toHaveBeenCalledWith(
      DocumentViewerDialogComponent,
      expect.objectContaining({
        data: { fileName: 'диплом.pdf', mimeType: 'application/pdf', blob: expect.any(Blob) },
      }),
    );
    expect(service.download).toHaveBeenCalledTimes(1);
  });

  it('ac3_refused_file_shows_a_tile_with_the_reason', async () => {
    service.download.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await render('диплом.pdf', 'application/pdf');

    expect(byTestId('document-thumbnail')).toBeNull();
    expect(byTestId('document-thumbnail-tile')?.textContent).toContain(
      'awards.documents.thumbnail.failed',
    );
  });

  it('ac4_other_type_shows_a_tile_without_loading_the_file', async () => {
    await render(
      'звіт.docx',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    );

    expect(service.download).not.toHaveBeenCalled();
    expect(byTestId('document-thumbnail-tile')?.textContent).toContain(
      'awards.documents.thumbnail.type',
    );
  });

  it('ac4_pdf_that_fails_to_render_shows_a_tile', async () => {
    renderer.open.mockRejectedValue(new Error('Invalid PDF structure'));
    await render('зламаний.pdf', 'application/pdf');

    expect(byTestId('document-thumbnail-tile')?.textContent).toContain(
      'awards.documents.thumbnail.failed',
    );
  });

  it('ac5_thumbnail_is_a_button_named_after_the_file', async () => {
    await render('диплом.pdf', 'application/pdf');

    const button = byTestId('document-thumbnail');
    expect(button?.tagName).toBe('BUTTON');
    expect(button?.getAttribute('aria-label')).toBe('awards.documents.previewOf');
  });

  it('releases_the_image_url_when_destroyed', async () => {
    service.download.mockReturnValue(of(new Blob(['x'], { type: 'image/jpeg' })));
    await render('фото.jpg', 'image/jpeg');

    fixture.destroy();

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:thumb');
  });
});
