import { canReadDirectory, grantableRoles, readPermissions } from './permissions';

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
    for (const value of [null, undefined, '', 'not-a-token', 'a.b.c', `header.${btoa('[1,2]')}.s`]) {
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

  it('ignores role scopes without an organisation', () => {
    const permissions = readPermissions(token({ role_scopes: ['DEAN:', ':9', 'DEAN:x', 'DEAN:9'] }));

    expect(permissions.roleScopes).toEqual([{ role: 'DEAN', organizationId: 9 }]);
  });
});
