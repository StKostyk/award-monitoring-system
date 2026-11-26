import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { AwardVersionDialogComponent, VersionDialogData } from './award-version-dialog.component';

const data: VersionDialogData = {
  version: {
    number: 1,
    action: 'CREATED',
    actor: null,
    createdAt: '2026-09-30T08:00:00Z',
    snapshot: {
      title: 'Letter',
      titleUk: null,
      description: null,
      descriptionUk: null,
      awardingOrganization: null,
      awardDate: null,
      categoryId: null,
      status: 'DRAFT',
      impactScore: null,
      verificationBadge: false,
      externalUrl: null,
      organizationId: 64,
    },
    changes: [],
    comment: null,
  },
  names: {
    categories: new Map(),
    organizations: new Map([[64, { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри' }]]),
    language: 'uk',
  },
};

describe('AwardVersionDialogComponent', () => {
  it('ac2_2_shows_every_field_of_the_version_with_dashes_for_empty_ones', async () => {
    await TestBed.configureTestingModule({
      imports: [
        AwardVersionDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              awards: {
                status: { DRAFT: 'Чернетка' },
                history: {
                  version: 'Версія {{number}}',
                  actions: { CREATED: 'Створено' },
                  no: 'Ні',
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AwardVersionDialogComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const field = (name: string) =>
      element.querySelector(`[data-testid="version-field-${name}"]`)?.textContent?.trim();

    expect(element.querySelector('[data-testid="version-dialog-title"]')?.textContent).toContain(
      'Версія 1',
    );
    expect(element.querySelectorAll('dd')).toHaveLength(12);
    expect(field('title')).toBe('Letter');
    expect(field('titleUk')).toBe('—');
    expect(field('categoryId')).toBe('—');
    expect(field('organizationId')).toBe('Кафедра алгебри');
    expect(field('status')).toBe('Чернетка');
    expect(field('verificationBadge')).toBe('Ні');
  });
});
