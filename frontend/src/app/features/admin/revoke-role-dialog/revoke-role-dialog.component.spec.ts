import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { RevokeRoleDialogComponent } from './revoke-role-dialog.component';

const assignment = {
  id: 3,
  role: 'FACULTY_SECRETARY' as const,
  organization: {
    id: 9,
    name: 'Faculty of Mathematics',
    nameUk: 'Факультет математики',
    code: 'FMI',
    type: 'FACULTY' as const,
  },
  validFrom: '2026-09-01',
  validTo: null,
};

describe('RevokeRoleDialogComponent', () => {
  const close = vi.fn();

  beforeEach(async () => {
    close.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        RevokeRoleDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              roles: { FACULTY_SECRETARY: 'Секретар факультету' },
              admin: {
                revoke: {
                  title: 'Відкликати роль?',
                  text: 'Роль «{{role}}» у підрозділі {{organization}} буде відкликано у {{name}}.',
                  warning: 'Користувача буде відʼєднано на всіх пристроях.',
                  confirm: 'Відкликати',
                  cancel: 'Скасувати',
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { assignment, name: 'Анастасія Працівник' } },
        { provide: MatDialogRef, useValue: { close } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
  });

  it('ac2_10_warns_that_revoking_signs_the_user_out_everywhere', () => {
    const fixture = TestBed.createComponent(RevokeRoleDialogComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="revoke-text"]')?.textContent).toContain(
      'Роль «Секретар факультету» у підрозділі Факультет математики буде відкликано у Анастасія Працівник.',
    );
    expect(element.textContent).toContain('відʼєднано на всіх пристроях');

    (element.querySelector('[data-testid="revoke-confirm"]') as HTMLButtonElement).click();

    expect(close).toHaveBeenCalledWith(true);
  });

  it('closes without revoking on cancel', () => {
    const fixture = TestBed.createComponent(RevokeRoleDialogComponent);
    fixture.detectChanges();

    (
      fixture.nativeElement.querySelector('[data-testid="revoke-cancel"]') as HTMLButtonElement
    ).click();

    expect(close).toHaveBeenCalledWith(false);
  });
});
