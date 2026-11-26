import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import {
  ThumbnailStubComponent,
  stubThumbnails,
} from '../../awards/award-documents/document-thumbnail.testing';
import { DocumentsService } from '../../awards/award-documents/documents.service';
import { AwardStatusView, AwardVersion, AwardsService, Page } from '../../awards/awards.service';
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
  approvedAt: '2026-11-04T12:00:00Z',
};

const status: AwardStatusView = {
  awardId: 41,
  status: 'APPROVED',
  requestStatus: 'APPROVED',
  currentLevel: 'DEAN',
  submittedAt: '2026-11-03T09:00:00Z',
  deadline: null,
  estimatedCompletion: null,
  overdue: false,
  completedAt: '2026-11-04T12:00:00Z',
  rejectionReason: null,
  delay: null,
  path: [],
  decisions: [
    {
      id: 1,
      decision: 'RETURNED',
      level: 'FACULTY_SECRETARY',
      reviewerId: 9,
      reviewerName: 'Ірина Секретар',
      comments: 'Додайте диплом',
      decidedAt: '2026-11-02T10:00:00Z',
      delegatorId: null,
      delegatorName: null,
    },
    {
      id: 2,
      decision: 'APPROVED',
      level: 'DEAN',
      reviewerId: 10,
      reviewerName: 'Олена Декан',
      comments: null,
      decidedAt: '2026-11-04T12:00:00Z',
      delegatorId: null,
      delegatorName: null,
    },
  ],
};

function submission(createdAt: string): AwardVersion {
  return {
    number: 2,
    action: 'SUBMITTED',
    actor: null,
    createdAt,
    snapshot: {} as AwardVersion['snapshot'],
    changes: [],
    comment: null,
  };
}

const versions: Page<AwardVersion> = {
  content: [submission('2026-11-03T09:00:00Z'), submission('2026-11-01T08:00:00Z')],
  totalElements: 2,
  totalPages: 1,
  size: 50,
  number: 0,
};

function failure(status: number): HttpErrorResponse {
  return new HttpErrorResponse({ status });
}

