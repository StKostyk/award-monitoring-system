import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { DocumentsService } from '../../awards/award-documents/documents.service';
import { Achievement, AchievementDetail, AchievementsService } from '../achievements.service';
import { AchievementDetailComponent } from './achievement-detail.component';

const achievement: Achievement = {
  awardId: 41,
  title: 'Best paper',
  titleUk: 'Найкраща стаття',
  description: 'For the paper',
  descriptionUk: 'За статтю',
  category: {
    id: 3,
    name: 'Science',
    nameUk: 'Наука',
    level: 'NATIONAL',
  } as Achievement['category'],
  awardingOrganization: 'МОН України',
  awardDate: '2026-05-04',
  externalUrl: null,
  verified: true,
  recipient: {
    type: 'PERSON',
    personName: 'Анастасія Коваль',
    unit: { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри', type: 'DEPARTMENT' },
  },
};

const detail: AchievementDetail = {
  achievement,
  documents: [
    {
      id: 7,
      fileName: 'диплом.pdf',
      type: 'DIPLOMA',
      mimeType: 'application/pdf',
      size: 2048,
      description: null,
    },
  ],
};

function failure(status: number): HttpErrorResponse {
  return new HttpErrorResponse({ status });
}

describe('AchievementDetailComponent', () => {
  let fixture: ComponentFixture<AchievementDetailComponent>;
  const service = { detail: vi.fn((): Observable<AchievementDetail> => of(detail)) };
  const documents = { download: vi.fn() };

  async function render(id: string): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        AchievementDetailComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id }) } },
        },
        { provide: AchievementsService, useValue: service },
        { provide: DocumentsService, useValue: documents },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AchievementDetailComponent);
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  beforeEach(() => {
    service.detail.mockReset();
    service.detail.mockReturnValue(of(detail));
    documents.download.mockReset();
  });

  it('ac2_shows_the_card_fields_and_the_shown_documents', async () => {
    await render('41');

    expect(service.detail).toHaveBeenCalledWith(41);
    expect(byTestId('achievement-detail-title')?.textContent).toContain('Найкраща стаття');
    expect(byTestId('achievement-detail-recipient')?.textContent).toContain('Анастасія Коваль');
    expect(byTestId('achievement-detail-unit')?.textContent).toContain('Кафедра алгебри');
    expect(byTestId('achievement-detail-category')?.textContent).toContain('Наука');
    expect(byTestId('achievement-detail-category')?.textContent).toContain(
      'categories.levels.NATIONAL',
    );
    expect(byTestId('achievement-detail-description')?.textContent).toContain('За статтю');
    expect(byTestId('shared-document-7')?.textContent).toContain('диплом.pdf');
    expect(byTestId('shared-document-7')?.textContent).toContain('awards.documents.types.DIPLOMA');
  });

  it('ac2_award_without_shown_documents_says_so', async () => {
    service.detail.mockReturnValue(of({ achievement, documents: [] }));
    await render('41');

    expect(byTestId('shared-documents-empty')?.textContent).toContain(
      'achievements.detail.noDocuments',
    );
  });

  it('ac2_award_not_shared_shows_that_it_is_not_available', async () => {
    service.detail.mockReturnValue(throwError(() => failure(404)));
    await render('41');

    expect(byTestId('achievement-detail-error')?.textContent).toContain('achievements.detail.gone');
    expect(byTestId('achievement-detail-title')).toBeNull();
  });

  it('ac2_malformed_id_is_not_requested', async () => {
    await render('abc');

    expect(service.detail).not.toHaveBeenCalled();
    expect(byTestId('achievement-detail-error')?.textContent).toContain('achievements.detail.gone');
  });

  it('ac2_other_failures_can_be_retried', async () => {
    service.detail.mockReturnValueOnce(throwError(() => failure(500)));
    await render('41');

    expect(byTestId('achievement-detail-error')?.textContent).toContain(
      'achievements.problems.failed',
    );
    byTestId('achievement-detail-retry')?.click();
    fixture.detectChanges();

    expect(service.detail).toHaveBeenCalledTimes(2);
    expect(byTestId('achievement-detail-title')).not.toBeNull();
  });

  it('ac3_download_saves_the_document_under_its_name', async () => {
    documents.download.mockReturnValue(of(new Blob(['%PDF-'])));
    const urls = { create: URL.createObjectURL, revoke: URL.revokeObjectURL };
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:file'), revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await render('41');

    byTestId('shared-document-download')?.click();

    expect(documents.download).toHaveBeenCalledWith(7);
    expect(click).toHaveBeenCalled();
    click.mockRestore();
    Object.assign(URL, { createObjectURL: urls.create, revokeObjectURL: urls.revoke });
  });

  it('ac3_document_hidden_meanwhile_reads_the_achievement_again', async () => {
    documents.download.mockReturnValue(throwError(() => failure(404)));
    await render('41');
    service.detail.mockReturnValue(of({ achievement, documents: [] }));

    byTestId('shared-document-download')?.click();
    fixture.detectChanges();

    expect(byTestId('achievement-detail-notice')?.textContent).toContain(
      'awards.documents.problems.gone',
    );
    expect(service.detail).toHaveBeenCalledTimes(2);
    expect(byTestId('shared-document-7')).toBeNull();
  });

  it('ac3_other_download_failures_keep_the_documents', async () => {
    documents.download.mockReturnValue(throwError(() => failure(500)));
    await render('41');

    byTestId('shared-document-download')?.click();
    fixture.detectChanges();

    expect(byTestId('achievement-detail-notice')?.textContent).toContain(
      'awards.documents.problems.download-failed',
    );
    expect(service.detail).toHaveBeenCalledTimes(1);
  });
});
