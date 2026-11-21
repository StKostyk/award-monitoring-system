import en from '../../../../public/i18n/en.json';
import uk from '../../../../public/i18n/uk.json';
import { RoleType } from '../../core/auth/user-profile';
import { capabilitiesOf } from './delegation-capabilities';
import { daysLeft } from './delegation-days';
import { Delegation } from './delegations.service';

function translated(dictionary: unknown, key: string): unknown {
  return key
    .split('.')
    .reduce<unknown>(
      (node, part) => (node as Record<string, unknown> | undefined)?.[part],
      dictionary,
    );
}

const delegation: Delegation = {
  id: 1,
  role: 'DEAN',
  organization: { id: 9, type: 'FACULTY', name: 'Faculty', nameUk: 'Факультет', code: null },
  delegator: { id: 2, firstName: 'Олена', lastName: 'Декан', email: 'dean@chnu.edu.ua' },
  delegate: { id: 3, firstName: 'Іван', lastName: 'Заступник', email: 'deputy@chnu.edu.ua' },
  validFrom: '2026-11-20',
  validTo: '2026-11-30',
  reason: null,
  state: 'active',
  createdAt: '2026-11-19T10:00:00Z',
  revokedAt: null,
};

describe('Delegation capabilities', () => {
  it('ac7_4_lets_a_borrowed_role_read_and_approve_only_at_its_own_levels', () => {
    expect(capabilitiesOf('FACULTY_SECRETARY').can).toEqual([
      'delegations.capabilities.can.readDepartment',
      'delegations.capabilities.can.approveLevel1',
    ]);
    expect(capabilitiesOf('DEAN').can).toContain('delegations.capabilities.can.approveLevel2');
    expect(capabilitiesOf('DEAN').can).not.toContain('delegations.capabilities.can.readAll');
    expect(capabilitiesOf('RECTOR_SECRETARY').can).not.toContain(
      'delegations.capabilities.can.approveFinal',
    );
    expect(capabilitiesOf('RECTOR').can).toContain('delegations.capabilities.can.approveFinal');
  });

  it('ac7_4_keeps_people_analytics_and_passing_on_with_the_delegator', () => {
    expect(capabilitiesOf('RECTOR').cannot).toEqual([
      'delegations.capabilities.cannot.manageUsers',
      'delegations.capabilities.cannot.analytics',
      'delegations.capabilities.cannot.delegate',
    ]);
    expect(capabilitiesOf('EMPLOYEE').can).toEqual([]);
  });

  it('ac7_4_has_every_line_in_both_languages', () => {
    const roles: RoleType[] = ['FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR'];
    const keys = roles.flatMap((role) => {
      const { can, cannot } = capabilitiesOf(role);
      return [...can, ...cannot];
    });

    for (const key of keys) {
      expect(typeof translated(uk, key), `uk ${key}`).toBe('string');
      expect(typeof translated(en, key), `en ${key}`).toBe('string');
    }
  });

  it('ac7_3_counts_the_days_left_including_today_and_the_days_until_the_start', () => {
    expect(daysLeft(delegation, '2026-11-21')).toEqual({ key: 'endsIn', count: 10 });
    expect(daysLeft(delegation, '2026-11-30')).toEqual({ key: 'endsIn', count: 1 });
    expect(daysLeft({ ...delegation, state: 'upcoming' }, '2026-11-17')).toEqual({
      key: 'startsIn',
      count: 3,
    });
    expect(daysLeft(delegation, '2026-12-01')).toBeNull();
    expect(daysLeft({ ...delegation, state: 'upcoming' }, '2026-11-20')).toBeNull();
    expect(daysLeft({ ...delegation, state: 'expired' }, '2026-12-01')).toBeNull();
    expect(daysLeft({ ...delegation, state: 'revoked' }, '2026-11-21')).toBeNull();
  });
});