describe('AchievementDetailComponent', () => {
  let fixture: ComponentFixture<AchievementDetailComponent>;
  const service = { detail: vi.fn((): Observable<AchievementDetail> => of(detail)) };
  const documents = { download: vi.fn() };
  const awards = {
    status: vi.fn((): Observable<AwardStatusView> => of(status)),
    versions: vi.fn((): Observable<Page<AwardVersion>> => of(versions)),
  };

  async function render(id: string): Promise<void> {
    stubThumbnails(AchievementDetailComponent);
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
        { provide: AwardsService, useValue: awards },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AchievementDetailComponent);
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  function all(id: string): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(`[data-testid="${id}"]`),
    );
  }

  beforeEach(() => {
    service.detail.mockReset();
    service.detail.mockReturnValue(of(detail));
    documents.download.mockReset();
    awards.status.mockReset();
    awards.status.mockReturnValue(of(status));
    awards.versions.mockReset();
    awards.versions.mockReturnValue(of(versions));
  });

  it('ac1_shows_the_facts_and_the_first_document_on_the_stage', async () => {
    await render('41');

    expect(service.detail).toHaveBeenCalledWith(41);
    expect(byTestId('achievement-detail-title')?.textContent).toContain('Найкраща стаття');
    expect(byTestId('achievement-detail-recipient')?.textContent).toContain('Анастасія Коваль');
    expect(byTestId('achievement-detail-recipient')?.textContent).toContain('АК');
    expect(byTestId('achievement-detail-unit')?.textContent).toContain('Кафедра алгебри');
    expect(byTestId('achievement-detail-category')?.textContent).toContain('Наука');
    expect(byTestId('achievement-detail-level')?.textContent).toContain(
      'categories.levels.NATIONAL',
    );
    expect(byTestId('achievement-detail-level')?.className).toContain('app-lv--NATIONAL');
    expect(byTestId('achievement-detail-description')?.textContent).toContain('За статтю');
    expect(byTestId('achievement-detail')?.classList).toContain('detail--staged');
    expect(byTestId('shared-document-7')?.textContent).toContain('диплом.pdf');
    expect(byTestId('shared-document-7')?.textContent).toContain('awards.documents.types.DIPLOMA');
    expect(byTestId('thumbnail-stub')?.textContent?.trim()).toBe('7 application/pdf');
  });

  it('ac1_open_shows_the_stage_document_in_the_viewer', async () => {
    await render('41');

    byTestId('achievement-detail-open')?.click();

    const stage = fixture.debugElement.query(
      (node) => node.componentInstance instanceof ThumbnailStubComponent,
    ).componentInstance as ThumbnailStubComponent;
    expect(stage.size()).toBe('stage');
    expect(stage.opened).toBe(1);
  });

  it('ac4_award_without_shown_documents_shows_the_facts_without_a_stage', async () => {
    service.detail.mockReturnValue(of({ ...detail, documents: [] }));
    await render('41');

    expect(byTestId('shared-documents')).toBeNull();
    expect(byTestId('achievement-detail')?.classList).not.toContain('detail--staged');
    expect(byTestId('achievement-detail-title')).not.toBeNull();
  });

  it('ac4_further_documents_show_as_small_thumbnails_below_the_stage', async () => {
    const second = {
      ...detail.documents[0],
      id: 8,
      fileName: 'довідка.png',
      mimeType: 'image/png',
    };
    service.detail.mockReturnValue(of({ ...detail, documents: [detail.documents[0], second] }));
    await render('41');

    expect(byTestId('shared-documents-more')?.textContent).toContain('довідка.png');
    expect(byTestId('shared-documents-more')?.textContent).not.toContain('диплом.pdf');
    expect(all('thumbnail-stub').map((stub) => stub.textContent?.trim())).toEqual([
      '7 application/pdf',
      '8 image/png',
    ]);
    expect(all('shared-document-download')).toHaveLength(2);
  });

  it('ac2_owner_or_reviewer_sees_the_review_path_with_roles_and_reasons', async () => {
    await render('41');

    expect(awards.status).toHaveBeenCalledWith(41);
    expect(awards.versions).toHaveBeenCalledWith(41, 0, 50);
    const steps = all('review-path-step').map((step) => step.textContent ?? '');
    expect(steps).toHaveLength(4);
    expect(steps[0]).toContain('awards.statusPanel.submitted');
    expect(steps[1]).toContain('awards.statusPanel.decision.RETURNED');
    expect(steps[1]).toContain('roles.FACULTY_SECRETARY');
    expect(steps[1]).toContain('Додайте диплом');
    expect(steps[2]).toContain('awards.statusPanel.resubmitted');
    expect(steps[3]).toContain('awards.statusPanel.decision.APPROVED');
    expect(byTestId('review-path')?.textContent).not.toContain('Олена Декан');
    expect(byTestId('review-final')).toBeNull();
  });

  it('ac2_reads_every_page_of_versions_for_the_first_submission', async () => {
    awards.versions.mockImplementation((...args: unknown[]) =>
      of(
        args[1] === 0
          ? { ...versions, content: [submission('2026-11-03T09:00:00Z')], totalPages: 2 }
          : {
              ...versions,
              content: [submission('2026-11-01T08:00:00Z')],
              number: 1,
              totalPages: 2,
            },
      ),
    );
    await render('41');

    expect(awards.versions).toHaveBeenCalledWith(41, 1, 50);
    const steps = all('review-path-step').map((step) => step.textContent ?? '');
    expect(steps[0]).toContain('awards.statusPanel.submitted');
    expect(steps[2]).toContain('awards.statusPanel.resubmitted');
  });

  it('ac4_stage_shows_the_next_document_after_the_first_is_hidden', async () => {
    const second = { ...detail.documents[0], id: 8, fileName: 'довідка.pdf' };
    service.detail.mockReturnValue(of({ ...detail, documents: [detail.documents[0], second] }));
    documents.download.mockReturnValue(throwError(() => failure(404)));
    await render('41');
    service.detail.mockReturnValue(of({ ...detail, documents: [second] }));

    all('shared-document-download')[0].click();
    fixture.detectChanges();

    expect(all('thumbnail-stub').map((stub) => stub.textContent?.trim())).toEqual([
      '8 application/pdf',
    ]);
    expect(byTestId('shared-document-8')?.textContent).toContain('довідка.pdf');
  });

  it('ac2_unreadable_versions_still_show_the_decisions', async () => {
    awards.versions.mockReturnValue(throwError(() => failure(500)));
    await render('41');

    expect(all('review-path-step')).toHaveLength(3);
  });

  it('ac3_colleague_sees_only_the_final_state_and_its_date', async () => {
    awards.status.mockReturnValue(throwError(() => failure(404)));
    await render('41');

    expect(byTestId('review-path')).toBeNull();
    expect(byTestId('review-final')?.textContent).toContain('achievements.detail.approvedOn');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Додайте диплом');
  });

  it('ac3_award_without_a_review_shows_no_final_date', async () => {
    awards.status.mockReturnValue(throwError(() => failure(404)));
    service.detail.mockReturnValue(of({ ...detail, approvedAt: null }));
    await render('41');

    expect(byTestId('review-final')).toBeNull();
  });

  it('ac2_award_not_shared_shows_that_it_is_not_available', async () => {
    service.detail.mockReturnValue(throwError(() => failure(404)));
    await render('41');

    expect(byTestId('achievement-detail-error')?.textContent).toContain('achievements.detail.gone');
    expect(byTestId('achievement-detail-title')).toBeNull();
    expect(awards.status).not.toHaveBeenCalled();
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
    service.detail.mockReturnValue(of({ ...detail, documents: [] }));

    byTestId('shared-document-download')?.click();
    fixture.detectChanges();

    expect(byTestId('achievement-detail-notice')?.textContent).toContain(
      'awards.documents.problems.gone',
    );
    expect(service.detail).toHaveBeenCalledTimes(2);
    expect(byTestId('shared-document-7')).toBeNull();
    expect(awards.status).toHaveBeenCalledTimes(1);
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
