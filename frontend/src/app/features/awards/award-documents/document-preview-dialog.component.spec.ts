import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { DocumentPreviewDialogComponent } from './document-preview-dialog.component';

describe('DocumentPreviewDialogComponent', () => {
  it('ac2_6_shows_the_image_and_releases_its_url_when_closed', async () => {
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    await TestBed.configureTestingModule({
      imports: [
        DocumentPreviewDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { name: 'фото.jpg', url: 'blob:preview' } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(DocumentPreviewDialogComponent);
    fixture.detectChanges();

    const image = (fixture.nativeElement as HTMLElement).querySelector('img');
    expect(image?.getAttribute('src')).toBe('blob:preview');
    expect(image?.getAttribute('alt')).toBe('фото.jpg');

    fixture.destroy();
    expect(revoke).toHaveBeenCalledWith('blob:preview');
    revoke.mockRestore();
  });
});
