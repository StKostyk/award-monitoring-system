import { HttpErrorResponse } from '@angular/common/http';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { MockInstance, vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { RoleScope } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { ReviewsService } from '../../reviews/reviews.service';
import { AwardDocument, DocumentsService } from '../award-documents/documents.service';
import { Award, AwardStatusView, AwardsService } from '../awards.service';
import { AwardSubmittedComponent } from '../award-submitted/award-submitted.component';
import { AwardDetailComponent } from './award-detail.component';

const pending: Award = {
  id: 5,
  title: 'Ministry letter',
  titleUk: 'Грамота МОН',
  description: null,
  descriptionUk: 'За внесок у розвиток освіти',
  category: {
    id: 13,
    name: 'Ministry Recognition',
    nameUk: 'Відзнака міністерства',
    level: 'NATIONAL',
  },
  awardingOrganization: 'МОН України',
  awardDate: '2025-05-01',
  externalUrl: 'https://mon.gov.ua',
  status: 'PENDING',
  impactScore: 80,
  owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
  recipient: { type: 'PERSON', organization: null },
  organization: {
    id: 64,
    name: 'Algebra',
    nameUk: 'Кафедра алгебри',
    code: 'DAI',
    type: 'DEPARTMENT',
  },
  request: {
    status: 'SUBMITTED',
    currentLevel: 'FACULTY_SECRETARY',
    submittedAt: '2026-09-28T09:00:00Z',
    deadline: '2026-10-01T09:00:00Z',
    estimatedCompletion: '2026-10-07',
    overdue: false,
  },
  warnings: [],
  createdAt: '2026-09-28T08:00:00Z',
  updatedAt: '2026-09-28T09:00:00Z',
  version: 3,
};

const withoutRequest: AwardStatusView = {
  awardId: 5,
  status: 'PENDING',
  requestStatus: null,
  currentLevel: null,
  submittedAt: null,
  deadline: null,
  estimatedCompletion: null,
  overdue: false,
  completedAt: null,
  rejectionReason: null,
  delay: null,
  path: [],
  decisions: [],
};

const documentRow: AwardDocument = {
  id: 3,
  awardId: 5,
  fileName: 'диплом.pdf',
  type: 'CERTIFICATE',
  mimeType: 'application/pdf',
  size: 1024,
  description: null,
  uploadedAt: '2026-10-01T08:00:00Z',
  uploadedBy: { id: 21, name: 'Анастасія Коваль' },
};

const noVersions = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };

const translations = {
  uk: {
    awards: {
      notFound: 'Не знайдено',
      status: { PENDING: 'На розгляді', DRAFT: 'Чернетка' },
      requestStatus: { SUBMITTED: 'Подано' },
      levels: { FACULTY_SECRETARY: 'секретар факультету' },
      submitted: { text: 'Подано на розгляд секретарю факультету.' },
    },
  },
};

