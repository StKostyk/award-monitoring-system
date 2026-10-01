import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { AuditTrailEntry, AwardsService, Page } from '../awards.service';
import { AwardAuditTrailComponent } from './award-audit-trail.component';

const submitted: AuditTrailEntry = {
  id: 90,
  createdAt: '2026-09-30T08:00:00Z',
  actorId: 21,
  actorName: 'Анастасія Коваль',
  actorEmail: 'employee.fmi@chnu.edu.ua',
  action: 'UPDATE',
  entityType: 'awards',
  entityId: 5,
  changedFields: ['title'],
  oldValues: { title: 'Letter' },
  newValues: { title: 'Diploma' },
  ipAddress: '10.0.0.1',
  correlationId: '0b5d5c0e-6a52-4b8e-9f43-1c4b4f0a9a11',
};

function page(content: AuditTrailEntry[], totalPages = 1): Page<AuditTrailEntry> {
  return { content, totalElements: content.length, totalPages, size: 20, number: 0 };
}

describe('AwardAuditTrailComponent', () => {
  const service = { auditTrail: vi.fn(), exportAuditTrail: vi.fn() };

  async function render(): Promise<ComponentFixture<AwardAuditTrailComponent>> {
    await TestBed.configureTestingModule({
      imports: [
        AwardAuditTrailComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              awards: {
                audit: {
                  empty: 'Записів немає',
                  problems: { failed: 'Не вдалося', gone: 'Недоступний', denied: 'Немає доступу' },
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: AwardsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(AwardAuditTrailComponent);
    fixture.componentRef.setInput('awardId', 5);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    service.auditTrail.mockReset();
    service.exportAuditTrail.mockReset();
  });

  afterEach(() => vi.restoreAllMocks());

  it('ac2_5_lists_the_rows_with_their_old_and_new_values', async () => {
    service.auditTrail.mockReturnValue(of(page([submitted])));
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;

    expect(service.auditTrail).toHaveBeenCalledWith(5, 0, 20);
    expect(element.querySelector('[data-testid="audit-action"]')?.textContent).toContain('UPDATE');
    expect(element.textContent).toContain('Анастасія Коваль');
    expect(element.textContent).toContain('10.0.0.1');
    expect(element.querySelector('[data-testid="audit-old"]')?.textContent).toContain(
      '"title": "Letter"',
    );
    expect(element.querySelector('[data-testid="audit-new"]')?.textContent).toContain(
      '"title": "Diploma"',
    );
  });

  it('ac2_5_names_an_unknown_actor_by_id_or_a_dash', async () => {
    service.auditTrail.mockReturnValue(of(page([])));
    const fixture = await render();
    const component = fixture.componentInstance;

    expect(component.actor({ ...submitted, actorName: null })).toBe('employee.fmi@chnu.edu.ua');
    expect(component.actor({ ...submitted, actorName: null, actorEmail: null })).toBe('#21');
    expect(
      component.actor({ ...submitted, actorName: null, actorEmail: null, actorId: null }),
    ).toBe('—');
    expect(component.json({})).toBe('—');
  });

  it('ac2_5_a_trail_with_no_rows_is_empty_not_failed', async () => {
    service.auditTrail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="audit-empty"]')?.textContent).toContain(
      'Записів немає',
    );
    expect(element.querySelector('[data-testid="audit-error"]')).toBeNull();
  });

  it('ac2_3_a_failed_load_offers_a_retry_and_more_pages_a_button', async () => {
    service.auditTrail
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
      .mockReturnValueOnce(of(page([submitted], 2)));
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;

    (element.querySelector('[data-testid="audit-retry"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.querySelectorAll('[data-testid="audit-row"]')).toHaveLength(1);
    expect(element.querySelector('[data-testid="audit-more"]')).not.toBeNull();
  });

  it('f2_an_auditor_who_lost_the_role_is_told_so_without_a_retry', async () => {
    service.auditTrail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="audit-error"]')?.textContent).toContain(
      'Немає доступу',
    );
    expect(element.querySelector('[data-testid="audit-retry"]')).toBeNull();
    expect(element.querySelector('[data-testid="audit-empty"]')).toBeNull();
  });

  it('f1_a_404_on_a_later_page_ends_the_list', async () => {
    service.auditTrail
      .mockReturnValueOnce(of(page([submitted], 2)))
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;

    (element.querySelector('[data-testid="audit-more"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="audit-more"]')).toBeNull();
    expect(element.querySelector('[data-testid="audit-retry"]')).toBeNull();
    expect(element.querySelectorAll('[data-testid="audit-row"]')).toHaveLength(1);
  });

  it('f3_a_row_pushed_onto_the_next_page_is_shown_once', async () => {
    service.auditTrail
      .mockReturnValueOnce(of(page([{ ...submitted, id: 91 }, submitted], 2)))
      .mockReturnValueOnce(of(page([submitted, { ...submitted, id: 89 }], 2)));
    const fixture = await render();

    (
      fixture.nativeElement.querySelector('[data-testid="audit-more"]') as HTMLButtonElement
    ).click();

    expect(fixture.componentInstance.rows().map((row) => row.id)).toEqual([91, 90, 89]);
  });

  it('f3_an_export_reloads_the_list_from_the_first_page_with_its_own_row', async () => {
    const exported = { ...submitted, id: 95, action: 'AUDIT_EXPORT' };
    service.auditTrail
      .mockReturnValueOnce(of(page([submitted], 2)))
      .mockReturnValueOnce(of(page([exported, submitted], 2)));
    service.exportAuditTrail.mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) })));
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit');
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockReturnValue(undefined);
    const fixture = await render();

    fixture.componentInstance.exportCsv();

    expect(service.auditTrail).toHaveBeenLastCalledWith(5, 0, 20);
    expect(fixture.componentInstance.rows().map((row) => row.id)).toEqual([95, 90]);
  });

  it('f2_an_export_refused_for_access_says_so', async () => {
    service.auditTrail.mockReturnValue(of(page([submitted])));
    service.exportAuditTrail.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    const fixture = await render();

    fixture.componentInstance.exportCsv();

    expect(fixture.componentInstance.notice()).toBe('awards.audit.problems.denied');
  });

  it('ac2_6_downloads_the_file_under_its_attachment_name', async () => {
    service.auditTrail.mockReturnValue(of(page([submitted])));
    service.exportAuditTrail.mockReturnValue(
      of(
        new HttpResponse({
          body: new Blob(['csv']),
          headers: new HttpHeaders({
            'Content-Disposition': 'attachment; filename="award-5-audit-2026-09-30.csv"',
          }),
        }),
      ),
    );
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit');
    vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockReturnValue(undefined);
    const fixture = await render();

    (
      fixture.nativeElement.querySelector('[data-testid="audit-export"]') as HTMLButtonElement
    ).click();

    expect(service.exportAuditTrail).toHaveBeenCalledWith(5);
    expect(createUrl).toHaveBeenCalled();
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe(
      'award-5-audit-2026-09-30.csv',
    );
    expect(fixture.componentInstance.notice()).toBeNull();
  });

  it('ac2_7_a_truncated_export_is_pointed_out', async () => {
    service.auditTrail.mockReturnValue(of(page([submitted])));
    service.exportAuditTrail.mockReturnValue(
      of(
        new HttpResponse({
          body: new Blob(['csv']),
          headers: new HttpHeaders({ 'X-Audit-Truncated': 'true' }),
        }),
      ),
    );
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit');
    vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockReturnValue(undefined);
    const fixture = await render();

    fixture.componentInstance.exportCsv();

    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('award-5-audit.csv');
    expect(fixture.componentInstance.notice()).toBe('awards.audit.truncated');
  });

  it('ac2_6_a_failed_export_says_so', async () => {
    service.auditTrail.mockReturnValue(of(page([submitted])));
    service.exportAuditTrail.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    const fixture = await render();

    fixture.componentInstance.exportCsv();

    expect(fixture.componentInstance.notice()).toBe('awards.audit.exportFailed');
  });
});
