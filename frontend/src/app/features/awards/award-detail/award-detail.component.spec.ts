import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { Award, AwardsService } from '../awards.service';
import { AwardSubmittedComponent } from '../award-submitted/award-submitted.component';
import { AwardDetailComponent } from './award-detail.component';

const pending: Award = {
  id: 5,
  title: 'Ministry letter',
  titleUk: 'Грамота МОН',
  description: null,
  descriptionUk: 'За внесок у розвиток освіти',
  category: { id: 13, name: 'Ministry Recognition', nameUk: 'Відзнака міністерства', level: 'NATIONAL' },
  awardingOrganization: 'МОН України',
  awardDate: '2025-05-01',
  externalUrl: 'https://mon.gov.ua',
  status: 'PENDING',
  impactScore: 80,
  owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
  organization: { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри', code: 'DAI', type: 'DEPARTMENT' },
  request: { status: 'SUBMITTED', currentLevel: 'FACULTY_SECRETARY', submittedAt: '2026-09-28T09:00:00Z' },
  warnings: [],
  createdAt: '2026-09-28T08:00:00Z',
  updatedAt: '2026-09-28T09:00:00Z',
  version: 3,
};

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
  const service = { get: vi.fn() };

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
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id }) } } },
        { provide: AwardsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { userId: signal('21') } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => service.get.mockReset());

  it('ac1_9_shows_a_submitted_award_read_only_with_its_request', async () => {
    service.get.mockReturnValue(of(pending));
    const fixture = await open(AwardDetailComponent, '5');
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-detail-title"]')?.textContent).toContain('Грамота МОН');
    expect(element.querySelector('[data-testid="award-detail-status"]')?.textContent).toContain('На розгляді');
    expect(element.querySelector('[data-testid="award-detail-request"]')?.textContent).toContain(
      'секретар факультету',
    );
    expect(element.querySelector('[data-testid="award-edit"]')).toBeNull();
  });

  it('ac1_9_offers_editing_of_an_own_draft', async () => {
    service.get.mockReturnValue(of({ ...pending, status: 'DRAFT', request: null }));
    const fixture = await open(AwardDetailComponent, '5');

    expect(fixture.nativeElement.querySelector('[data-testid="award-edit"]')).not.toBeNull();
  });

  it('ac1_8_an_unknown_award_is_not_found', async () => {
    service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const fixture = await open(AwardDetailComponent, '999999');

    expect(fixture.nativeElement.querySelector('[data-testid="award-not-found"]')?.textContent).toContain(
      'Не знайдено',
    );
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

    expect(element.querySelector('[data-testid="award-submitted-name"]')?.textContent).toContain('Грамота МОН');
    expect(element.querySelector('[data-testid="award-submitted-text"]')?.textContent).toContain(
      'секретарю факультету',
    );
  });
});
