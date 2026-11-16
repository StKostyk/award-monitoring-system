import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { Award } from '../../awards/awards.service';
import { PeopleService, PersonDetail } from '../people.service';
import { PersonDialogComponent } from '../person-dialog/person-dialog.component';
import { PersonCardComponent } from './person-card.component';

const department = {
  id: 64,
  name: 'Department of Algebra',
  nameUk: 'Кафедра алгебри',
  code: 'DAI',
  type: 'DEPARTMENT' as const,
};

const award = {
  id: 90,
  title: 'Letter of thanks',
  titleUk: 'Подяка ректора',
  status: 'DRAFT',
} as Award;

const person: PersonDetail = {
  id: 41,
  firstName: 'Петро',
  lastName: 'Іваненко',
  email: 'p.ivanenko@chnu.edu.ua',
  organization: department,
  status: 'UNCLAIMED',
  addedBy: { id: 3, name: 'Секретар ФМІ', email: 'secretary.fmi@chnu.edu.ua' },
  invitedAt: '2026-11-16T10:00:00Z',
  awardCount: 1,
  version: 2,
  awards: [award],
};

const translations = {
  uk: {
    awards: { status: { DRAFT: 'Чернетка' } },
    admin: {
      people: {
        invitedAt: 'Надіслано {{at}}',
        notInvited: 'Запрошення не надіслано: адреса невідома',
        noAwards: 'Нагород ще немає',
        invited: 'Запрошення надіслано.',
        changed: 'Зміни збережено.',
        problems: {
          'too-many-requests': 'Запрошення вже надіслано, спробуйте пізніше.',
          'person-has-awards': 'У колеги є нагороди',
        },
      },
    },
  },
};

describe('PersonCardComponent', () => {
  let fixture: ComponentFixture<PersonCardComponent>;
  let api: Record<'get' | 'invite' | 'remove', ReturnType<typeof vi.fn>>;
  let dialog: { open: ReturnType<typeof vi.fn> };

  function render(detail: PersonDetail): HTMLElement {
    api.get.mockReturnValue(of(detail));
    fixture = TestBed.createComponent(PersonCardComponent);
    fixture.componentRef.setInput('personId', detail.id);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function click(element: HTMLElement, testId: string): void {
    (element.querySelector(`[data-testid="${testId}"]`) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function text(element: HTMLElement, testId: string): string {
    return element.querySelector(`[data-testid="${testId}"]`)?.textContent ?? '';
  }

  beforeEach(async () => {
    api = { get: vi.fn(), invite: vi.fn(), remove: vi.fn() };
    dialog = { open: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [
        PersonCardComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: PeopleService, useValue: api },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
  });

  it('ac1_9_shows_who_added_the_person_the_invitation_and_the_awards', () => {
    const element = render(person);

    expect(text(element, 'person-added-by')).toContain('Секретар ФМІ');
    expect(text(element, 'person-invitation')).toContain('Надіслано 16.11.2026, 12:00');
    expect(text(element, 'person-awards')).toContain('Подяка ректора');
    expect(text(element, 'person-awards')).toContain('Чернетка');
    expect(element.querySelector('[data-testid="person-change"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="person-remove"]')).not.toBeNull();
  });

  it('ac1_4_says_no_invitation_went_and_offers_no_resend_without_an_address', () => {
    const element = render({ ...person, email: null, invitedAt: null, awards: [] });

    expect(text(element, 'person-invitation')).toContain(
      'Запрошення не надіслано: адреса невідома',
    );
    expect(text(element, 'person-no-awards')).toContain('Нагород ще немає');
    expect(element.querySelector('[data-testid="person-invite"]')).toBeNull();
  });

  it('ac1_9_offers_no_actions_once_the_account_is_claimed', () => {
    const element = render({ ...person, status: 'ACTIVE' });

    expect(element.querySelector('[data-testid="person-change"]')).toBeNull();
    expect(element.querySelector('[data-testid="person-invite"]')).toBeNull();
    expect(element.querySelector('[data-testid="person-remove"]')).toBeNull();
  });

  it('ac1_5_resends_the_invitation_and_reports_the_interval', () => {
    api.invite
      .mockReturnValueOnce(of(undefined))
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 429 })));
    const element = render(person);

    click(element, 'person-invite');
    expect(text(element, 'person-message')).toContain('Запрошення надіслано.');
    expect(api.get).toHaveBeenCalledTimes(2);

    click(element, 'person-invite');
    expect(text(element, 'person-error')).toContain('Запрошення вже надіслано, спробуйте пізніше.');
  });

  it('ac1_6_opens_the_change_dialog_and_tells_the_page_after_a_save', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(person) });
    const element = render(person);
    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);

    click(element, 'person-change');

    expect(dialog.open).toHaveBeenCalledWith(
      PersonDialogComponent,
      expect.objectContaining({ data: { person } }),
    );
    expect(changed).toHaveBeenCalled();
    expect(text(element, 'person-message')).toContain('Зміни збережено.');
  });

  it('ac1_7_removes_after_confirmation_and_returns_to_the_list', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    api.remove.mockReturnValue(of(undefined));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const element = render(person);

    click(element, 'person-remove');

    expect(api.remove).toHaveBeenCalledWith(person.id);
    expect(navigate).toHaveBeenCalledWith(['/admin/users']);
  });

  it('ac1_7_explains_why_a_person_with_awards_stays', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    api.remove.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { type: 'urn:awards:problem:person-has-awards' },
          }),
      ),
    );
    const element = render(person);

    click(element, 'person-remove');

    expect(text(element, 'person-error')).toContain('У колеги є нагороди');
  });
});
