import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
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

const received: Delegation = {
  ...given,
  id: 2,
  state: 'revoked',
  revokedAt: '2026-09-26T08:00:00Z',
};

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
      columns: { period: 'Період' },
      states: {
        active: 'Активне',
        upcoming: 'Заплановане',
        expired: 'Завершене',
        revoked: 'Відкликано',
      },
      messages: { created: 'Повноваження делеговано.', revoked: 'Делегування відкликано.' },
      create: { title: 'Делегувати повноваження' },
      revoke: { action: 'Відкликати' },
      detail: {
        reason: 'Причина',
        can: 'Може',
        cannot: 'Не може',
        hint: 'Оберіть делегування',
        notFound: 'Такого делегування немає серед ваших.',
        gone: 'Делегування більше немає в списку.',
        endsIn: { many: 'Залишилося {{count}} днів', few: 'Залишилося {{count}} дні' },
      },
      capabilities: {
        can: { approveLevel2: 'Розглядати подання на рівні факультету' },
        cannot: { manageUsers: 'Додавати користувачів чи змінювати їхні ролі' },
      },
      problems: { 'delegation-not-active': 'Це делегування вже відкликано або завершено.' },
    },
  },
};

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('DelegationListComponent', () => {
  let harness: RouterTestingHarness;
  let store: Store;
  const dialog = { open: vi.fn() };
  const snackBar = { open: vi.fn() };

  beforeEach(async () => {
    dialog.open.mockReset();
    snackBar.open.mockReset();
    await TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'delegations', component: DelegationListComponent }]),
        provideStore(),
        provideState(delegationsFeature),
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        {
          provide: AuthService,
          useValue: { permissions: signal(readPermissions(token({ role_scopes: ['DEAN:9'] }))) },
        },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    harness = await RouterTestingHarness.create();
  });

  async function open(
    url: string,
    list: { given: Delegation[]; received: Delegation[] } = {
      given: [given],
      received: [received],
    },
  ): Promise<void> {
    await harness.navigateByUrl(url);
    store.dispatch(DelegationsActions.delegationsLoaded({ list }));
    harness.detectChanges();
  }

  function query(selector: string): HTMLElement | null {
    return harness.routeNativeElement?.querySelector(selector) ?? null;
  }

  function queryAll(selector: string): HTMLElement[] {
    return Array.from(harness.routeNativeElement?.querySelectorAll(selector) ?? []);
  }

  it('ac7_3_lists_given_and_received_delegations_with_person_role_period_and_state', async () => {
    await open('/delegations');
    const givenRow = query('[data-testid="given-row"]');
    const receivedRow = query('[data-testid="received-row"]');

    expect(givenRow?.textContent).toContain('Секретар Аліна');
    expect(givenRow?.textContent).toContain('Декан');
    expect(givenRow?.textContent).toContain('24.09.2026 — 08.10.2026');
    expect(givenRow?.querySelector('[data-testid="given-state"]')?.textContent).toContain(
      'Активне',
    );
    expect(receivedRow?.textContent).toContain('Мартинюк Мартин');
    expect(receivedRow?.querySelector('[data-testid="received-state"]')?.textContent).toContain(
      'Відкликано',
    );
    expect(query('[data-testid="delegation-hint"]')?.textContent).toContain('Оберіть делегування');
  });

  it('ac7_3_opens_a_delegation_from_the_list_and_keeps_it_in_the_url', async () => {
    await open('/delegations');

    query('[data-testid="given-row"]')?.click();
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/delegations?delegation=1');
    expect(query('[data-testid="given-row"]')?.getAttribute('aria-current')).toBe('true');
    expect(query('[data-testid="delegation-detail"]')?.textContent).toContain(
      'Факультет математики',
    );
    expect(query('[data-testid="detail-reason"]')?.textContent).toContain('Відпустка');
    expect(query('[data-testid="detail-period"]')?.textContent).toContain(
      '24.09.2026 — 08.10.2026',
    );
  });

  it('ac7_3_shows_the_days_left_of_an_active_delegation', async () => {
    vi.useFakeTimers({ now: new Date('2026-09-30T09:00:00Z'), toFake: ['Date'] });
    try {
      await open('/delegations?delegation=1');

      expect(query('[data-testid="detail-days-left"]')?.textContent).toContain('Залишилося 9 днів');
    } finally {
      vi.useRealTimers();
    }
  });

  it('ac7_4_lists_what_the_delegate_can_and_cannot_do_from_the_role_rules', async () => {
    await open('/delegations?delegation=1');

    expect(query('[data-testid="detail-can"]')?.textContent).toContain(
      'Розглядати подання на рівні факультету',
    );
    expect(query('[data-testid="detail-cannot"]')?.textContent).toContain(
      'Додавати користувачів чи змінювати їхні ролі',
    );
  });

  it('ac7_3_offers_revocation_only_for_a_given_delegation_that_still_runs', async () => {
    const expired: Delegation = { ...given, id: 3, state: 'expired' };
    await open('/delegations?delegation=1', { given: [given, expired], received: [received] });
    expect(queryAll('[data-testid="revoke-delegation"]')).toHaveLength(1);

    await harness.navigateByUrl('/delegations?delegation=3');
    harness.detectChanges();
    expect(query('[data-testid="delegation-detail"]')).not.toBeNull();
    expect(query('[data-testid="revoke-delegation"]')).toBeNull();

    await harness.navigateByUrl('/delegations?delegation=2');
    harness.detectChanges();
    expect(query('[data-testid="delegation-detail"]')).not.toBeNull();
    expect(query('[data-testid="revoke-delegation"]')).toBeNull();
  });

  it('ac5_3_reads_another_persons_page_without_creating_or_revoking', async () => {
    await open('/delegations?delegatorId=5&delegation=1');

    expect(store.selectSignal(delegationsFeature.selectDelegatorId)()).toBe(5);
    expect(query('[data-testid="delegation-detail"]')).not.toBeNull();
    expect(query('[data-testid="revoke-delegation"]')).toBeNull();
    expect(query('[data-testid="delegate-open"]')).toBeNull();
  });

  it('ac3_4_asks_for_a_confirmation_before_revoking', async () => {
    await open('/delegations?delegation=1');
    const dispatch = vi.spyOn(store, 'dispatch');
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });

    query('[data-testid="revoke-delegation"]')?.click();

    expect(dispatch).not.toHaveBeenCalled();

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    query('[data-testid="revoke-delegation"]')?.click();

    expect(dispatch).toHaveBeenCalledWith(DelegationsActions.revokeRequested({ id: 1 }));
  });

  it('ac3_4_renders_a_refused_revocation_in_the_pane', async () => {
    await open('/delegations?delegation=1');
    store.dispatch(DelegationsActions.revokeFailed({ id: 1, problem: 'delegation-not-active' }));
    harness.detectChanges();

    expect(query('[data-testid="revoke-delegation-error"]')?.textContent).toContain(
      'Це делегування вже відкликано',
    );
  });

  it('ac3_6_reports_a_new_delegation_from_the_dialog', async () => {
    await open('/delegations');
    const dispatch = vi.spyOn(store, 'dispatch');
    dialog.open.mockReturnValue({ afterClosed: () => of(given) });

    query('[data-testid="delegate-open"]')?.click();
    harness.detectChanges();

    expect(dispatch).toHaveBeenCalledWith(DelegationsActions.created({ delegation: given }));
    expect(query('[data-testid="delegations-message"]')?.textContent).toContain(
      'Повноваження делеговано.',
    );
  });

  it('ac3_6_shows_both_empty_states_when_nothing_is_delegated', async () => {
    await open('/delegations', { given: [], received: [] });

    expect(query('[data-testid="given-empty"]')?.textContent).toContain(
      'Ви ще нікому не передавали повноважень',
    );
    expect(query('[data-testid="received-empty"]')?.textContent).toContain(
      'Вам ще не передавали повноважень',
    );
  });

  it('ac7_3_shows_no_empty_hint_beside_a_failed_load', async () => {
    await harness.navigateByUrl('/delegations');
    store.dispatch(DelegationsActions.delegationsLoadFailed({ problem: 'network' }));
    harness.detectChanges();

    expect(query('[data-testid="delegations-error"]')).not.toBeNull();
    expect(query('[data-testid="delegation-hint"]')).toBeNull();
  });

  it('ac7_3_says_so_when_the_delegation_in_the_url_is_not_one_of_the_callers', async () => {
    await open('/delegations?delegation=99');

    expect(query('[data-testid="delegation-not-found"]')?.textContent).toContain(
      'Такого делегування немає серед ваших.',
    );
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('ac7_3_clears_a_shown_delegation_that_left_the_list_and_says_so', async () => {
    await open('/delegations?delegation=1');
    expect(query('[data-testid="delegation-detail"]')).not.toBeNull();

    store.dispatch(
      DelegationsActions.delegationsLoaded({ list: { given: [], received: [received] } }),
    );
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/delegations');
    expect(snackBar.open).toHaveBeenCalledWith(
      'Делегування більше немає в списку.',
      undefined,
      expect.anything(),
    );
  });
});
