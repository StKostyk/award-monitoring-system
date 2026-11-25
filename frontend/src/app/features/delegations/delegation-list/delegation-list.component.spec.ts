import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
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

const upcoming: Delegation = {
  ...given,
  id: 4,
  validFrom: '2026-10-20',
  validTo: '2026-10-30',
  reason: null,
  state: 'upcoming',
};
const expired: Delegation = { ...given, id: 3, state: 'expired' };

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
      personTitle: 'Делегування',
      period: '{{from}} — {{to}}',
      states: {
        active: 'Активне',
        upcoming: 'Заплановане',
        expired: 'Завершене',
        revoked: 'Відкликано',
      },
      messages: { created: 'Повноваження делеговано.', revoked: 'Делегування відкликано.' },
      create: { title: 'Делегувати повноваження' },
      revoke: { action: 'Відкликати' },
      board: {
        given: 'Від мене · {{count}}',
        received: 'Мені · {{count}}',
        givenBy: 'Видані · {{count}}',
        receivedBy: 'Отримані · {{count}}',
        columns: { active: 'Активні', upcoming: 'Заплановані', ended: 'Завершені' },
        empty: {
          active: 'Немає активних делегувань',
          upcoming: 'Немає запланованих делегувань',
          ended: 'Немає завершених делегувань',
        },
        limits: 'Межі повноважень',
        showAll: 'Показати всі · {{count}}',
        showFewer: 'Згорнути',
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

  beforeEach(async () => {
    dialog.open.mockReset();
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
      given: [given, upcoming, expired],
      received: [received],
    },
  ): Promise<void> {
    await harness.navigateByUrl(url);
    store.dispatch(DelegationsActions.delegationsLoaded({ list }));
    harness.detectChanges();
  }

  function query(selector: string, root?: HTMLElement | null): HTMLElement | null {
    return (root ?? harness.routeNativeElement)?.querySelector(selector) ?? null;
  }

  function queryAll(selector: string, root?: HTMLElement | null): HTMLElement[] {
    return Array.from((root ?? harness.routeNativeElement)?.querySelectorAll(selector) ?? []);
  }

  function column(key: string): HTMLElement | null {
    return query(`[data-testid="column-${key}"]`);
  }

  async function showReceived(): Promise<void> {
    query('[data-testid="side-received"] button')?.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  it('ac6_1_shows_the_side_toggle_with_counts_and_three_columns', async () => {
    await open('/delegations');

    expect(query('[data-testid="side-given"]')?.textContent).toContain('Від мене · 3');
    expect(query('[data-testid="side-received"]')?.textContent).toContain('Мені · 1');
    expect(
      queryAll('[data-testid^="column-"] h2').map((title) => title.textContent?.trim()),
    ).toEqual([
      expect.stringContaining('Активні'),
      expect.stringContaining('Заплановані'),
      expect.stringContaining('Завершені'),
    ]);
  });

  it('ac6_1_sorts_delegations_into_columns_by_state', async () => {
    await open('/delegations');

    expect(queryAll('[data-testid="given-card"]', column('active'))).toHaveLength(1);
    expect(queryAll('[data-testid="given-card"]', column('upcoming'))).toHaveLength(1);
    expect(queryAll('[data-testid="given-card"]', column('ended'))).toHaveLength(1);
    expect(query('[data-testid="card-state"]', column('ended'))?.textContent).toContain(
      'Завершене',
    );
  });

  it('ac6_1_puts_revoked_delegations_among_the_ended_ones', async () => {
    await open('/delegations');
    await showReceived();

    expect(queryAll('[data-testid="received-card"]', column('ended'))).toHaveLength(1);
    expect(query('[data-testid="card-state"]', column('ended'))?.textContent).toContain(
      'Відкликано',
    );
    expect(query('[data-testid="empty-active"]')?.textContent).toContain(
      'Немає активних делегувань',
    );
  });

  it('ac6_1_card_shows_the_other_person_role_unit_dates_and_note', async () => {
    await open('/delegations');
    const card = query('[data-testid="given-card"]', column('active'));

    expect(query('[data-testid="card-person"]', card)?.textContent).toContain('Секретар Аліна');
    expect(query('[data-testid="card-role"]', card)?.textContent).toContain(
      'Декан · Факультет математики',
    );
    expect(query('[data-testid="card-period"]', card)?.textContent).toContain(
      '24.09.2026 — 08.10.2026',
    );
    expect(query('[data-testid="card-note"]', card)?.textContent).toContain('Відпустка');
    expect(query('.delegations__initials', card)?.textContent).toContain('СА');
    expect(query('[data-testid="card-note"]', column('upcoming'))).toBeNull();
  });

  it('ac6_1_received_cards_name_the_delegator', async () => {
    await open('/delegations');
    await showReceived();

    expect(query('[data-testid="card-person"]')?.textContent).toContain('Мартинюк Мартин');
  });

  it('ac6_1_opens_on_received_when_nothing_was_given', async () => {
    await open('/delegations', { given: [], received: [received] });

    expect(queryAll('[data-testid="received-card"]')).toHaveLength(1);
    expect(queryAll('[data-testid="given-card"]')).toHaveLength(0);
  });

  it('ac7_3_shows_the_days_left_of_an_active_delegation', async () => {
    vi.useFakeTimers({ now: new Date('2026-09-30T09:00:00Z'), toFake: ['Date'] });
    try {
      await open('/delegations');

      expect(query('[data-testid="card-days-left"]', column('active'))?.textContent).toContain(
        'Залишилося 9 днів',
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it('ac7_4_lists_what_the_delegate_can_and_cannot_do_from_the_role_rules', async () => {
    await open('/delegations');
    const card = query('[data-testid="given-card"]', column('active'));

    expect(query('[data-testid="card-limits"] summary', card)?.textContent).toContain(
      'Межі повноважень',
    );
    expect(query('[data-testid="card-can"]', card)?.textContent).toContain(
      'Розглядати подання на рівні факультету',
    );
    expect(query('[data-testid="card-cannot"]', card)?.textContent).toContain(
      'Додавати користувачів чи змінювати їхні ролі',
    );
  });

  it('ac6_2_offers_revocation_only_on_active_and_scheduled_given_cards', async () => {
    await open('/delegations');

    expect(queryAll('[data-testid="revoke-delegation"]', column('active'))).toHaveLength(1);
    expect(queryAll('[data-testid="revoke-delegation"]', column('upcoming'))).toHaveLength(1);
    expect(queryAll('[data-testid="revoke-delegation"]', column('ended'))).toHaveLength(0);
  });

  it('ac6_2_received_cards_have_no_action', async () => {
    const running: Delegation = { ...given, id: 5 };
    await open('/delegations', { given: [], received: [running, received] });

    expect(queryAll('[data-testid="received-card"]')).toHaveLength(2);
    expect(query('[data-testid="revoke-delegation"]')).toBeNull();
  });

  it('ac5_3_reads_another_persons_page_without_creating_or_revoking', async () => {
    await open('/delegations?delegatorId=5');

    expect(store.selectSignal(delegationsFeature.selectDelegatorId)()).toBe(5);
    expect(queryAll('[data-testid="given-card"]')).toHaveLength(3);
    expect(query('[data-testid="revoke-delegation"]')).toBeNull();
    expect(query('[data-testid="delegate-open"]')).toBeNull();
    expect(query('h1')?.textContent).toContain('Делегування');
    expect(query('h1')?.textContent).not.toContain('Мої');
    expect(query('[data-testid="side-given"]')?.textContent).toContain('Видані · 3');
    expect(query('[data-testid="side-received"]')?.textContent).toContain('Отримані · 1');
  });

  it('ac6_3_keeps_the_columns_in_the_order_active_scheduled_ended', async () => {
    await open('/delegations');

    expect(
      queryAll('[data-testid="delegations-board"] > section').map((section) =>
        section.getAttribute('data-testid'),
      ),
    ).toEqual(['column-active', 'column-upcoming', 'column-ended']);
  });

  it('ac3_4_asks_for_a_confirmation_before_revoking', async () => {
    await open('/delegations');
    const dispatch = vi.spyOn(store, 'dispatch');
    const button = query('[data-testid="revoke-delegation"]', column('active'));
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });

    button?.click();

    expect(dispatch).not.toHaveBeenCalled();

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    button?.click();

    expect(dispatch).toHaveBeenCalledWith(DelegationsActions.revokeRequested({ id: 1 }));
  });

  it('ac3_4_renders_a_refused_revocation_on_the_card', async () => {
    await open('/delegations');
    store.dispatch(DelegationsActions.revokeFailed({ id: 1, problem: 'delegation-not-active' }));
    harness.detectChanges();

    expect(
      query('[data-testid="revoke-delegation-error"]', column('active'))?.textContent,
    ).toContain('Це делегування вже відкликано');
  });

  it('ac3_4_keeps_a_refusal_visible_after_the_card_moved_to_ended', async () => {
    await open('/delegations');
    store.dispatch(DelegationsActions.revokeFailed({ id: 1, problem: 'delegation-not-active' }));
    store.dispatch(
      DelegationsActions.delegationsLoaded({
        list: { given: [{ ...given, state: 'revoked' }], received: [] },
      }),
    );
    harness.detectChanges();

    expect(query('[data-testid="revoke-delegation"]', column('ended'))).toBeNull();
    expect(query('[data-testid="revoke-delegation-error"]', column('ended'))).not.toBeNull();
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

  it('ac3_6_shows_an_empty_text_in_each_column_when_nothing_is_delegated', async () => {
    await open('/delegations', { given: [], received: [] });

    expect(query('[data-testid="empty-active"]')?.textContent).toContain(
      'Немає активних делегувань',
    );
    expect(query('[data-testid="empty-upcoming"]')?.textContent).toContain(
      'Немає запланованих делегувань',
    );
    expect(query('[data-testid="empty-ended"]')?.textContent).toContain(
      'Немає завершених делегувань',
    );
  });

  it('ac6_1_shows_the_latest_ended_delegations_until_all_are_asked_for', async () => {
    const ended = Array.from({ length: 7 }, (_, index) => ({ ...expired, id: 10 + index }));
    await open('/delegations', { given: ended, received: [] });

    expect(queryAll('[data-testid="given-card"]', column('ended'))).toHaveLength(5);
    expect(query('.delegations__count', column('ended'))?.textContent).toContain('7');
    expect(query('[data-testid="card-limits"]', column('ended'))).toBeNull();

    query('[data-testid="ended-all"]')?.click();
    harness.detectChanges();

    expect(queryAll('[data-testid="given-card"]', column('ended'))).toHaveLength(7);
    expect(query('[data-testid="ended-all"]')?.textContent).toContain('Згорнути');
    expect(query('[data-testid="ended-all"]')?.getAttribute('aria-expanded')).toBe('true');

    query('[data-testid="ended-all"]')?.click();
    harness.detectChanges();

    expect(queryAll('[data-testid="given-card"]', column('ended'))).toHaveLength(5);
  });

  it('ac6_1_orders_revocations_by_the_kyiv_day', async () => {
    const lateEvening: Delegation = {
      ...given,
      id: 30,
      state: 'revoked',
      reason: 'Пізно ввечері',
      revokedAt: '2026-11-23T22:30:00Z',
    };
    const morning: Delegation = {
      ...given,
      id: 31,
      state: 'revoked',
      reason: 'Зранку',
      revokedAt: '2026-11-23T08:00:00Z',
    };
    await open('/delegations', { given: [morning, lateEvening], received: [] });

    const shown = queryAll('[data-testid="card-note"]', column('ended'));
    expect(shown.map((note) => note.textContent)).toEqual(['Пізно ввечері', 'Зранку']);
  });

  it('ac6_1_orders_ended_delegations_by_the_day_they_ended', async () => {
    const planned = Array.from({ length: 5 }, (_, index) => ({
      ...expired,
      id: 20 + index,
      state: 'revoked' as const,
      validFrom: '2026-12-10',
      validTo: '2026-12-12',
      revokedAt: '2026-11-01T08:00:00Z',
      reason: 'Планове',
    }));
    const justRevoked: Delegation = {
      ...given,
      state: 'revoked',
      revokedAt: '2026-11-24T08:00:00Z',
    };
    await open('/delegations', { given: [...planned, justRevoked], received: [] });

    const shown = queryAll('[data-testid="given-card"]', column('ended'));
    expect(shown).toHaveLength(5);
    expect(shown[0].textContent).toContain('Відпустка');
  });

  it('ac6_1_folds_the_ended_column_again_on_the_other_side', async () => {
    const ended = Array.from({ length: 7 }, (_, index) => ({ ...expired, id: 10 + index }));
    await open('/delegations', { given: [given], received: ended });
    await showReceived();
    query('[data-testid="ended-all"]')?.click();
    harness.detectChanges();
    expect(queryAll('[data-testid="received-card"]', column('ended'))).toHaveLength(7);

    query('[data-testid="side-given"] button')?.click();
    harness.detectChanges();
    await showReceived();

    expect(queryAll('[data-testid="received-card"]', column('ended'))).toHaveLength(5);
  });

  it('ac6_1_keeps_the_side_when_a_reload_changes_the_lists', async () => {
    await open('/delegations', { given: [], received: [received] });
    expect(queryAll('[data-testid="received-card"]')).toHaveLength(1);

    store.dispatch(
      DelegationsActions.delegationsLoaded({ list: { given: [given], received: [received] } }),
    );
    harness.detectChanges();

    expect(queryAll('[data-testid="received-card"]')).toHaveLength(1);
    expect(queryAll('[data-testid="given-card"]')).toHaveLength(0);
  });

  it('ac6_1_shows_the_given_side_after_a_new_delegation', async () => {
    await open('/delegations');
    await showReceived();
    dialog.open.mockReturnValue({ afterClosed: () => of(upcoming) });

    query('[data-testid="delegate-open"]')?.click();
    harness.detectChanges();

    expect(query('[data-testid="side-given"]')?.className).toContain('checked');
  });

  it('ac7_3_shows_no_board_beside_a_failed_load', async () => {
    await harness.navigateByUrl('/delegations');
    store.dispatch(DelegationsActions.delegationsLoadFailed({ problem: 'network' }));
    harness.detectChanges();

    expect(query('[data-testid="delegations-error"]')).not.toBeNull();
    expect(query('[data-testid="delegations-board"]')).toBeNull();
  });

  it('ac7_3_keeps_the_board_beside_a_failed_reload', async () => {
    await open('/delegations');
    store.dispatch(DelegationsActions.reloaded());
    store.dispatch(DelegationsActions.delegationsLoadFailed({ problem: 'network' }));
    harness.detectChanges();

    expect(query('[data-testid="delegations-error"]')).not.toBeNull();
    expect(queryAll('[data-testid="given-card"]')).toHaveLength(3);
  });
});
