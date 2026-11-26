import { HttpErrorResponse } from '@angular/common/http';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { Award, AwardVisibility, AwardsService } from '../awards.service';
import { PublishDialogComponent } from './publish-dialog.component';
import { VisibilitySectionComponent } from './visibility-section.component';

const approved = {
  id: 5,
  status: 'APPROVED',
  visibility: 'PRIVATE',
  recipient: { type: 'PERSON', organization: null },
  owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
} as Award;

const translations = {
  uk: {
    awards: {
      visibility: {
        saved: 'Видимість збережено.',
        options: { PRIVATE: 'Лише мені та рецензентам' },
      },
      problems: { 'visibility-fixed': 'Лише затверджена.', unknown: 'Помилка.' },
    },
  },
};

describe('VisibilitySectionComponent', () => {
  const service = { updateVisibility: vi.fn() };
  const dialog = { open: vi.fn() };
  const granted = signal<string[]>(['award:update:own']);
  const permissions = computed(() => ({
    hasPermission: (permission: string) => granted().includes(permission),
    roleScopes: [],
  }));
  let changed: Award[];
  let refused: number;

  async function render(award: Award): Promise<ComponentFixture<VisibilitySectionComponent>> {
    await TestBed.configureTestingModule({
      imports: [
        VisibilitySectionComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: AwardsService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: AuthService, useValue: { permissions } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(VisibilitySectionComponent);
    fixture.componentRef.setInput('award', award);
    fixture.componentInstance.changed.subscribe((saved) => changed.push(saved));
    fixture.componentInstance.refused.subscribe(() => refused++);
    fixture.detectChanges();
    return fixture;
  }

  const checked = (fixture: ComponentFixture<unknown>): string | null =>
    (fixture.nativeElement as HTMLElement)
      .querySelector('mat-radio-button.mat-mdc-radio-checked')
      ?.getAttribute('data-testid') ?? null;

  const answer = (confirmed: boolean): void => {
    dialog.open.mockReturnValue({ afterClosed: () => of(confirmed) });
  };

  beforeEach(() => {
    service.updateVisibility.mockReset();
    dialog.open.mockReset();
    granted.set(['award:update:own']);
    changed = [];
    refused = 0;
  });

  it('ac1_1_shows_the_current_choice_among_the_three', async () => {
    const fixture = await render(approved);
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelectorAll('mat-radio-button')).toHaveLength(3);
    expect(checked(fixture)).toBe('visibility-PRIVATE');
  });

  it('ac1_2_colleagues_are_saved_without_a_question', async () => {
    const saved = { ...approved, visibility: 'UNIVERSITY' as AwardVisibility };
    service.updateVisibility.mockReturnValue(of(saved));
    const fixture = await render(approved);

    fixture.componentInstance.choose('UNIVERSITY');
    fixture.detectChanges();

    expect(dialog.open).not.toHaveBeenCalled();
    expect(service.updateVisibility).toHaveBeenCalledWith(5, 'UNIVERSITY');
    expect(changed).toEqual([saved]);
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="visibility-notice"]')
        ?.textContent,
    ).toContain('Видимість збережено.');
  });

  it('ac1_3_cancelling_the_publication_sends_nothing_and_keeps_the_choice', async () => {
    answer(false);
    const fixture = await render(approved);

    fixture.componentInstance.choose('PUBLIC');
    fixture.detectChanges();

    expect(dialog.open).toHaveBeenCalledWith(PublishDialogComponent, expect.anything());
    expect(service.updateVisibility).not.toHaveBeenCalled();
    expect(checked(fixture)).toBe('visibility-PRIVATE');
  });

  it('ac1_3_a_confirmed_publication_is_saved', async () => {
    answer(true);
    service.updateVisibility.mockReturnValue(of({ ...approved, visibility: 'PUBLIC' }));
    const fixture = await render(approved);

    fixture.componentInstance.choose('PUBLIC');

    expect(service.updateVisibility).toHaveBeenCalledWith(5, 'PUBLIC');
    expect(changed).toHaveLength(1);
  });

  it('ac1_4_a_refusal_says_why_restores_the_choice_and_asks_for_a_reload', async () => {
    service.updateVisibility.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { type: 'urn:awards:problem:visibility-fixed' },
          }),
      ),
    );
    const fixture = await render(approved);

    fixture.componentInstance.choose('UNIVERSITY');
    fixture.detectChanges();

    expect(checked(fixture)).toBe('visibility-PRIVATE');
    expect(refused).toBe(1);
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="visibility-notice"]')
        ?.textContent,
    ).toContain('Лише затверджена.');
  });

  it('ac1_4_without_award_update_own_the_choice_is_read_only', async () => {
    granted.set([]);
    const fixture = await render(approved);

    expect(
      (fixture.nativeElement as HTMLElement).querySelector(
        'mat-radio-button.mat-mdc-radio-disabled',
      ),
    ).not.toBeNull();
  });
});

describe('PublishDialogComponent', () => {
  it('ac1_3_lists_what_is_published_and_what_is_not', async () => {
    await TestBed.configureTestingModule({
      imports: [
        PublishDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PublishDialogComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelectorAll('[data-testid="publish-shown"] li')).toHaveLength(9);
    expect(element.querySelector('[data-testid="publish-kept"]')?.textContent).toContain(
      'awards.visibility.publish.items.email',
    );
  });
});
