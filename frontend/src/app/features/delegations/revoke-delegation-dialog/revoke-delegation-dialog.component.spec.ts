import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { Delegation } from '../delegations.service';
import { RevokeDelegationDialogComponent } from './revoke-delegation-dialog.component';

const delegation: Delegation = {
  id: 1,
  role: 'DEAN',
  organization: {
    id: 9,
    name: 'Faculty of Mathematics',
    nameUk: 'Факультет математики',
    code: 'FMI',
    type: 'FACULTY',
  },
  delegator: { id: 2, firstName: 'Мартин', lastName: 'Мартинюк', email: 'dean.fmi@chnu.edu.ua' },
  delegate: { id: 3, firstName: 'Аліна', lastName: 'Секретар', email: 'secretary.fmi@chnu.edu.ua' },
  validFrom: '2026-09-24',
  validTo: '2026-10-08',
  reason: null,
  state: 'active',
  createdAt: '2026-09-24T08:00:00Z',
  revokedAt: null,
};

const translations = {
  uk: {
    roles: { DEAN: 'Декан' },
    delegations: {
      revoke: {
        title: 'Відкликати делегування?',
        text: 'Повноваження ролі «{{role}}» у підрозділі {{organization}} буде відкликано у користувача {{name}}.',
        warning: 'Користувача буде відʼєднано на всіх пристроях.',
        confirm: 'Відкликати',
        cancel: 'Скасувати',
      },
    },
  },
};

describe('RevokeDelegationDialogComponent', () => {
  let fixture: ComponentFixture<RevokeDelegationDialogComponent>;
  const close = vi.fn();

  beforeEach(async () => {
    close.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        RevokeDelegationDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: delegation },
        { provide: MatDialogRef, useValue: { close } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RevokeDelegationDialogComponent);
    fixture.detectChanges();
  });

  it('ac3_4_names_the_role_the_organisation_and_the_delegate', () => {
    const text = fixture.nativeElement.querySelector('[data-testid="revoke-delegation-text"]').textContent;

    expect(text).toContain('Декан');
    expect(text).toContain('Факультет математики');
    expect(text).toContain('Аліна Секретар');
  });

  it('ac3_4_closes_with_the_answer_of_the_user', () => {
    fixture.nativeElement.querySelector('[data-testid="revoke-delegation-cancel"]').click();

    expect(close).toHaveBeenCalledWith(false);

    fixture.nativeElement.querySelector('[data-testid="revoke-delegation-confirm"]').click();

    expect(close).toHaveBeenCalledWith(true);
  });
});
