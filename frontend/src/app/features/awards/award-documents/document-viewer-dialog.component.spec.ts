import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { PdfFile, PdfRenderer } from '../../../shared/pdf/pdf-renderer';
import {
  DocumentViewerData,
  DocumentViewerDialogComponent,
} from './document-viewer-dialog.component';

describe('DocumentViewerDialogComponent', () => {
  let fixture: ComponentFixture<DocumentViewerDialogComponent>;
  let component: DocumentViewerDialogComponent;
  const pdf: PdfFile = { pages: 3, render: vi.fn(() => Promise.resolve()), close: vi.fn() };
  const renderer = { open: vi.fn(() => Promise.resolve(pdf)) };

  async function render(data: DocumentViewerData): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        DocumentViewerDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: PdfRenderer, useValue: renderer },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DocumentViewerDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await settle();
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  const pdfData = (): DocumentViewerData => ({
    fileName: 'диплом.pdf',
    mimeType: 'application/pdf',
    blob: new Blob(['%PDF-'], { type: 'application/pdf' }),
  });

  beforeEach(() => {
    renderer.open.mockReset();
    renderer.open.mockResolvedValue(pdf);
    vi.mocked(pdf.render).mockClear();
    vi.mocked(pdf.close).mockClear();
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:view'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('ac1_pdf_opens_on_the_first_page', async () => {
    await render(pdfData());

    const canvas = byTestId('document-viewer-page');
    expect(pdf.render).toHaveBeenCalledWith(1, canvas, expect.any(Number));
    expect(component.pages()).toBe(3);
  });

  it('ac2_pages_move_forward_and_back_within_the_file', async () => {
    await render(pdfData());

    expect((byTestId('document-viewer-previous') as HTMLButtonElement).disabled).toBe(true);
    byTestId('document-viewer-next')?.click();
    byTestId('document-viewer-next')?.click();
    await settle();

    expect(component.page()).toBe(3);
    expect((byTestId('document-viewer-next') as HTMLButtonElement).disabled).toBe(true);
    expect(pdf.render).toHaveBeenLastCalledWith(3, expect.anything(), expect.any(Number));
    byTestId('document-viewer-previous')?.click();
    await settle();
    expect(component.page()).toBe(2);
  });

  it('ac2_zoom_draws_the_page_wider_and_stays_within_limits', async () => {
    await render(pdfData());
    const width = vi.mocked(pdf.render).mock.lastCall?.[2] ?? 0;

    byTestId('document-viewer-zoom-in')?.click();
    await settle();
    expect(vi.mocked(pdf.render).mock.lastCall?.[2]).toBeGreaterThan(width);

    for (let step = 0; step < 10; step++) {
      component.zoomOut();
    }
    fixture.detectChanges();
    expect((byTestId('document-viewer-zoom-out') as HTMLButtonElement).disabled).toBe(true);
  });

  it('ac2_download_saves_the_file_under_its_name', async () => {
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    await render(pdfData());

    byTestId('document-viewer-download')?.click();

    expect((click.mock.instances[0] as unknown as HTMLAnchorElement).download).toBe('диплом.pdf');
    click.mockRestore();
  });

  it('ac1_image_shows_inline_and_releases_its_url', async () => {
    await render({ fileName: 'фото.jpg', mimeType: 'image/jpeg', blob: new Blob(['x']) });

    const image = byTestId('document-viewer-image');
    expect(image?.getAttribute('src')).toBe('blob:view');
    expect(image?.getAttribute('alt')).toBe('фото.jpg');
    expect(renderer.open).not.toHaveBeenCalled();
    fixture.destroy();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:view');
  });

  it('ac4_file_that_fails_to_render_offers_the_download', async () => {
    renderer.open.mockRejectedValue(new Error('Invalid PDF structure'));
    await render(pdfData());

    expect(byTestId('document-viewer-failed')?.textContent).toContain(
      'awards.documents.viewer.failed',
    );
    expect(byTestId('document-viewer-download')).not.toBeNull();
  });

  it('ac5_page_number_is_announced', async () => {
    await render(pdfData());

    const status = byTestId('document-viewer-status');
    expect(status?.getAttribute('aria-live')).toBe('polite');
    expect(status?.textContent).toContain('awards.documents.viewer.page');
    const stage = byTestId('document-viewer');
    expect(stage?.getAttribute('tabindex')).toBe('0');
    expect(stage?.getAttribute('aria-label')).toBe('диплом.pdf');
  });

  it('closes_the_file_when_the_dialog_closes', async () => {
    await render(pdfData());

    fixture.destroy();

    expect(pdf.close).toHaveBeenCalled();
  });
});
