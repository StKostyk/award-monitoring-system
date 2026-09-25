import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { Delegation } from '../delegations.service';
import { DelegationsActions } from '../store/delegations.actions';
import { delegationsFeature } from '../store/delegations.feature';
import { DelegationListComponent } from './delegation-list.component';

const faculty = {
  id: 9,
  name: 'Faculty of Mathematics',
  nameUk: 'Факультет математики',
  code: 'FMI',
  type: 'FACULTY' as const,
};

const dean = { id: 2, firstName: 'Мартин', lastName: 'Мартинюк', email: 'dean.fmi@chnu.edu.ua' };
const secretary = {
  id: 3,
  firstName: 'Аліна',
  lastName: 'Секретар',
  email: 'secretary.fmi@chnu.edu.ua',
};

const given: Delegation = {
  id: 1,
  role: 'DEAN',
  organization: faculty,
  delegator: dean,
  delegate: secretary,
  validFrom: '2026-09-24',
  validTo: '2026-10-08',
  reason: 'Відпустка',
  state: 'active',
  createdAt: '2026-09-24T08:00:00Z',
  revokedAt: null,
};

const received: Delegation = { ...given, id: 2, state: 'revoked', revokedAt: '2026-09-26T08:00:00Z' };

const translations = {
  uk: {
    roles: { DEAN: 'Декан' },
    delegations: {
      title: 'Мої делегування',
      given: 'Надані',
      received: 'Отримані',
      emptyGiven: 'Ви ще нікому не передавали повноважень',
      emptyReceived: 'Вам ще не передавали повноважень',
      period: '{{from}} — {{to}}',
      columns: {
        role: 'Роль',
        organization: 'Підрозділ',
        delegate: 'Кому передано',
        delegator: 'Від кого',
        period: 'Період',
        state: 'Стан',
        actions: 'Дії',
      },
      states: { active: 'Активне', upcoming: 'Заплановане', expired: 'Завершене', revoked: 'Відкликано' },
      messages: { created: 'Повноваження делеговано.', revoked: 'Делегування відкликано.' },
      create: { title: 'Делегувати повноваження' },
      revoke: { action: 'Відкликати' },
      problems: { 'delegation-not-active': 'Це делегування вже відкликано або завершено.' },
    },
  },
};

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('DelegationListComponent', () => {
  let fixture: ComponentFixture<DelegationListComponent>;
  let store: Store;
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    dialog.open.mockReset();
    await TestBed.configureTestingModule({
      imports: [
        DelegationListComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideStore(),
        provideState(delegationsFeature),
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        {
          provide: AuthService,
          useValue: { permissions: signal(readPermissions(token({ role_scopes: ['DEAN:9'] }))) },
        },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    fixture = TestBed.createComponent(DelegationListComponent);
    fixture.detectChanges();
  });

  it('ac3_6_splits_the_delegations_into_given_and_received_with_the_state_chip_of_the_server', () => {
    store.dispatch(DelegationsActions.delegationsLoaded({ list: { given: [given], received: [received] } }));
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const givenRow = element.querySelector('[data-testid="given-table"] tbody tr');
    const receivedRow = element.querySelector('[data-testid="received-table"] tbody tr');

    expect(givenRow?.textContent).toContain('Декан');
    expect(givenRow?.textContent).toContain('Факультет математики');
    expect(givenRow?.textContent).toContain('Секретар Аліна');
    expect(givenRow?.textContent).toContain('2026-09-24 — 2026-10-08');
    expect(givenRow?.querySelector('[data-testid="given-state"]')?.textContent).toContain('Активне');
    expect(receivedRow?.textContent).toContain('Мартинюк Мартин');
    expect(receivedRow?.querySelector('[data-testid="received-state"]')?.textContent).toContain(
      'Відкликано',
    );
  });

  it('ac3_6_offers_revocation_only_while_the_delegation_still_runs', () => {
    store.dispatch(
      DelegationsActions.delegationsLoaded({
        list: { given: [given, { ...given, id: 3, state: 'expired' }], received: [] },
      }),
    );
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('[data-testid="revoke-delegation"]');

    expect(buttons).toHaveLength(1);
  });

  it('ac3_4_asks_for_a_confirmation_before_revoking', () => {
    store.dispatch(DelegationsActions.delegationsLoaded({ list: { given: [given], received: [] } }));
    fixture.detectChanges();
    const dispatch = vi.spyOn(store, 'dispatch');
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });

    fixture.nativeElement.querySelector('[data-testid="revoke-delegation"]').click();

    expect(dispatch).not.toHaveBeenCalled();

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    fixture.nativeElement.querySelector('[data-testid="revoke-delegation"]').click();

    expect(dispatch).toHaveBeenCalledWith(DelegationsActions.revokeRequested({ id: 1 }));
  });

  it('ac3_6_reports_a_new_delegation_from_the_dialog', () => {
    const dispatch = vi.spyOn(store, 'dispatch');
    dialog.open.mockReturnValue({ afterClosed: () => of(given) });

    fixture.nativeElement.querySelector('[data-testid="delegate-open"]').click();
    fixture.detectChanges();

    expect(dispatch).toHaveBeenCalledWith(DelegationsActions.created({ delegation: given }));
    expect(fixture.nativeElement.querySelector('[data-testid="delegations-message"]').textContent).toContain(
      'Повноваження делеговано.',
    );
  });

  it('ac3_4_renders_a_refused_revocation_inline_on_its_row', () => {
    store.dispatch(
      DelegationsActions.delegationsLoaded({
        list: { given: [given, { ...given, id: 4 }], received: [] },
      }),
    );
    store.dispatch(DelegationsActions.revokeFailed({ id: 1, problem: 'delegation-not-active' }));
    fixture.detectChanges();
    const rows: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid="given-table"] tbody tr'),
    );

    expect(rows[0].querySelector('[data-testid="revoke-delegation-error"]')?.textContent).toContain(
      'Це делегування вже відкликано',
    );
    expect(rows[1].querySelector('[data-testid="revoke-delegation-error"]')).toBeNull();
  });

  it('ac3_6_shows_both_empty_states_when_nothing_is_delegated', () => {
    store.dispatch(DelegationsActions.delegationsLoaded({ list: { given: [], received: [] } }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="given-empty"]').textContent).toContain(
      'Ви ще нікому не передавали повноважень',
    );
    expect(fixture.nativeElement.querySelector('[data-testid="received-empty"]').textContent).toContain(
      'Вам ще не передавали повноважень',
    );
  });
});
