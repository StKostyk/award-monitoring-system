import { HttpErrorResponse } from '@angular/common/http';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { MockInstance, vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
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
  const permissions = computed(() => ({
    hasPermission: (permission: string) => granted().includes(permission),
  }));
  const dialog = { open: vi.fn() };
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

  it('ac1_15_a_changed_review_status_reloads_the_award', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    service.get.mockReturnValue(of({ ...pending, status: 'APPROVED' }));

    fixture.componentInstance.refresh(5);
    fixture.detectChanges();

    expect(service.get).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.award()?.status).toBe('APPROVED');
  });

  it('ac1_7_a_draft_shows_no_status_panel', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-status-panel"]')).toBeNull();
    expect(service.status).not.toHaveBeenCalled();
  });

  it('ac1_9_offers_editing_of_an_own_draft', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-edit"]')).not.toBeNull();
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
});
