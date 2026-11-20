import {
  approvalScopes,
  canCreateAwards,
  canDelegate,
  canOpenCommandCentre,
  canOpenHallOfFame,
  commandCentreScope,
  canReadCompliance,
  canManagePeople,
  canReadDirectory,
  canReadOwnAnalytics,
  canReadUnitAnalytics,
  canReadUniversityAnalytics,
  canReadOwnAwards,
  canReview,
  delegatableOrganizations,
  delegatableRoles,
  grantableRoles,
  readPermissions,
  readSubject,
  readsUniversityHallOfFame,
  reviewableLevels,
  roleHome,
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

  it('ac1_9_lets_a_faculty_secretary_or_dean_manage_people_without_an_account', () => {
    expect(canManagePeople(readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] })))).toBe(
      true,
    );
    expect(canManagePeople(readPermissions(token({ role_scopes: ['DEAN:9'] })))).toBe(true);
    expect(canManagePeople(readPermissions(token({ role_scopes: ['RECTOR_SECRETARY:1'] })))).toBe(
      false,
    );
    expect(canManagePeople(readPermissions(token({ role_scopes: ['EMPLOYEE:64'] })))).toBe(false);
  });

  it('reads a non-ascii claim and a space separated permission string', () => {
    const permissions = readPermissions(
      token({ permissions: 'user:read:all user:manage', name: 'Мартин' }),
    );

    expect(permissions.permissions).toEqual(['user:read:all', 'user:manage']);
    expect(canReadDirectory(permissions)).toBe(true);
  });

  it('ac2_5_opens_the_compliance_page_only_with_compliance_read', () => {
    expect(canReadCompliance(readPermissions(token({ permissions: ['compliance:read'] })))).toBe(
      true,
    );
    expect(
      canReadCompliance(readPermissions(token({ permissions: ['user:read:all', 'user:manage'] }))),
    ).toBe(false);
  });

  it('ac2_1_opens_unit_analytics_only_with_analytics_read_department', () => {
    expect(
      canReadUnitAnalytics(readPermissions(token({ permissions: ['analytics:read:department'] }))),
    ).toBe(true);
    expect(
      canReadUnitAnalytics(readPermissions(token({ permissions: ['analytics:read:own'] }))),
    ).toBe(false);
  });

  it('ac2_1_opens_university_analytics_only_with_analytics_read_university', () => {
    expect(
      canReadUniversityAnalytics(
        readPermissions(token({ permissions: ['analytics:read:university'] })),
      ),
    ).toBe(true);
    expect(
      canReadUniversityAnalytics(
        readPermissions(token({ permissions: ['analytics:read:department'] })),
      ),
    ).toBe(false);
  });

  it('ac2_4_opens_the_dashboard_only_with_analytics_read_own', () => {
    expect(
      canReadOwnAnalytics(readPermissions(token({ permissions: ['analytics:read:own'] }))),
    ).toBe(true);
    expect(canReadOwnAnalytics(readPermissions(token({ permissions: ['award:read:own'] })))).toBe(
      false,
    );
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

  it('ac3_offers_only_the_roles_each_role_grants', () => {
    const secretary = readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] }));
    const dean = readPermissions(token({ role_scopes: ['DEAN:9'] }));
    const office = readPermissions(token({ role_scopes: ['RECTOR_SECRETARY:1'] }));
    const rector = readPermissions(token({ role_scopes: ['RECTOR:1'] }));
    const both = readPermissions(token({ role_scopes: ['RECTOR:1', 'DEAN:9'] }));
    const administrator = readPermissions(token({ role_scopes: ['SYSTEM_ADMIN:1'] }));
    const officer = readPermissions(token({ role_scopes: ['GDPR_OFFICER:1'] }));

    expect(grantableRoles(secretary)).toEqual(['EMPLOYEE']);
    expect(grantableRoles(dean)).toEqual(['EMPLOYEE', 'FACULTY_SECRETARY']);
    expect(grantableRoles(office)).toEqual(['EMPLOYEE']);
    expect(grantableRoles(rector)).toEqual(['DEAN', 'RECTOR_SECRETARY']);
    expect(grantableRoles(both)).toEqual([
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

  it('ac1_10_ac1_2_counts_own_and_delegated_approval_roles_for_the_queue', () => {
    const borrowed = readPermissions(
      token({
        role_scopes: ['EMPLOYEE:64', 'DEAN:9', 'FACULTY_SECRETARY:9'],
        delegations: ['DEAN:9:2'],
      }),
    );
    const employee = readPermissions(token({ role_scopes: ['EMPLOYEE:64'] }));

    expect(canReview(borrowed)).toBe(true);
    expect(approvalScopes(borrowed).map((scope) => scope.role)).toEqual([
      'DEAN',
      'FACULTY_SECRETARY',
    ]);
    expect(reviewableLevels(borrowed)).toEqual(['FACULTY_SECRETARY', 'DEAN']);
    expect(canReview(employee)).toBe(false);
    expect(reviewableLevels(employee)).toEqual([]);
  });

  it('ac1_10_reads_the_user_id_of_the_token', () => {
    expect(readSubject(token({ sub: '21' }))).toBe('21');
    expect(readSubject(token({}))).toBeNull();
    expect(readSubject(null)).toBeNull();
  });

  it('ac1_2_tells_who_may_enter_and_read_awards', () => {
    const employee = readPermissions(token({ permissions: ['award:read:own', 'award:create'] }));
    const admin = readPermissions(token({ permissions: ['award:read:own', 'award:read:all'] }));

    expect(canCreateAwards(employee)).toBe(true);
    expect(canCreateAwards(admin)).toBe(false);
    expect(canReadOwnAwards(admin)).toBe(true);
  });

  it('ignores role scopes without an organisation', () => {
    const permissions = readPermissions(
      token({ role_scopes: ['DEAN:', ':9', 'DEAN:x', 'DEAN:9'] }),
    );

    expect(permissions.roleScopes).toEqual([{ role: 'DEAN', organizationId: 9 }]);
  });

  it('ac3_1_ac3_2_opens_the_hall_of_fame_for_held_roles_only_and_tells_whether_it_shows_the_university', () => {
    const rector = readPermissions(
      token({ role_scopes: ['RECTOR:1'], permissions: ['analytics:read:university'] }),
    );
    const dean = readPermissions(
      token({ role_scopes: ['DEAN:9'], permissions: ['analytics:read:department'] }),
    );
    const delegate = readPermissions(
      token({ role_scopes: ['EMPLOYEE:5', 'DEAN:9'], delegations: ['DEAN:9:4'] }),
    );
    const secretary = readPermissions(token({ role_scopes: ['RECTOR_SECRETARY:1'] }));

    expect([rector, dean, delegate, secretary].map(canOpenHallOfFame)).toEqual([
      true,
      true,
      false,
      false,
    ]);
    expect(readsUniversityHallOfFame(rector)).toBe(true);
    expect(readsUniversityHallOfFame(dean)).toBe(false);
    expect(readsUniversityHallOfFame(readPermissions(token({ role_scopes: ['RECTOR:1'] })))).toBe(
      false,
    );
  });
  it('ac5_1_ac5_6_opens_the_command_centre_for_held_secretary_and_dean_roles_with_their_queue_level', () => {
    const secretary = readPermissions(token({ role_scopes: ['FACULTY_SECRETARY:9'] }));
    const dean = readPermissions(token({ role_scopes: ['DEAN:9'] }));
    const rectorSecretary = readPermissions(token({ role_scopes: ['RECTOR_SECRETARY:1'] }));
    const delegate = readPermissions(
      token({
        role_scopes: ['EMPLOYEE:5', 'FACULTY_SECRETARY:9'],
        delegations: ['FACULTY_SECRETARY:9:4'],
      }),
    );
    const employee = readPermissions(token({ role_scopes: ['EMPLOYEE:5'] }));

    expect(
      [secretary, dean, rectorSecretary, delegate, employee].map(canOpenCommandCentre),
    ).toEqual([true, true, true, false, false]);
    expect(commandCentreScope(secretary)).toEqual({ level: 'FACULTY_SECRETARY', facultyId: 9 });
    expect(commandCentreScope(dean)).toEqual({ level: 'DEAN', facultyId: 9 });
    expect(commandCentreScope(rectorSecretary)).toEqual({
      level: 'RECTOR_SECRETARY',
      facultyId: null,
    });
    expect(
      commandCentreScope(
        readPermissions(token({ role_scopes: ['RECTOR_SECRETARY:1', 'FACULTY_SECRETARY:9'] })),
      ),
    ).toEqual({ level: 'FACULTY_SECRETARY', facultyId: 9 });
    expect(commandCentreScope(delegate)).toBeNull();
  });

  it('ac1_role_home_opens_the_first_section_the_roles_carry', () => {
    const home = (claims: Record<string, unknown>) => roleHome(readPermissions(token(claims)));

    expect(home({ role_scopes: ['RECTOR:1'] })).toBe('/hall-of-fame');
    expect(
      home({ role_scopes: ['FACULTY_SECRETARY:9'], permissions: ['analytics:read:own'] }),
    ).toBe('/command-centre');
    expect(home({ role_scopes: ['EMPLOYEE:5'], permissions: ['analytics:read:own'] })).toBe(
      '/dashboard',
    );
    expect(
      home({ role_scopes: ['GDPR_OFFICER:1'], permissions: ['compliance:read', 'user:read:all'] }),
    ).toBe('/compliance');
    expect(home({ role_scopes: ['SYSTEM_ADMIN:1'], permissions: ['user:read:all'] })).toBe(
      '/admin/users',
    );
    expect(home({ permissions: ['award:read:own'] })).toBe('/');
  });
});
