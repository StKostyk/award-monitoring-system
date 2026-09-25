import {
  canDelegate,
  canReadDirectory,
  delegatableOrganizations,
  delegatableRoles,
  grantableRoles,
  readPermissions,
} from './permissions';

function token(claims: Record<string, unknown>): string {
  const bytes = new TextEncoder().encode(JSON.stringify(claims));
  const payload = btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${payload}.signature`;
}

describe('permissions', () => {
  it('ac2_10_reads_the_permissions_and_role_scopes_of_the_access_token', () => {
    const permissions = readPermissions(
      token({
        permissions: ['user:read:scope', 'user:manage:scope'],
        role_scopes: ['DEAN:9', 'EMPLOYEE:64'],
      }),
    );

    expect(permissions.permissions).toEqual(['user:read:scope', 'user:manage:scope']);
    expect(permissions.roleScopes).toEqual([
      { role: 'DEAN', organizationId: 9 },
      { role: 'EMPLOYEE', organizationId: 64 },
    ]);
    expect(permissions.hasPermission('user:manage:scope')).toBe(true);
    expect(permissions.hasPermission('user:manage')).toBe(false);
    expect(canReadDirectory(permissions)).toBe(true);
  });

  it('reads a non-ascii claim and a space separated permission string', () => {
    const permissions = readPermissions(
      token({ permissions: 'user:read:all user:manage', name: 'Мартин' }),
    );

    expect(permissions.permissions).toEqual(['user:read:all', 'user:manage']);
    expect(canReadDirectory(permissions)).toBe(true);
  });

  it('yields nothing instead of throwing for a missing or broken token', () => {
    for (const value of [
      null,
      undefined,
      '',
      'not-a-token',
      'a.b.c',
      `header.${btoa('[1,2]')}.s`,
    ]) {
      const permissions = readPermissions(value);

      expect(permissions.permissions).toEqual([]);
      expect(permissions.roleScopes).toEqual([]);
      expect(canReadDirectory(permissions)).toBe(false);
      expect(permissions.canGrant('EMPLOYEE')).toBe(false);
    }
  });

  it('ac2_2_offers_only_the_roles_below_the_callers_own_level', () => {
    const secretary = readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] }));
    const dean = readPermissions(token({ role_scopes: ['DEAN:9'] }));
    const rector = readPermissions(token({ role_scopes: ['RECTOR:1'] }));
    const administrator = readPermissions(token({ role_scopes: ['SYSTEM_ADMIN:1'] }));
    const officer = readPermissions(token({ role_scopes: ['GDPR_OFFICER:1'] }));

    expect(grantableRoles(secretary)).toEqual(['EMPLOYEE']);
    expect(grantableRoles(dean)).toEqual(['EMPLOYEE', 'FACULTY_SECRETARY']);
    expect(grantableRoles(rector)).toEqual([
      'EMPLOYEE',
      'FACULTY_SECRETARY',
      'DEAN',
      'RECTOR_SECRETARY',
    ]);
    expect(grantableRoles(administrator)).toEqual([
      'EMPLOYEE',
      'FACULTY_SECRETARY',
      'DEAN',
      'RECTOR_SECRETARY',
      'RECTOR',
      'SYSTEM_ADMIN',
      'GDPR_OFFICER',
    ]);
    expect(grantableRoles(officer)).toEqual([]);
    expect(dean.canGrant('DEAN')).toBe(false);
    expect(dean.canGrant('SYSTEM_ADMIN')).toBe(false);
  });

  it('ac3_2_reads_the_delegations_claim_of_the_access_token', () => {
    const permissions = readPermissions(
      token({ role_scopes: ['DEAN:9'], delegations: ['DEAN:9:2', 'DEAN:', 'DEAN:9', 'DEAN:9:x'] }),
    );

    expect(permissions.delegations).toEqual([{ role: 'DEAN', organizationId: 9, delegatorId: 2 }]);
  });

  it('ac3_2_never_offers_a_role_the_caller_holds_only_by_delegation', () => {
    const secretary = readPermissions(
      token({ role_scopes: ['FACULTY_SECRETARY:9', 'DEAN:9'], delegations: ['DEAN:9:2'] }),
    );

    expect(delegatableRoles(secretary)).toEqual(['FACULTY_SECRETARY']);
    expect(delegatableOrganizations(secretary, 'DEAN')).toEqual([]);
    expect(delegatableOrganizations(secretary, 'FACULTY_SECRETARY')).toEqual([9]);
    expect(canDelegate(secretary)).toBe(true);
  });

  it('ac3_2_keeps_an_own_role_that_somebody_also_delegated_to_the_caller', () => {
    const dean = readPermissions(
      token({
        role_scopes: ['DEAN:9'],
        held_scopes: ['DEAN:9'],
        delegations: ['DEAN:9:2'],
        permissions: ['user:manage:scope'],
      }),
    );

    expect(delegatableRoles(dean)).toEqual(['DEAN']);
    expect(delegatableOrganizations(dean, 'DEAN')).toEqual([9]);
    expect(canDelegate(dean)).toBe(true);
  });

  it('ac3_2_reads_the_held_scopes_claim_in_preference_to_subtracting_the_borrowed_ones', () => {
    const borrowed = readPermissions(
      token({
        role_scopes: ['EMPLOYEE:64', 'DEAN:9'],
        held_scopes: ['EMPLOYEE:64'],
        delegations: ['DEAN:9:2'],
      }),
    );

    expect(borrowed.heldScopes).toEqual([{ role: 'EMPLOYEE', organizationId: 64 }]);
    expect(canDelegate(borrowed)).toBe(false);
  });

  it('ac3_6_opens_the_delegations_page_only_for_a_caller_holding_an_approval_role', () => {
    const dean = readPermissions(token({ role_scopes: ['DEAN:9', 'EMPLOYEE:64'] }));
    const employee = readPermissions(token({ role_scopes: ['EMPLOYEE:64'] }));
    const borrowed = readPermissions(
      token({ role_scopes: ['EMPLOYEE:64', 'DEAN:9'], delegations: ['DEAN:9:2'] }),
    );

    expect(canDelegate(dean)).toBe(true);
    expect(delegatableRoles(dean)).toEqual(['DEAN']);
    expect(canDelegate(employee)).toBe(false);
    expect(canDelegate(borrowed)).toBe(false);
  });

  it('ignores role scopes without an organisation', () => {
    const permissions = readPermissions(
      token({ role_scopes: ['DEAN:', ':9', 'DEAN:x', 'DEAN:9'] }),
    );

    expect(permissions.roleScopes).toEqual([{ role: 'DEAN', organizationId: 9 }]);
  });
});