describe('AwardDetailComponent', () => {
  const service = {
    get: vi.fn(),
    remove: vi.fn(),
    versions: vi.fn(),
    categories: vi.fn(),
    auditTrail: vi.fn(),
    status: vi.fn(),
  };
  const granted = signal<string[]>([]);
  const roleScopes = signal<RoleScope[]>([]);
  const permissions = computed(() => ({
    hasPermission: (permission: string) => granted().includes(permission),
    roleScopes: roleScopes(),
  }));
  const reviews = { item: vi.fn() };
  const dialog = { open: vi.fn() };
  const documents = { list: vi.fn(() => of<AwardDocument[]>([])) };
  let navigate: MockInstance<Router['navigate']>;

  async function open<T>(component: new () => T, id: string): Promise<ComponentFixture<T>> {
    await TestBed.configureTestingModule({
      imports: [
        component,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id }) } },
        },
        { provide: AwardsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { userId: signal('21'), permissions } },
        { provide: MatDialog, useValue: dialog },
        { provide: DocumentsService, useValue: documents },
        { provide: ReviewsService, useValue: reviews },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    service.get.mockReset();
    service.remove.mockReset();
    dialog.open.mockReset();
    service.versions.mockReset().mockReturnValue(of(noVersions));
    service.categories.mockReset().mockReturnValue(of([]));
    service.auditTrail.mockReset().mockReturnValue(of(noVersions));
    service.status.mockReset().mockReturnValue(of(withoutRequest));
    granted.set([]);
    roleScopes.set([]);
    reviews.item
      .mockReset()
      .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    documents.list.mockReset().mockReturnValue(of([]));
  });

  it('ac1_9_shows_a_submitted_award_read_only_with_its_request', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-detail-title"]')?.textContent).toContain(
      'Грамота МОН',
    );
    expect(element.querySelector('[data-testid="award-detail-status"]')?.textContent).toContain(
      'На розгляді',
    );
    expect(element.querySelector('[data-testid="award-detail-request"]')?.textContent).toContain(
      'секретар факультету',
    );
    expect(element.querySelector('[data-testid="award-edit"]')).toBeNull();
    expect(element.querySelector('[data-testid="award-status-panel"]')).not.toBeNull();
    expect(service.status).toHaveBeenCalledWith(5);
  });

  it('ac1_11_shows_the_review_panel_to_an_approver_on_somebody_elses_pending_award', async () => {
    service.get.mockReturnValue(of(pending));
    let fixture = await open(AwardDetailComponent, '5');
    expect(reviews.item).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    roleScopes.set([{ role: 'FACULTY_SECRETARY', organizationId: 9 }]);
    fixture = await open(AwardDetailComponent, '5');
    expect(reviews.item).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    service.get.mockReturnValue(of({ ...pending, owner: { ...pending.owner, id: 22 } }));
    fixture = await open(AwardDetailComponent, '5');
    expect(reviews.item).toHaveBeenCalledWith(5);
    expect(fixture.nativeElement.querySelector('app-review-panel')).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="award-back"]')?.getAttribute('href'),
    ).toBe('/reviews');
  });

  it('ac0_7_a_unit_award_names_the_unit_as_recipient_and_who_entered_it', async () => {
    service.get.mockReturnValue(
      of({
        ...pending,
        recipient: {
          type: 'UNIT',
          organization: { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри', type: 'DEPARTMENT' },
        },
      }),
    );
    const fixture = await open(AwardDetailComponent, '5');
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-detail-recipient"]')?.textContent).toContain(
      'Кафедра алгебри',
    );
    expect(element.textContent).toContain('Анастасія Коваль');
    expect(element.textContent?.split('Кафедра алгебри').length).toBe(2);
  });

  it('ac0_7_a_personal_award_shows_no_unit_recipient', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');

    expect(
      (fixture.nativeElement as HTMLElement).querySelector(
        '[data-testid="award-detail-recipient"]',
      ),
    ).toBeNull();
  });

  it('ac1_15_a_changed_review_status_reloads_the_award', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    service.get.mockReturnValue(of({ ...pending, status: 'APPROVED' }));

    fixture.componentInstance.refresh(5);
    fixture.detectChanges();

    expect(service.get).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.award()?.status).toBe('APPROVED');
  });

  it('ac1_15_an_award_no_longer_readable_is_replaced_by_the_not_found_notice', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    fixture.componentInstance.refresh(5);

    expect(fixture.componentInstance.award()).toBeNull();
    expect(fixture.componentInstance.notFound()).toBe(true);
  });

  it('ac1_15_a_failed_reload_keeps_the_award', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    fixture.componentInstance.refresh(5);

    expect(fixture.componentInstance.award()).not.toBeNull();
    expect(fixture.componentInstance.notFound()).toBe(false);
  });

  it('ac1_7_a_draft_shows_no_status_panel', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-status-panel"]')).toBeNull();
    expect(service.status).not.toHaveBeenCalled();
  });

  it('ac1_9_offers_editing_of_an_own_draft', async () => {
    granted.set(['award:update:own']);
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-edit"]')).not.toBeNull();
  });

  it('ac1_9_offers_no_editing_without_award_update_own', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-edit"]')).toBeNull();
  });

  it('ac1_8_an_unknown_award_is_not_found', async () => {
    service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const fixture = await open(AwardDetailComponent, '999999');

    expect(
      fixture.nativeElement.querySelector('[data-testid="award-not-found"]')?.textContent,
    ).toContain('Не знайдено');
  });

  it('ac1_8_a_malformed_id_is_not_found_without_a_request', async () => {
    const fixture = await open(AwardDetailComponent, 'abc');

    expect(fixture.nativeElement.querySelector('[data-testid="award-not-found"]')).not.toBeNull();
    expect(service.get).not.toHaveBeenCalled();
  });

  it('ac1_9_the_confirmation_names_the_award_and_the_faculty_secretary', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardSubmittedComponent, '5');
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-submitted-name"]')?.textContent).toContain(
      'Грамота МОН',
    );
    expect(element.querySelector('[data-testid="award-submitted-text"]')?.textContent).toContain(
      'секретарю факультету',
    );
  });

  it('f1_a_draft_is_no_submission_and_opens_its_form', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardSubmittedComponent, '5');

    expect(navigate).toHaveBeenCalledWith(['/awards', 5, 'edit'], { replaceUrl: true });
    expect(fixture.nativeElement.querySelector('[data-testid="award-submitted"]')).toBeNull();
  });

  it('f1_somebody_elses_award_opens_read_only_without_a_confirmation', async () => {
    service.get.mockReturnValue(of({ ...pending, owner: { ...pending.owner, id: 22 } }));
    const fixture = await open(AwardSubmittedComponent, '5');

    expect(navigate).toHaveBeenCalledWith(['/awards', 5], { replaceUrl: true });
    expect(fixture.nativeElement.querySelector('[data-testid="award-submitted"]')).toBeNull();
  });

  it('f1_an_unknown_or_malformed_id_confirms_nothing', async () => {
    service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const unknown = await open(AwardSubmittedComponent, '999999');
    expect(unknown.nativeElement.querySelector('[data-testid="award-submitted"]')).toBeNull();
    expect(unknown.nativeElement.querySelector('[data-testid="award-not-found"]')).not.toBeNull();

    TestBed.resetTestingModule();
    service.get.mockClear();
    const malformed = await open(AwardSubmittedComponent, 'abc');
    expect(malformed.nativeElement.querySelector('[data-testid="award-not-found"]')).not.toBeNull();
    expect(service.get).not.toHaveBeenCalled();
  });

  it('edge_a_server_failure_on_the_confirmation_is_no_not_found', async () => {
    service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const fixture = await open(AwardSubmittedComponent, '5');
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-submitted-error"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="award-not-found"]')).toBeNull();
  });

  it('f9_an_own_draft_is_deleted_after_confirmation', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    service.remove.mockReturnValue(of(undefined));
    const fixture = await open(AwardDetailComponent, '5');
    const button = fixture.nativeElement.querySelector(
      '[data-testid="award-remove"]',
    ) as HTMLButtonElement;

    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    button.click();
    expect(service.remove).not.toHaveBeenCalled();

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    button.click();
    expect(service.remove).toHaveBeenCalledWith(5);
    expect(navigate).toHaveBeenCalledWith(['/awards'], {
      replaceUrl: true,
      state: { notice: 'awards.messages.removed' },
    });
  });

  it('f9_a_draft_submitted_meanwhile_is_not_deleted', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    service.remove.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { type: 'urn:awards:problem:award-not-editable' },
          }),
      ),
    );
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    const fixture = await open(AwardDetailComponent, '5');

    fixture.componentInstance.remove(fixture.componentInstance.award() as Award);

    expect(fixture.componentInstance.notice()).toBe('awards.problems.award-not-editable');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac2_1_the_owner_sees_the_history_of_a_draft_without_an_audit_tab', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-history-section"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="award-history-tabs"]')).toBeNull();
    expect(service.versions).toHaveBeenCalledWith(5, 0, 20);
  });

  it('ac2_4_the_draft_of_somebody_else_requests_no_history', async () => {
    service.get.mockReturnValue(
      of({ ...pending, status: 'DRAFT', request: null, owner: { ...pending.owner, id: 22 } }),
    );
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-history-section"]')).toBeNull();
    expect(service.versions).not.toHaveBeenCalled();
  });

  it('ac2_4_a_reader_of_a_submitted_award_sees_its_history', async () => {
    service.get.mockReturnValue(of({ ...pending, owner: { ...pending.owner, id: 22 } }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(
      fixture.nativeElement.querySelector('[data-testid="award-history-section"]'),
    ).not.toBeNull();
    expect(service.versions).toHaveBeenCalled();
  });

  it('ac2_5_an_auditor_gets_the_history_and_the_audit_log_as_tabs', async () => {
    granted.set(['audit:read']);
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-history-tabs"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="award-audit-tab"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="award-history-section"]')).toBeNull();
  });

  it('f9_a_submitted_award_offers_no_deletion', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-remove"]')).toBeNull();
  });

  it('ac2_7_a_submitted_award_lists_its_documents_without_deletion', async () => {
    documents.list.mockReturnValue(of([documentRow]));
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    const element = fixture.nativeElement as HTMLElement;

    expect(documents.list).toHaveBeenCalledWith(5);
    expect(element.querySelector('[data-testid="document-download"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="document-remove"]')).toBeNull();
    expect(element.querySelector('[data-testid="documents-drop"]')).toBeNull();
  });

  it('ac2_7_the_owner_may_delete_documents_of_a_draft', async () => {
    granted.set(['award:update:own']);
    documents.list.mockReturnValue(of([documentRow]));
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="document-remove"]')).not.toBeNull();
  });
});
